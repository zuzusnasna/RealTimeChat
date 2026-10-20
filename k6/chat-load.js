import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL_1 = __ENV.BASE_URL_1 || 'http://localhost:8080';
const BASE_URL_2 = __ENV.BASE_URL_2 || 'http://localhost:8081';
const PASSWORD = 'LoadTest!123';

const sentMessages = new Counter('chat_messages_sent');
const receivedMessages = new Counter('chat_messages_received');
const websocketConnected = new Rate('websocket_connected');
const messageLatency = new Trend('chat_message_delivery_ms', true);

export const options = {
  scenarios: {
    chat_load: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '20s', target: 5 },
        { duration: '40s', target: 5 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    websocket_connected: ['rate>0.95'],
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
  },
};

function failIfNot(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function login(baseUrl, username) {
  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ username, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  );

  check(response, {
    'login returns 200': (r) => r.status === 200,
  });
  failIfNot(response.status === 200, `Login failed at ${baseUrl}: HTTP ${response.status}`);

  const cookies = http.cookieJar().cookiesForURL(`${baseUrl}/`);
  const sessionId = cookies.JSESSIONID?.[0];
  failIfNot(sessionId, `JSESSIONID cookie missing at ${baseUrl}`);
  return sessionId;
}

export function setup() {
  const suffix = `${Date.now().toString().slice(-8)}${Math.floor(Math.random() * 90 + 10)}`;
  const username = `load${suffix}`;

  const signup = http.post(
    `${BASE_URL_1}/api/auth/signup`,
    JSON.stringify({ username, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'signup' } },
  );
  check(signup, { 'test user created': (r) => r.status === 201 });
  failIfNot(signup.status === 201, `Test user signup failed: HTTP ${signup.status}`);

  login(BASE_URL_1, username);
  const createRoom = http.post(
    `${BASE_URL_1}/api/rooms`,
    JSON.stringify({ name: `k6-load-${suffix}` }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'create_room' } },
  );
  check(createRoom, { 'test room created': (r) => r.status === 201 });
  failIfNot(createRoom.status === 201, `Test room creation failed: HTTP ${createRoom.status}`);
  const roomId = createRoom.json('id');
  failIfNot(roomId, 'Room ID missing from create-room response');

  // HTTP sessions are instance-local, so log in separately to app-2.
  login(BASE_URL_2, username);
  const joinRoom = http.post(
    `${BASE_URL_2}/api/rooms/${roomId}/join`,
    null,
    { tags: { name: 'join_room_app2' } },
  );
  check(joinRoom, { 'test room joined on app-2': (r) => r.status === 200 });
  failIfNot(joinRoom.status === 200, `Joining room on app-2 failed: HTTP ${joinRoom.status}`);

  return { username, roomId };
}

export default function (data) {
  const baseUrl = (__VU % 2 === 1) ? BASE_URL_1 : BASE_URL_2;
  const sessionId = login(baseUrl, data.username);
  const wsUrl = baseUrl.replace(/^http/, 'ws') + '/ws';
  const destination = `/app/chat/${data.roomId}`;
  const subscription = `/topic/rooms/${data.roomId}`;
  let connected = false;
  let subscribed = false;
  const sentAt = {};

  const response = ws.connect(
    wsUrl,
    { headers: { Cookie: `JSESSIONID=${sessionId}` }, tags: { name: 'chat_websocket' } },
    (socket) => {
      socket.on('open', () => {
        socket.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\u0000');
      });

      socket.on('message', (frame) => {
        if (frame.startsWith('CONNECTED')) {
          connected = true;
          websocketConnected.add(true);
          socket.send(
            `SUBSCRIBE\nid:sub-${__VU}\ndestination:${subscription}\nack:auto\n\n\u0000`,
          );
          socket.setTimeout(() => {
            subscribed = true;
            const messageId = `${__VU}-${Date.now()}`;
            sentAt[messageId] = Date.now();
            socket.send(
              `SEND\ndestination:${destination}\ncontent-type:application/json\n\n${JSON.stringify({ content: `k6 message ${messageId}` })}\u0000`,
            );
            sentMessages.add(1);
          }, 1000);
          socket.setInterval(() => {
            if (!subscribed) return;
            const messageId = `${__VU}-${Date.now()}`;
            sentAt[messageId] = Date.now();
            socket.send(
              `SEND\ndestination:${destination}\ncontent-type:application/json\n\n${JSON.stringify({ content: `k6 message ${messageId}` })}\u0000`,
            );
            sentMessages.add(1);
          }, 2000);
        } else if (frame.startsWith('MESSAGE')) {
          receivedMessages.add(1);
          const bodyStart = frame.indexOf('\n\n');
          if (bodyStart >= 0) {
            const body = frame.slice(bodyStart + 2).replace(/\u0000$/, '');
            try {
              const message = JSON.parse(body);
              const match = /^k6 message (.+)$/.exec(message.content || '');
              if (match && sentAt[match[1]]) {
                messageLatency.add(Date.now() - sentAt[match[1]]);
                delete sentAt[match[1]];
              }
            } catch (_) {
              // Ignore non-JSON STOMP frames; delivery is still counted.
            }
          }
        } else if (frame.startsWith('ERROR')) {
          socket.close();
        }
      });

      socket.on('error', () => {
        websocketConnected.add(false);
      });

      socket.setTimeout(() => socket.close(), 15000);
    },
  );

  const ok = check(response, {
    'websocket handshake succeeds': (r) => r && r.status === 101,
  });
  if (!connected) websocketConnected.add(false);
  sleep(0.2);
}

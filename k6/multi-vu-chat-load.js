import http from 'k6/http';
import ws from 'k6/ws';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Multi-connection load test. Each VU gets its own sequence identity, even when
// VUs share one of the two available test accounts. This is NOT a ping-pong test.
const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const ROOM_ID = __ENV.ROOM_ID;
const USER_A = __ENV.USER_A;
const PASS_A = __ENV.PASS_A;
const USER_B = __ENV.USER_B;
const PASS_B = __ENV.PASS_B;
const TEST_DURATION = __ENV.TEST_DURATION || '30s';
const TEST_DURATION_MS = Number(__ENV.TEST_DURATION_MS || 30000);
const SEND_INTERVAL_MS = Math.max(100, Number(__ENV.SEND_INTERVAL_MS || 250));

const messagesSent = new Counter('multi_vu_messages_sent');
const peerMessagesReceived = new Counter('multi_vu_peer_messages_received');
const peerDeliveryLatency = new Trend('multi_vu_peer_delivery_ms', true);
const outOfOrderMessages = new Counter('multi_vu_out_of_order_messages');
const missingSequenceGaps = new Counter('multi_vu_missing_sequence_gaps');
const websocketHandshakeSuccess = new Rate('multi_vu_websocket_handshake_success');

export const options = {
  scenarios: {
    multi_vu_chat_load: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 2),
      duration: TEST_DURATION,
      gracefulStop: '5s',
    },
  },
  thresholds: {
    multi_vu_websocket_handshake_success: ['rate>0.99'],
    multi_vu_peer_messages_received: ['count>5'],
    multi_vu_peer_delivery_ms: ['p(95)<1000'],
    checks: ['rate>0.95'],
  },
};

function requireValue(value, name) {
  if (!value) fail(`Missing required environment variable: ${name}`);
}

function loginAndJoin(username, password, roomId) {
  const loginResponse = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username, password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'multi_vu_login' },
    },
  );

  const loginOk = check(loginResponse, {
    [`login succeeds for ${username}`]: (response) => response.status === 200,
  });
  if (!loginOk) fail(`Login failed for ${username}: HTTP ${loginResponse.status}`);

  const cookies = http.cookieJar().cookiesForURL(`${BASE_URL}/`);
  const sessionId = cookies.JSESSIONID?.[0];
  if (!sessionId) fail(`JSESSIONID cookie missing for ${username}`);

  const joinResponse = http.post(`${BASE_URL}/api/rooms/${roomId}/join`, null, {
    tags: { name: 'multi_vu_join_room' },
  });
  const joinOk = check(joinResponse, {
    [`${username} can join room ${roomId}`]: (response) => response.status === 200,
  });
  if (!joinOk) fail(`Room join failed for ${username}: HTTP ${joinResponse.status}`);

  return sessionId;
}

export function setup() {
  requireValue(BASE_URL, 'BASE_URL');
  requireValue(ROOM_ID, 'ROOM_ID');
  requireValue(USER_A, 'USER_A');
  requireValue(PASS_A, 'PASS_A');
  requireValue(USER_B, 'USER_B');
  requireValue(PASS_B, 'PASS_B');
  return { runId: `${Date.now()}-${Math.floor(Math.random() * 100000)}`, roomId: ROOM_ID };
}

export default function (data) {
  // Reuse the two test accounts safely for authentication, but keep message
  // sequence identity unique per VU so their streams cannot be conflated.
  const isAccountA = __VU % 2 === 1;
  const username = isAccountA ? USER_A : USER_B;
  const password = isAccountA ? PASS_A : PASS_B;
  const role = isAccountA ? 'A' : 'B';
  const sessionId = loginAndJoin(username, password, data.roomId);
  const wsUrl = BASE_URL.replace(/^http/, 'ws') + '/ws';
  const destination = `/app/chat/${data.roomId}`;
  const subscription = `/topic/rooms/${data.roomId}`;

  let sequence = 0;
  const lastSequenceByVu = {};

  function sendMessage(socket) {
    sequence += 1;
    const sentAt = Date.now();
    const content = `k6load|${data.runId}|${__VU}|${sequence}|${sentAt}`;
    socket.send(
      `SEND\ndestination:${destination}\ncontent-type:application/json\n\n${JSON.stringify({ content })}\u0000`,
    );
    messagesSent.add(1);
  }

  const response = ws.connect(
    wsUrl,
    {
      headers: { Cookie: `JSESSIONID=${sessionId}` },
      tags: { name: 'multi_vu_chat_websocket' },
    },
    (socket) => {
      socket.on('open', () => {
        socket.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\u0000');
      });

      socket.on('message', (frame) => {
        if (frame.startsWith('CONNECTED')) {
          websocketHandshakeSuccess.add(true);
          socket.send(
            `SUBSCRIBE\nid:sub-vu-${__VU}\ndestination:${subscription}\nack:auto\n\n\u0000`,
          );
          // Stagger starts slightly so all VUs do not publish at the exact same instant.
          socket.setTimeout(() => {
            sendMessage(socket);
            socket.setInterval(() => sendMessage(socket), SEND_INTERVAL_MS);
          }, 1000 + (__VU * 50));
          return;
        }

        if (!frame.startsWith('MESSAGE')) {
          if (frame.startsWith('ERROR')) socket.close();
          return;
        }

        const bodyStart = frame.indexOf('\n\n');
        if (bodyStart < 0) return;
        const body = frame.slice(bodyStart + 2).replace(/\u0000$/, '');

        try {
          const message = JSON.parse(body);
          const match = /^k6load\|([^|]+)\|(\d+)\|(\d+)\|(\d+)$/.exec(message.content || '');
          if (!match || match[1] !== data.runId) return;

          const senderVu = Number(match[2]);
          const senderSequence = Number(match[3]);
          const sentAt = Number(match[4]);

          if (senderVu !== __VU) {
            peerMessagesReceived.add(1);
            peerDeliveryLatency.add(Math.max(0, Date.now() - sentAt));
          }

          const previous = lastSequenceByVu[senderVu] || 0;
          if (senderSequence <= previous) {
            // Only a duplicate/older sequence arriving after a newer one is
            // counted as out-of-order. A gap is reported separately.
            outOfOrderMessages.add(1);
          } else {
            if (previous > 0 && senderSequence > previous + 1) {
              missingSequenceGaps.add(senderSequence - previous - 1);
            }
            lastSequenceByVu[senderVu] = senderSequence;
          }
        } catch (_) {
          // Ignore unrelated/non-JSON STOMP frames.
        }
      });

      socket.on('error', () => websocketHandshakeSuccess.add(false));
      socket.setTimeout(() => socket.close(), TEST_DURATION_MS);
    },
  );

  const handshakeOk = check(response, {
    'WebSocket upgrade returns HTTP 101': (result) => result && result.status === 101,
  });
  if (!handshakeOk) {
    websocketHandshakeSuccess.add(false);
    console.error(`WebSocket connection failed for VU ${__VU} (account ${role}): HTTP ${response?.status}`);
  }
}

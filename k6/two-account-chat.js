import http from 'k6/http';
import ws from 'k6/ws';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Two existing accounts join the same room and continuously reply to each other.
// Credentials are supplied via environment variables; never commit real passwords.
const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const ROOM_ID = __ENV.ROOM_ID;
const USER_A = __ENV.USER_A;
const PASS_A = __ENV.PASS_A;
const USER_B = __ENV.USER_B;
const PASS_B = __ENV.PASS_B;
const TEST_DURATION = __ENV.TEST_DURATION || '30s';
const TEST_DURATION_MS = Number(__ENV.TEST_DURATION_MS || 30000);

const messagesSent = new Counter('two_account_messages_sent');
const peerMessagesReceived = new Counter('two_account_peer_messages_received');
const peerDeliveryLatency = new Trend('two_account_peer_delivery_ms', true);
const outOfOrderMessages = new Counter('two_account_out_of_order_messages');
const websocketHandshakeSuccess = new Rate('two_account_websocket_handshake_success');

export const options = {
  scenarios: {
    two_account_ping_pong: {
      executor: 'constant-vus',
      vus: 2,
      duration: TEST_DURATION,
      gracefulStop: '5s',
    },
  },
  thresholds: {
    two_account_websocket_handshake_success: ['rate>0.99'],
    two_account_peer_messages_received: ['count>5'],
    two_account_peer_delivery_ms: ['p(95)<1000'],
    checks: ['rate>0.95'],
  },
};

function requireValue(value, name) {
  if (!value) {
    fail(`Missing required environment variable: ${name}`);
  }
}

function loginAndJoin(username, password, roomId) {
  const loginResponse = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username, password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'two_account_login' },
    },
  );

  const loginOk = check(loginResponse, {
    [`login succeeds for ${username}`]: (response) => response.status === 200,
  });
  if (!loginOk) {
    fail(`Login failed for ${username}: HTTP ${loginResponse.status}`);
  }

  const cookies = http.cookieJar().cookiesForURL(`${BASE_URL}/`);
  const sessionId = cookies.JSESSIONID?.[0];
  if (!sessionId) {
    fail(`JSESSIONID cookie missing for ${username}`);
  }

  const joinResponse = http.post(
    `${BASE_URL}/api/rooms/${roomId}/join`,
    null,
    { tags: { name: 'two_account_join_room' } },
  );
  const joinOk = check(joinResponse, {
    [`${username} can join room ${roomId}`]: (response) => response.status === 200,
  });
  if (!joinOk) {
    fail(`Room join failed for ${username}: HTTP ${joinResponse.status}`);
  }

  return sessionId;
}

export function setup() {
  requireValue(BASE_URL, 'BASE_URL');
  requireValue(ROOM_ID, 'ROOM_ID');
  requireValue(USER_A, 'USER_A');
  requireValue(PASS_A, 'PASS_A');
  requireValue(USER_B, 'USER_B');
  requireValue(PASS_B, 'PASS_B');

  return {
    runId: `${Date.now()}-${Math.floor(Math.random() * 100000)}`,
    roomId: ROOM_ID,
  };
}

export default function (data) {
  const isAccountA = __VU === 1;
  const username = isAccountA ? USER_A : USER_B;
  const password = isAccountA ? PASS_A : PASS_B;
  const role = isAccountA ? 'A' : 'B';
  const peerRole = isAccountA ? 'B' : 'A';
  const sessionId = loginAndJoin(username, password, data.roomId);
  const wsUrl = BASE_URL.replace(/^http/, 'ws') + '/ws';
  const destination = `/app/chat/${data.roomId}`;
  const subscription = `/topic/rooms/${data.roomId}`;

  let connected = false;
  let sequence = 0;
  let lastPeerSequence = 0;

  function sendMessage(socket) {
    sequence += 1;
    const sentAt = Date.now();
    const content = `k6two|${data.runId}|${role}|${sequence}|${sentAt}`;
    socket.send(
      `SEND\ndestination:${destination}\ncontent-type:application/json\n\n${JSON.stringify({ content })}\u0000`,
    );
    messagesSent.add(1);
  }

  const response = ws.connect(
    wsUrl,
    {
      headers: { Cookie: `JSESSIONID=${sessionId}` },
      tags: { name: 'two_account_chat_websocket' },
    },
    (socket) => {
      socket.on('open', () => {
        socket.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\u0000');
      });

      socket.on('message', (frame) => {
        if (frame.startsWith('CONNECTED')) {
          connected = true;
          websocketHandshakeSuccess.add(true);
          socket.send(
            `SUBSCRIBE\nid:sub-${role}\ndestination:${subscription}\nack:auto\n\n\u0000`,
          );

          // Give both accounts time to subscribe before starting the ping-pong.
          if (isAccountA) {
            socket.setTimeout(() => sendMessage(socket), 1500);
          }
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
          const match = /^k6two\|([^|]+)\|([AB])\|(\d+)\|(\d+)$/.exec(message.content || '');
          if (!match || match[1] !== data.runId || match[2] !== peerRole) {
            return; // Ignore own echo and messages from other test runs/users.
          }

          const peerSequence = Number(match[3]);
          const sentAt = Number(match[4]);
          peerMessagesReceived.add(1);
          peerDeliveryLatency.add(Math.max(0, Date.now() - sentAt));

          if (peerSequence !== lastPeerSequence + 1) {
            outOfOrderMessages.add(1);
          }
          lastPeerSequence = peerSequence;

          // Immediately reply to the other account, creating a rapid two-way exchange.
          sendMessage(socket);
        } catch (_) {
          // Ignore unrelated/non-JSON STOMP frames.
        }
      });

      socket.on('error', () => {
        websocketHandshakeSuccess.add(false);
      });

      socket.setTimeout(() => socket.close(), TEST_DURATION_MS);
    },
  );

  const handshakeOk = check(response, {
    'WebSocket upgrade returns HTTP 101': (result) => result && result.status === 101,
  });
  if (!connected && (!response || response.status !== 101)) {
    websocketHandshakeSuccess.add(false);
  }
  if (!handshakeOk) {
    console.error(`WebSocket connection failed for account ${role}: HTTP ${response?.status}`);
  }
}

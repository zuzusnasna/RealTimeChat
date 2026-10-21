import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { Client } from '@stomp/stompjs';
import './styles.css';

async function request(url, options = {}) {
  const response = await fetch(url, {
    credentials: 'same-origin',
    headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers },
    ...options
  });
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.detail || body?.message || '요청에 실패했습니다.');
  return body;
}

function LoginPage() {
  const navigate = useNavigate();
  const [mode, setMode] = useState('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    request('/api/auth/me').then(() => navigate('/chat', { replace: true })).catch(() => {});
  }, [navigate]);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setNotice('');
    try {
      await request('/api/auth/' + mode, {
        method: 'POST',
        body: JSON.stringify({ username: username.trim(), password })
      });
      if (mode === 'signup') {
        setMode('login');
        setPassword('');
        setError(false);
        setNotice('가입했습니다. 로그인해 주세요.');
      } else {
        navigate('/chat', { replace: true });
      }
    } catch (e) {
      setError(true);
      setNotice(e.message);
    } finally {
      setBusy(false);
    }
  }

  return <main className="auth-layout">
    <aside className="auth-aside">
      <a className="wordmark" href="#/login"><span className="brand-mark">R</span> RealTimeChat</a>
      <div className="auth-intro">
        <p className="overline">실시간 채팅</p>
        <h1>채팅방에서<br />대화하세요.</h1>
        <p>로그인 후 채팅방을 선택하세요.</p>
      </div>
      <div className="auth-aside-foot">RealTimeChat</div>
    </aside>
    <section className="auth-main">
      <div className="auth-form-wrap">
        <div className="auth-tabs" role="tablist" aria-label="계정 메뉴">
          <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => { setMode('login'); setNotice(''); }}>로그인</button>
          <button type="button" className={mode === 'signup' ? 'active' : ''} onClick={() => { setMode('signup'); setNotice(''); }}>회원가입</button>
        </div>
        <h2>{mode === 'login' ? '로그인' : '회원가입'}</h2>
        <p className="form-help">{mode === 'login' ? '아이디와 비밀번호를 입력하세요.' : '사용할 아이디와 비밀번호를 입력하세요.'}</p>
        <form onSubmit={submit}>
          <label htmlFor="username">아이디</label>
          <input id="username" value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" minLength={3} maxLength={30} required />
          <label htmlFor="password">비밀번호</label>
          <input id="password" type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={8} maxLength={72} required />
          <button className="primary-button auth-submit" disabled={busy}>{busy ? '처리 중...' : mode === 'login' ? '로그인' : '회원가입'}</button>
        </form>
        {notice && <p className={error ? 'notice error' : 'notice'} role="status">{notice}</p>}
      </div>
    </section>
  </main>;
}

function ChatPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [rooms, setRooms] = useState([]);
  const [roomId, setRoomId] = useState('');
  const [roomName, setRoomName] = useState('');
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [connection, setConnection] = useState('연결 중');
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState('');
  const clientRef = useRef(null);
  const subscriptionRef = useRef(null);
  const readSubscriptionRef = useRef(null);
  const roomIdRef = useRef('');
  const usernameRef = useRef('');
  const messageAreaRef = useRef(null);

  useEffect(() => {
    let alive = true;
    request('/api/auth/me').then(user => {
      if (!alive) return;
      setUsername(user.username);
      usernameRef.current = user.username;
    }).catch(() => navigate('/login', { replace: true }));
    return () => { alive = false; };
  }, [navigate]);

  const loadRooms = useCallback(async (preferredId) => {
    const data = await request('/api/rooms');
    setRooms(data);
    const saved = sessionStorage.getItem('lastActiveRoomId');
    const target = [preferredId, saved, roomIdRef.current, data[0]?.id].find(id =>
      id != null && data.some(room => String(room.id) === String(id))
    );
    if (target != null) setRoomId(String(target));
    else {
      setRoomId('');
      setMessages([{ system: '채팅방이 없습니다. 새 채팅방을 만들어 주세요.' }]);
    }
  }, []);

  useEffect(() => {
    const client = new Client({
      brokerURL: (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws',
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        setConnection('연결됨');
        loadRooms().catch(e => setError(e.message));
      },
      onWebSocketClose: () => { setConnected(false); setConnection('연결 끊김'); },
      onWebSocketError: () => { setConnected(false); setConnection('연결 실패'); },
      onStompError: () => { setError('메시지를 처리하지 못했습니다.'); }
    });
    clientRef.current = client;
    client.activate();
    return () => {
      subscriptionRef.current?.unsubscribe();
      readSubscriptionRef.current?.unsubscribe();
      client.deactivate();
    };
  }, [loadRooms]);

  const activeRoom = rooms.find(room => String(room.id) === String(roomId));

  useEffect(() => {
    const messageArea = messageAreaRef.current;
    if (messageArea) messageArea.scrollTop = messageArea.scrollHeight;
  }, [messages]);

  useEffect(() => {
    let alive = true;
    roomIdRef.current = roomId;
    subscriptionRef.current?.unsubscribe();
    readSubscriptionRef.current?.unsubscribe();
    subscriptionRef.current = null;
    readSubscriptionRef.current = null;
    if (!roomId || !activeRoom) {
      if (!roomId) setMessages([{ system: '채팅방을 선택하거나 새 채팅방을 만들어 주세요.' }]);
      return;
    }
    sessionStorage.setItem('lastActiveRoomId', roomId);
    if (!activeRoom.joined) {
      setMessages([{ system: '메시지를 보려면 채팅방에 참여하세요.' }]);
      return;
    }
    setMessages([]);
    const client = clientRef.current;
    if (client?.connected) {
      subscriptionRef.current = client.subscribe('/topic/rooms/' + roomId, frame => {
        const message = JSON.parse(frame.body);
        setMessages(current => [...current.filter(item => !item.system), message]);
        if (message.sender !== usernameRef.current) {
          request('/api/rooms/' + roomId + '/read', { method: 'POST' })
            .catch(e => setError(e.message));
        }
      });
      readSubscriptionRef.current = client.subscribe('/topic/rooms/' + roomId + '/read', frame => {
        const receipt = JSON.parse(frame.body);
        setMessages(current => current.map(message => {
          if (message.system || message.sender === receipt.reader ||
              Number(message.id) > Number(receipt.lastReadMessageId)) return message;
          return { ...message, unreadCount: Math.max(0, Number(message.unreadCount || 0) - 1) };
        }));
      });
    }
    request('/api/rooms/' + roomId + '/messages').then(async history => {
      if (!alive) return;
      setMessages(history.length ? history : [{ system: '첫 메시지를 보내 보세요.' }]);
      try {
        await request('/api/rooms/' + roomId + '/read', { method: 'POST' });
      } catch (e) {
        if (alive) setError(e.message);
      }
    }).catch(e => { if (alive) setError(e.message); });
    return () => {
      alive = false;
      subscriptionRef.current?.unsubscribe();
      subscriptionRef.current = null;
    };
  }, [roomId, activeRoom?.joined, connected]);

  async function createRoom(event) {
    event.preventDefault();
    const name = roomName.trim();
    if (!name) return;
    try {
      const room = await request('/api/rooms', { method: 'POST', body: JSON.stringify({ name }) });
      setRoomName('');
      await loadRooms(room.id);
      setError('');
    } catch (e) { setError(e.message); }
  }

  async function joinRoom() {
    if (!roomId) return;
    try {
      await request('/api/rooms/' + roomId + '/join', { method: 'POST' });
      await loadRooms(roomId);
      setError('');
    } catch (e) { setError(e.message); }
  }

  async function deleteRoom(room) {
    const confirmed = window.confirm(`'${room.name}' 채팅방을 삭제할까요? 저장된 메시지와 참여 정보도 모두 삭제됩니다.`);
    if (!confirmed) return;
    try {
      await request('/api/rooms/' + room.id, { method: 'DELETE' });
      if (sessionStorage.getItem('lastActiveRoomId') === String(room.id)) {
        sessionStorage.removeItem('lastActiveRoomId');
      }
      await loadRooms();
      setError('');
    } catch (e) { setError(e.message); }
  }

  function sendMessage(event) {
    event.preventDefault();
    const content = draft.trim();
    const client = clientRef.current;
    if (!content || !roomId || !client?.connected || !activeRoom?.joined) return;
    client.publish({ destination: '/app/chat/' + roomId, body: JSON.stringify({ content }) });
    setDraft('');
  }

  async function logout() {
    try { await request('/api/auth/logout', { method: 'POST' }); } catch {}
    navigate('/login', { replace: true });
  }

  return <main className="chat-app">
    <aside className="sidebar">
      <a className="wordmark" href="#/chat"><span className="brand-mark">R</span> RealTimeChat</a>
      <div className="section-title">채팅방</div>
      <form className="create-room" onSubmit={createRoom}>
        <input aria-label="새 채팅방 이름" placeholder="채팅방 이름" maxLength={100} value={roomName} onChange={e => setRoomName(e.target.value)} />
        <button aria-label="채팅방 만들기" title="채팅방 만들기" className="add-button">+</button>
      </form>
      <nav className="room-list" aria-label="채팅방 목록">
        {rooms.map(room => <div key={room.id} className="room-item">
          <button type="button" className={String(room.id) === String(roomId) ? 'room-link selected' : 'room-link'} onClick={() => setRoomId(String(room.id))}>
            <span className={room.joined ? 'room-indicator joined' : 'room-indicator'} />
            <span className="room-name">{room.name}</span>
          </button>
          {room.owner && <button type="button" className="room-delete" aria-label={room.name + ' 채팅방 삭제'} title="채팅방 삭제" onClick={() => deleteRoom(room)}>×</button>}
        </div>)}
        {!rooms.length && <p className="empty-rooms">채팅방이 없습니다.</p>}
      </nav>
      <div className="sidebar-bottom">
        <div className="connection-line"><span className={connected ? 'status-dot online' : 'status-dot'} />{connection}</div>
        <div className="user-row"><span className="avatar">{username.slice(0, 1).toUpperCase() || 'U'}</span><span className="user-name">{username}</span><button type="button" className="logout-button" onClick={logout}>로그아웃</button></div>
      </div>
    </aside>
    <section className="chat-main">
      <header className="chat-header">
        <div><p className="overline">채팅</p><h1>{activeRoom?.name || '채팅방 선택'}</h1></div>
        {activeRoom && !activeRoom.joined && <button className="outline-button" type="button" onClick={joinRoom}>참여하기</button>}
      </header>
      {error && <div className="error-bar" role="alert">{error}<button type="button" onClick={() => setError('')} aria-label="오류 닫기">×</button></div>}
      <section className="message-area" aria-label="메시지 목록" ref={messageAreaRef}>
        {messages.map((message, index) => message.system
          ? <p className="system-message" key={'system-' + index}>{message.system}</p>
          : <div key={message.id || message.createdAt + '-' + index} className={message.sender === username ? 'message-row own' : 'message-row'}>
              <article className={message.sender === username ? 'message own' : 'message'}>
                <div className="message-author">{message.sender === username ? '나' : message.sender}</div>
                <div className="message-content">{message.content}</div>
              </article>
              <div className="message-meta">
                {message.sender === username && Number(message.unreadCount) > 0 && <span className="unread-count" aria-label={message.unreadCount + '명 안 읽음'}>{message.unreadCount}</span>}
                {message.createdAt && <time className="message-time">{new Date(message.createdAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}</time>}
              </div>
            </div>)}
      </section>
      <form className="composer" onSubmit={sendMessage}>
        <input aria-label="메시지 입력" placeholder={activeRoom?.joined ? '메시지 입력...' : '채팅방에 참여한 뒤 메시지를 보낼 수 있습니다.'} maxLength={1000} value={draft} onChange={e => setDraft(e.target.value)} disabled={!connected || !activeRoom?.joined} />
        <button className="primary-button send-button" disabled={!connected || !activeRoom?.joined || !draft.trim()}>전송</button>
      </form>
    </section>
  </main>;
}

function App() {
  return <HashRouter>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/chat" element={<ChatPage />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  </HashRouter>;
}

createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
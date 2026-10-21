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

function renderMessageContent(content) {
  const text = String(content ?? '');
  const urlPattern = /https?:\/\/[^\s<>"']+|www\.[^\s<>"']+/gi;
  const nodes = [];
  let cursor = 0;
  let match;

  while ((match = urlPattern.exec(text)) !== null) {
    let url = match[0];
    const trailing = url.match(/[.,!?;:)\]}]+$/)?.[0] ?? '';
    if (trailing) url = url.slice(0, -trailing.length);
    if (!url) continue;

    if (match.index > cursor) nodes.push(text.slice(cursor, match.index));
    const href = /^www\./i.test(url) ? 'https://' + url : url;
    nodes.push(
      <a
        key={match.index}
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        className="message-link"
      >
        {url}
      </a>
    );
    if (trailing) nodes.push(trailing);
    cursor = match.index + match[0].length;
  }

  if (cursor < text.length) nodes.push(text.slice(cursor));
  return nodes.length ? nodes : text;
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
  const [displayName, setDisplayName] = useState('');
  const [profileOpen, setProfileOpen] = useState(false);
  const [profileDraft, setProfileDraft] = useState('');
  const [profileNotice, setProfileNotice] = useState('');
  const [profileError, setProfileError] = useState(false);
  const [profileBusy, setProfileBusy] = useState(false);
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
      setDisplayName(user.displayName || user.username);
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
    let readRefreshTimer = null;
    let readRefreshVersion = 0;
    if (client?.connected) {
      subscriptionRef.current = client.subscribe('/topic/rooms/' + roomId, frame => {
        const message = JSON.parse(frame.body);
        // STOMP handlers can run concurrently on different app instances.
        // Deduplicate by database ID and sort every live update so late events
        // cannot leave the visible conversation in arrival order.
        setMessages(current => {
          const byId = new Map();
          current.filter(item => !item.system).forEach(item => {
            if (item.id != null) byId.set(String(item.id), item);
          });
          if (message.id != null) byId.set(String(message.id), message);
          else byId.set('pending-' + Date.now() + '-' + Math.random(), message);
          return [...byId.values()].sort((a, b) => {
            if (a.id != null && b.id != null) return Number(a.id) - Number(b.id);
            return String(a.createdAt || '').localeCompare(String(b.createdAt || ''));
          });
        });
        if (message.sender !== usernameRef.current) {
          request('/api/rooms/' + roomId + '/read', { method: 'POST' })
            .catch(e => setError(e.message));
        }
      });
      readSubscriptionRef.current = client.subscribe('/topic/rooms/' + roomId + '/read', () => {
        // Read receipts can arrive in bursts when several messages are sent.
        // Coalesce history refreshes and ignore stale responses so an older
        // request cannot overwrite a newer conversation snapshot.
        if (roomIdRef.current !== String(roomId)) return;
        if (readRefreshTimer) clearTimeout(readRefreshTimer);
        readRefreshTimer = setTimeout(() => {
          const requestVersion = ++readRefreshVersion;
          request('/api/rooms/' + roomId + '/messages')
            .then(history => {
              if (!alive || roomIdRef.current !== String(roomId)
                  || requestVersion !== readRefreshVersion) return;
              setMessages(current => {
                const byId = new Map(
                  history.filter(item => item.id != null).map(item => [String(item.id), item])
                );
                // Keep any live event that arrived after the history request began.
                current.filter(item => !item.system && item.id != null).forEach(item => {
                  if (!byId.has(String(item.id))) byId.set(String(item.id), item);
                });
                const merged = [...byId.values()].sort((a, b) => Number(a.id) - Number(b.id));
                return merged.length ? merged : [{ system: '첫 메시지를 보내 보세요.' }];
              });
            })
            .catch(e => {
              if (alive && roomIdRef.current === String(roomId)
                  && requestVersion === readRefreshVersion) setError(e.message);
            });
        }, 120);
      });
    }
    // Persist this user's read position first, then load history using the
    // committed state so refresh and initial room entry show the same counts.
    request('/api/rooms/' + roomId + '/read', { method: 'POST' })
      .then(() => request('/api/rooms/' + roomId + '/messages'))
      .then(history => {
        if (!alive || roomIdRef.current !== String(roomId)) return;
        setMessages(history.length ? history : [{ system: '첫 메시지를 보내 보세요.' }]);
      })
      .catch(e => { if (alive && roomIdRef.current === String(roomId)) setError(e.message); });
    return () => {
      alive = false;
      if (readRefreshTimer) clearTimeout(readRefreshTimer);
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

  async function saveProfile(event) {
    event.preventDefault();
    const nextName = profileDraft.trim();
    if (nextName.length < 2 || nextName.length > 30) {
      setProfileError(true);
      setProfileNotice('닉네임은 2~30자로 입력해 주세요.');
      return;
    }
    setProfileBusy(true);
    setProfileNotice('');
    try {
      const updated = await request('/api/auth/me', {
        method: 'PUT',
        body: JSON.stringify({ displayName: nextName })
      });
      setDisplayName(updated.displayName || updated.username);
      setProfileError(false);
      setProfileNotice('닉네임을 변경했습니다.');
      setProfileDraft(updated.displayName || updated.username);
    } catch (e) {
      setProfileError(true);
      setProfileNotice(e.message);
    } finally {
      setProfileBusy(false);
    }
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
        <div className="user-row"><span className="avatar">{(displayName || username).slice(0, 1).toUpperCase() || 'U'}</span><span className="user-name" title={username}>{displayName || username}</span><button type="button" className="logout-button" onClick={() => { setProfileDraft(displayName || username); setProfileNotice(''); setProfileError(false); setProfileOpen(true); }}>프로필</button><button type="button" className="logout-button" onClick={logout}>로그아웃</button></div>
      </div>
    </aside>
    {profileOpen && <div className="modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setProfileOpen(false); }}>
      <section className="profile-dialog" role="dialog" aria-modal="true" aria-labelledby="profile-title">
        <div className="profile-dialog-header">
          <div><p className="overline">계정 관리</p><h2 id="profile-title">프로필</h2></div>
          <button type="button" className="dialog-close" onClick={() => setProfileOpen(false)} aria-label="프로필 닫기">×</button>
        </div>
        <p className="profile-account">아이디 <strong>{username}</strong></p>
        <form onSubmit={saveProfile} className="profile-form">
          <label htmlFor="profile-display-name">닉네임</label>
          <input id="profile-display-name" autoFocus minLength={2} maxLength={30} value={profileDraft} onChange={event => setProfileDraft(event.target.value)} required />
          <p className="profile-help">2~30자까지 입력할 수 있습니다. 아이디는 변경되지 않습니다.</p>
          <div className="profile-actions">
            <button type="button" className="outline-button" onClick={() => setProfileOpen(false)}>닫기</button>
            <button type="submit" className="primary-button" disabled={profileBusy}>{profileBusy ? '저장 중...' : '변경 저장'}</button>
          </div>
        </form>
        {profileNotice && <p className={profileError ? 'notice error' : 'notice'} role="status">{profileNotice}</p>}
      </section>
    </div>}
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
                <div className="message-author">{message.sender === username ? '나' : (message.displayName || message.sender)}</div>
                <div className="message-content">{renderMessageContent(message.content)}</div>
              </article>
              <div className="message-meta">
                {Number(message.unreadCount) > 0 && <span className="unread-count" aria-label={message.unreadCount + '명 안 읽음'}>{message.unreadCount}</span>}
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
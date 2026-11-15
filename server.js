const http = require('http');
const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST', 'DELETE']
  }
});

const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// In-memory store
const messages = [];
const userMap = new Map(); // Key: userId -> { userId, shortId, username, color }

const DEFAULT_COLORS = [
  { name: 'Crimson', hex: '#E63946' },
  { name: 'Teal', hex: '#2A9D8F' },
  { name: 'Navy', hex: '#1D3557' },
  { name: 'Terracotta', hex: '#E76F51' },
  { name: 'Purple', hex: '#6A4C93' },
  { name: 'Ocean Blue', hex: '#1982C4' },
  { name: 'Emerald', hex: '#38B000' },
  { name: 'Amber', hex: '#D97706' }
];

const HEX_COLOR_REGEX = /^#[0-9A-Fa-f]{6}$/;

function isValidHexColor(color) {
  return typeof color === 'string' && HEX_COLOR_REGEX.test(color.trim());
}

function getDefaultColor(index) {
  return DEFAULT_COLORS[index % DEFAULT_COLORS.length].hex;
}

function generateShortId(uuid) {
  return uuid.replace(/-/g, '').slice(0, 4).toUpperCase();
}

function resolveUser({ userId, username, color }, fallbackUser = null) {
  const cleanUsername = username.trim();
  const cleanColor = isValidHexColor(color) ? color.trim().toUpperCase() : null;

  if (userId && userMap.has(userId)) {
    const existing = userMap.get(userId);
    existing.username = cleanUsername;
    if (cleanColor) existing.color = cleanColor;
    return existing;
  }

  if (fallbackUser) {
    fallbackUser.username = cleanUsername;
    if (cleanColor) fallbackUser.color = cleanColor;
    userMap.set(fallbackUser.userId, fallbackUser);
    return fallbackUser;
  }

  const newUserId = userId || crypto.randomUUID();
  const newUser = {
    userId: newUserId,
    shortId: generateShortId(newUserId),
    username: cleanUsername,
    color: cleanColor || getDefaultColor(userMap.size)
  };

  userMap.set(newUserId, newUser);
  return newUser;
}

function createMessage({ userId, username, message, color }, fallbackUser = null) {
  if (!username || typeof username !== 'string' || !username.trim()) {
    return { error: 'Field "username" wajib diisi dan tidak boleh kosong.' };
  }

  if (!message || typeof message !== 'string' || !message.trim()) {
    return { error: 'Field "message" wajib diisi dan tidak boleh kosong.' };
  }

  if (color !== undefined && color !== null && color !== '' && !isValidHexColor(color)) {
    return { error: 'Format "color" harus hex 6-digit, contoh: "#1982C4".' };
  }

  const user = resolveUser({ userId, username, color }, fallbackUser);

  const newMessage = {
    id: crypto.randomUUID(),
    userId: user.userId,
    shortId: user.shortId,
    username: user.username,
    message: message.trim(),
    color: user.color,
    timestamp: new Date().toISOString()
  };

  messages.push(newMessage);
  return { data: newMessage, user };
}

// ==========================================
// REST API ENDPOINTS (Untuk Data Awal / Admin)
// ==========================================

app.get('/api/colors', (req, res) => {
  return res.status(200).json({
    success: true,
    data: DEFAULT_COLORS
  });
});

app.get('/api/messages', (req, res) => {
  return res.status(200).json({
    success: true,
    count: messages.length,
    data: messages
  });
});

app.delete('/api/messages', (req, res) => {
  messages.length = 0;
  userMap.clear();

  io.emit('clear');

  return res.status(200).json({
    success: true,
    message: 'Semua pesan dan sesi user telah dibersihkan.'
  });
});

// ==========================================
// SOCKET.IO REAL-TIME EVENTS
// ==========================================

io.on('connection', (socket) => {
  socket.data.user = null;

  // 1. Kirim riwayat pesan saat client pertama kali terhubung
  socket.emit('history', messages);

  // 2. Event registrasi sesi user (opsional sebelum chat, atau langsung via 'chat')
  socket.on('join', (payload = {}, ack) => {
    const { username, color } = payload;

    if (!username || typeof username !== 'string' || !username.trim()) {
      if (typeof ack === 'function') {
        ack({ success: false, error: 'Field "username" wajib diisi.' });
      }
      return;
    }

    if (color !== undefined && color !== null && color !== '' && !isValidHexColor(color)) {
      if (typeof ack === 'function') {
        ack({ success: false, error: 'Format "color" harus hex 6-digit, contoh: "#1982C4".' });
      }
      return;
    }

    const user = resolveUser({ username, color }, socket.data.user);
    socket.data.user = user;

    if (typeof ack === 'function') {
      ack({ success: true, data: user });
    }
  });

  // 3. Event kirim pesan baru
  socket.on('chat', (payload = {}, ack) => {
    const result = createMessage(payload, socket.data.user);

    if (result.error) {
      if (typeof ack === 'function') {
        ack({ success: false, error: result.error });
      } else {
        socket.emit('error_message', { error: result.error });
      }
      return;
    }

    socket.data.user = result.user;

    // Broadcast pesan ke semua client yang terhubung
    io.emit('message', result.data);

    // Kirim konfirmasi balik ke pengirim
    if (typeof ack === 'function') {
      ack({ success: true, data: result.data });
    }
  });
});

server.listen(PORT, () => {
  console.log(`Express + Socket.IO Server aktif di http://localhost:${PORT}`);
});
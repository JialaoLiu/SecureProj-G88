<template>
  <div class="discord-chat">
    <!-- Top Bar -->
    <div class="top-bar">
      <div class="channel-info">
        <span class="channel-icon">#</span>
        <span class="channel-name">socp-secure-chat</span>
        <div class="connection-status">
          <span :class="['status-dot', connected ? 'online' : 'offline']"></span>
          <span class="status-text">{{ connected ? 'Connected' : 'Disconnected' }}</span>
        </div>
      </div>

      <div class="top-controls">
        <div class="search-container">
          <input
            v-model="searchTerm"
            placeholder="Search messages..."
            class="search-input"
            @input="searchMessages"
          />
          <svg class="search-icon" viewBox="0 0 24 24" width="16" height="16">
            <path fill="currentColor" d="M21.707 20.293l-6.388-6.388a7.5 7.5 0 1 0-1.414 1.414l6.388 6.388a1 1 0 0 0 1.414-1.414zM3 10.5a7.5 7.5 0 1 1 15 0 7.5 7.5 0 0 1-15 0z"/>
          </svg>
        </div>

        <button @click="toggleMemberList" class="member-toggle" :class="{ active: showMembers }">
          <svg viewBox="0 0 24 24" width="20" height="20">
            <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
          </svg>
          {{ onlineUsers.length }}
        </button>
      </div>
    </div>

    <!-- Main Content -->
    <div class="main-content">
      <!-- Chat Area -->
      <div class="chat-area">
        <!-- Messages -->
        <div class="messages-wrapper" ref="messagesRef">
          <div v-if="filteredMessages.length === 0" class="empty-state">
            <div class="empty-icon">💬</div>
            <p>{{ searchTerm ? 'No messages found' : 'Welcome to #socp-secure-chat' }}</p>
            <small v-if="!searchTerm">Start typing to send your first message</small>
          </div>

          <div v-for="(msg, idx) in filteredMessages" :key="idx" class="message-item">
            <div class="message-avatar">
              <div :class="['avatar', getAvatarClass(msg.from)]">
                {{ getAvatarInitial(msg.from) }}
              </div>
            </div>
            <div class="message-body">
              <div class="message-header">
                <span class="username">{{ msg.from }}</span>
                <span class="timestamp">{{ formatTime(msg.ts) }}</span>
                <span class="message-type-badge" :class="msg.type.toLowerCase()">{{ getDisplayType(msg.type) }}</span>
              </div>
              <div class="message-text">
                <div v-if="msg.type === 'MSG_DIRECT'" class="direct-msg">
                  {{ msg.payload.ciphertext }}
                </div>
                <div v-else-if="msg.type === 'USER_HELLO'" class="system-msg">
                  👋 <strong>{{ msg.payload.client }}</strong> joined the channel
                </div>
                <div v-else-if="msg.type === 'HEARTBEAT'" class="system-msg">
                  💓 Heartbeat
                </div>
                <div v-else-if="msg.type === 'ERROR'" class="error-msg">
                  ⚠️ {{ msg.payload.detail }}
                </div>
                <div v-else class="raw-msg">
                  <details>
                    <summary>{{ msg.type }} payload</summary>
                    <pre>{{ JSON.stringify(msg.payload, null, 2) }}</pre>
                  </details>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Message Input -->
        <div class="message-input-area">
          <div class="input-wrapper">
            <input
              v-model="inputMessage"
              @keyup.enter="sendMessage"
              :placeholder="`Message #socp-secure-chat`"
              :disabled="!connected"
              class="message-input"
            />
            <div class="input-buttons">
              <button @click="sendMessage" :disabled="!connected || !inputMessage.trim()" class="send-btn">
                <svg viewBox="0 0 24 24" width="16" height="16">
                  <path fill="currentColor" d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Quick Actions -->
          <div class="quick-actions">
            <button @click="sendHeartbeat" :disabled="!connected" class="quick-btn">
              💓 Heartbeat
            </button>
            <button @click="testRateLimit" :disabled="!connected" class="quick-btn">
              ⚡ Rate Test
            </button>
            <button @click="clearMessages" class="quick-btn">
              🗑️ Clear
            </button>
          </div>
        </div>
      </div>

      <!-- Member List -->
      <div v-if="showMembers" class="member-list">
        <div class="member-header">
          <h3>Online — {{ onlineUsers.length }}</h3>
        </div>
        <div class="member-items">
          <div v-for="user in onlineUsers" :key="user.id" class="member-item">
            <div :class="['member-avatar', user.status]">
              {{ getAvatarInitial(user.name) }}
            </div>
            <div class="member-info">
              <span class="member-name">{{ user.name }}</span>
              <span class="member-status">{{ user.activity }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { WS } from '../services/ws'

const connected = ref(false)
const messages = ref<any[]>([])
const inputMessage = ref('')
const messagesRef = ref<HTMLElement>()
const wsUrl = import.meta.env.VITE_WS_URL || 'ws://127.0.0.1:8080'

// Discord-style features
const showMembers = ref(true)
const searchTerm = ref('')
const filteredMessages = ref<any[]>([])

// Mock online users (在实际应用中这些数据会来自Person A的DHT查询)
const onlineUsers = ref([
  { id: 1, name: 'frontend-dev', status: 'online', activity: 'Testing SOCP' },
  { id: 2, name: 'server', status: 'online', activity: 'Processing messages' },
  { id: 3, name: 'alice', status: 'away', activity: 'Idle' },
  { id: 4, name: 'bob', status: 'dnd', activity: 'Do not disturb' },
])

let ws: WS

onMounted(() => {
  ws = new WS(wsUrl)

  ws.connect((msg) => {
    messages.value.push(msg)
    searchMessages() // 更新过滤后的消息
    nextTick(() => {
      messagesRef.value?.scrollTo(0, messagesRef.value.scrollHeight)
    })
  })

  // 简单连接状态检测
  setTimeout(() => connected.value = true, 1000)

  // 初始化过滤消息
  filteredMessages.value = messages.value
})

onUnmounted(() => {
  ws?.disconnect()
})

function sendMessage() {
  if (!inputMessage.value.trim()) return

  const msg = {
    type: "MSG_DIRECT",
    from: "frontend-dev",
    to: "test-target",
    ts: Date.now(),
    payload: {
      ciphertext: inputMessage.value, // 简化：直接发明文
      sender_pub: "dev-pubkey",
      content_sig: "dev-sig"
    },
    sig: "dev-mock"
  }

  ws.send(msg)
  inputMessage.value = ''
}

function sendHeartbeat() {
  const hb = {
    type: "HEARTBEAT",
    from: "frontend-dev",
    to: "server",
    ts: Date.now(),
    payload: {},
    sig: "dev-mock"
  }
  ws.send(hb)
}

function formatTime(ts: number) {
  return new Date(ts).toLocaleTimeString()
}

function getMessageType(msg: any) {
  if (msg.from === 'frontend-dev') return 'outgoing'
  if (msg.type === 'ERROR') return 'error'
  if (msg.type === 'HEARTBEAT') return 'system'
  return 'incoming'
}

function getDisplayType(type: string) {
  const typeMap: Record<string, string> = {
    'MSG_DIRECT': '💬 Message',
    'HEARTBEAT': '💓 Heartbeat',
    'USER_HELLO': '👋 Join',
    'ERROR': '⚠️ Error',
    'SERVER_WELCOME': '🎉 Welcome',
    'USER_ADVERTISE': '📢 User Update'
  }
  return typeMap[type] || type
}

function testRateLimit() {
  // 1秒内发送15条消息测试限流
  for (let i = 0; i < 15; i++) {
    setTimeout(() => sendHeartbeat(), i * 60) // 60ms间隔
  }
}

function clearMessages() {
  messages.value = []
  searchMessages()
}

// Discord-style functions
function toggleMemberList() {
  showMembers.value = !showMembers.value
}

function searchMessages() {
  if (!searchTerm.value) {
    filteredMessages.value = messages.value
    return
  }

  const term = searchTerm.value.toLowerCase()
  filteredMessages.value = messages.value.filter(msg => {
    // 搜索消息内容
    if (msg.type === 'MSG_DIRECT' && msg.payload.ciphertext?.toLowerCase().includes(term)) {
      return true
    }
    // 搜索用户名
    if (msg.from?.toLowerCase().includes(term)) {
      return true
    }
    // 搜索消息类型
    if (msg.type?.toLowerCase().includes(term)) {
      return true
    }
    return false
  })
}

function getAvatarInitial(name: string) {
  if (!name) return '?'
  return name.charAt(0).toUpperCase()
}

function getAvatarClass(from: string) {
  // 根据用户名生成不同颜色
  const colors = ['red', 'blue', 'green', 'purple', 'orange', 'pink']
  const hash = from.split('').reduce((a, b) => {
    a = ((a << 5) - a) + b.charCodeAt(0)
    return a & a
  }, 0)
  return colors[Math.abs(hash) % colors.length]
}
</script>

<style scoped>
/* Discord-like theme */
.discord-chat {
  height: 100vh;
  display: flex;
  flex-direction: column;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background-color: #36393f;
  color: #dcddde;
}

/* Top Bar */
.top-bar {
  height: 48px;
  background-color: #2f3136;
  border-bottom: 1px solid #202225;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  box-shadow: 0 1px 0 rgba(4, 4, 5, 0.2), 0 1px 2px rgba(6, 6, 7, 0.05), 0 2px 10px rgba(4, 4, 5, 0.1);
}

.channel-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.channel-icon {
  font-size: 20px;
  color: #8e9297;
  font-weight: bold;
}

.channel-name {
  font-weight: 600;
  color: #ffffff;
  font-size: 16px;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: 12px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.online { background-color: #3ba55d; }
.status-dot.offline { background-color: #ed4245; }

.status-text {
  font-size: 12px;
  color: #b9bbbe;
}

.top-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-container {
  position: relative;
}

.search-input {
  background-color: #40444b;
  border: none;
  border-radius: 4px;
  padding: 6px 12px 6px 32px;
  font-size: 14px;
  color: #dcddde;
  width: 200px;
  outline: none;
}

.search-input::placeholder {
  color: #72767d;
}

.search-input:focus {
  background-color: #484c52;
}

.search-icon {
  position: absolute;
  left: 8px;
  top: 50%;
  transform: translateY(-50%);
  color: #72767d;
}

.member-toggle {
  background: none;
  border: none;
  color: #b9bbbe;
  cursor: pointer;
  padding: 6px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  transition: all 0.2s;
}

.member-toggle:hover,
.member-toggle.active {
  background-color: #40444b;
  color: #ffffff;
}

/* Main Content */
.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: #36393f;
}

.messages-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 16px 16px 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #72767d;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.6;
}

/* Messages */
.message-item {
  display: flex;
  padding: 4px 0;
  margin-bottom: 8px;
  position: relative;
  transition: background-color 0.1s;
}

.message-item:hover {
  background-color: rgba(4, 4, 5, 0.07);
  margin: 0 -16px 8px;
  padding: 4px 16px;
}

.message-avatar {
  width: 40px;
  height: 40px;
  margin-right: 16px;
  flex-shrink: 0;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: white;
  font-size: 16px;
}

.avatar.red { background-color: #ed4245; }
.avatar.blue { background-color: #5865f2; }
.avatar.green { background-color: #3ba55d; }
.avatar.purple { background-color: #9266cc; }
.avatar.orange { background-color: #faa61a; }
.avatar.pink { background-color: #eb459e; }

.message-body {
  flex: 1;
  min-width: 0;
}

.message-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 2px;
}

.username {
  font-weight: 500;
  color: #ffffff;
  font-size: 16px;
}

.timestamp {
  font-size: 12px;
  color: #72767d;
  margin-left: 4px;
}

.message-type-badge {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 8px;
  font-weight: 500;
  text-transform: uppercase;
}

.message-type-badge.msg_direct {
  background-color: #5865f2;
  color: white;
}

.message-type-badge.heartbeat {
  background-color: #faa61a;
  color: white;
}

.message-type-badge.user_hello {
  background-color: #3ba55d;
  color: white;
}

.message-type-badge.error {
  background-color: #ed4245;
  color: white;
}

.message-text {
  color: #dcddde;
  line-height: 1.375;
  font-size: 16px;
  word-wrap: break-word;
}

.direct-msg {
  color: #dcddde;
}

.system-msg {
  color: #b9bbbe;
  font-style: italic;
  font-size: 14px;
}

.error-msg {
  color: #ed4245;
  font-weight: 500;
}

.raw-msg details {
  margin-top: 4px;
}

.raw-msg summary {
  color: #00b0f4;
  cursor: pointer;
  font-size: 14px;
}

.raw-msg pre {
  background-color: #2f3136;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  margin-top: 4px;
  overflow-x: auto;
}

/* Message Input */
.message-input-area {
  padding: 16px;
  background-color: #36393f;
}

.input-wrapper {
  background-color: #40444b;
  border-radius: 8px;
  padding: 11px 16px;
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.message-input {
  flex: 1;
  background: none;
  border: none;
  color: #dcddde;
  font-size: 16px;
  outline: none;
  resize: none;
}

.message-input::placeholder {
  color: #72767d;
}

.input-buttons {
  display: flex;
  align-items: center;
}

.send-btn {
  background: none;
  border: none;
  color: #72767d;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: color 0.2s;
}

.send-btn:hover:not(:disabled) {
  color: #dcddde;
}

.send-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.quick-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.quick-btn {
  background-color: #4f545c;
  border: none;
  color: #dcddde;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.quick-btn:hover:not(:disabled) {
  background-color: #5865f2;
}

.quick-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

/* Member List */
.member-list {
  width: 240px;
  background-color: #2f3136;
  border-left: 1px solid #202225;
  display: flex;
  flex-direction: column;
}

.member-header {
  padding: 16px 16px 8px;
  border-bottom: 1px solid #202225;
}

.member-header h3 {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: #8e9297;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.member-items {
  flex: 1;
  overflow-y: auto;
  padding: 8px 8px 16px;
}

.member-item {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  border-radius: 4px;
  margin-bottom: 1px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.member-item:hover {
  background-color: #40444b;
}

.member-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  margin-right: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: white;
  font-size: 14px;
  position: relative;
}

.member-avatar.online { background-color: #3ba55d; }
.member-avatar.away { background-color: #faa61a; }
.member-avatar.dnd { background-color: #ed4245; }

.member-avatar::after {
  content: '';
  position: absolute;
  bottom: -2px;
  right: -2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid #2f3136;
}

.member-avatar.online::after { background-color: #3ba55d; }
.member-avatar.away::after { background-color: #faa61a; }
.member-avatar.dnd::after { background-color: #ed4245; }

.member-info {
  flex: 1;
  min-width: 0;
}

.member-name {
  display: block;
  font-weight: 500;
  color: #ffffff;
  font-size: 14px;
  line-height: 18px;
}

.member-status {
  display: block;
  font-size: 12px;
  color: #b9bbbe;
  line-height: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Scrollbar */
.messages-wrapper::-webkit-scrollbar,
.member-items::-webkit-scrollbar {
  width: 8px;
}

.messages-wrapper::-webkit-scrollbar-track,
.member-items::-webkit-scrollbar-track {
  background: transparent;
}

.messages-wrapper::-webkit-scrollbar-thumb,
.member-items::-webkit-scrollbar-thumb {
  background-color: #202225;
  border-radius: 4px;
}

.messages-wrapper::-webkit-scrollbar-thumb:hover,
.member-items::-webkit-scrollbar-thumb:hover {
  background-color: #36393f;
}

/* Responsive */
@media (max-width: 768px) {
  .member-list {
    position: absolute;
    right: 0;
    top: 48px;
    height: calc(100vh - 48px);
    z-index: 10;
    box-shadow: -2px 0 10px rgba(4, 4, 5, 0.2);
  }

  .search-input {
    width: 140px;
  }

  .quick-actions {
    justify-content: center;
  }
}
</style>
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

        <div class="user-info">
          <div class="current-user">
            <div :class="['user-avatar', getAvatarClass(currentUser)]">
              {{ getAvatarInitial(currentUser) }}
            </div>
            <span class="username-display">{{ currentUser }}</span>
          </div>
          <button @click="handleLogout" class="logout-btn" title="Logout">
            <svg viewBox="0 0 24 24" width="16" height="16">
              <path fill="currentColor" d="M16 17v-3H9v-4h7V7l5 5-5 5M14 2a2 2 0 0 1 2 2v2h-2V4H4v16h10v-2h2v2a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h10z"/>
            </svg>
          </button>
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
      <!-- Chat List Sidebar -->
      <div class="chat-list">
        <div class="chat-list-header">
          <h3>Chats</h3>
        </div>
        <div class="chat-items">
          <!-- Group Chat -->
          <div
            class="chat-item"
            :class="{ active: currentChatType === 'group' }"
            @click="selectGroupChat()"
          >
            <div class="chat-avatar group-avatar">#</div>
            <div class="chat-info">
              <div class="chat-name">General</div>
              <div class="chat-preview">{{ getLastGroupMessage() }}</div>
            </div>
            <div v-if="getUnreadCount('group') > 0" class="unread-badge">
              {{ getUnreadCount('group') }}
            </div>
          </div>

          <!-- Private Chats -->
          <div
            v-for="user in onlineUsers"
            :key="user.id"
            v-show="user.id !== currentUser"
            class="chat-item"
            :class="{ active: currentChatType === 'private' && currentChatTarget === user.id }"
            @click="selectPrivateChat(user.id)"
          >
            <div :class="['chat-avatar', 'user-avatar', user.status]">
              {{ getAvatarInitial(user.name) }}
            </div>
            <div class="chat-info">
              <div class="chat-name">{{ user.name }}</div>
              <div class="chat-preview">{{ getLastPrivateMessage(user.id) }}</div>
            </div>
            <div v-if="getUnreadCount(user.id) > 0" class="unread-badge">
              {{ getUnreadCount(user.id) }}
            </div>
          </div>
        </div>
      </div>

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
                <div v-else-if="msg.type === 'FILE_START'" class="file-msg">
                  File transfer started: {{ msg.payload.name }} ({{ formatFileSize(msg.payload.size) }})
                </div>
                <div v-else-if="msg.type === 'FILE_END'" class="file-msg">
                  File transfer completed: {{ getFileFromId(msg.payload.file_id) }}
                  <a v-if="receivedFiles[msg.payload.file_id]" :href="receivedFiles[msg.payload.file_id].url" :download="receivedFiles[msg.payload.file_id].name" class="download-link">
                    Download
                  </a>
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

        <!-- File Upload Progress -->
        <div v-if="uploadingFiles.length > 0" class="file-upload-area">
          <div v-for="upload in uploadingFiles" :key="upload.id" class="upload-item">
            <div class="upload-header">
              <span class="upload-name">File: {{ upload.fileName }}</span>
              <span class="upload-size">{{ formatFileSize(upload.totalSize) }}</span>
              <button @click="cancelUpload(upload.id)" class="cancel-btn">Cancel</button>
            </div>
            <div class="upload-progress">
              <div class="progress-bar">
                <div class="progress-fill" :style="{ width: upload.progress + '%' }"></div>
              </div>
              <span class="progress-text">{{ upload.progress.toFixed(1) }}%</span>
            </div>
            <div class="upload-status" :class="upload.status">
              {{ upload.statusText }}
            </div>
          </div>
        </div>

        <!-- Message Input -->
        <div class="message-input-area">
          <div class="input-wrapper">
            <input
              v-model="inputMessage"
              @keyup.enter="sendMessage"
              :placeholder="getInputPlaceholder()"
              :disabled="!connected"
              class="message-input"
            />
            <div class="input-buttons">
              <input
                type="file"
                ref="fileInput"
                @change="onFileSelected"
                style="display: none"
                multiple
              />
              <button @click="$refs.fileInput.click()" :disabled="!connected" class="file-btn" title="Send file">
                File
              </button>
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
              Heartbeat
            </button>
            <button @click="testRateLimit" :disabled="!connected" class="quick-btn">
              Rate Test
            </button>
            <button @click="clearMessages" class="quick-btn">
              Clear
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

const props = defineProps<{
  currentUser: string
}>()

const emit = defineEmits<{
  logout: []
}>()

const connected = ref(false)
const messages = ref<any[]>([])
const inputMessage = ref('')
const messagesRef = ref<HTMLElement>()
const wsUrl = import.meta.env.VITE_WS_URL || 'ws://127.0.0.1:8080'

// Discord-style features
const showMembers = ref(true)
const searchTerm = ref('')
const filteredMessages = ref<any[]>([])

// 真实的在线用户数据（通过DHT查询获取）
const onlineUsers = ref<any[]>([])

// 文件传输相关状态
const uploadingFiles = ref<any[]>([])
const receivedFiles = ref<Record<string, { url: string; name: string }>>({})
const fileTransfers = ref<Record<string, { chunks: Record<number, string>; metadata: any }>>({})

// 私聊状态管理
const currentChatType = ref<'group' | 'private'>('group')
const currentChatTarget = ref<string>('*')
const groupMessages = ref<any[]>([])
const privateMessages = ref<Record<string, any[]>>({})

let ws: WS

onMounted(() => {
  ws = new WS(wsUrl)

  ws.connect((msg) => {
    // 处理在线用户列表响应
    if (msg.type === 'USER_LIST_RESPONSE' && msg.payload && msg.payload.online_users) {
      onlineUsers.value = msg.payload.online_users
      console.log('[WS] Updated online users:', onlineUsers.value)
      return
    }

    // 处理文件传输消息
    if (msg.type === 'FILE_START') {
      handleFileStart(msg)
    } else if (msg.type === 'FILE_CHUNK') {
      handleFileChunk(msg)
    } else if (msg.type === 'FILE_END') {
      handleFileEnd(msg)
    } else if (msg.type === 'ACK') {
      handleAck(msg)
    }

    // 将消息路由到正确的聊天
    if (msg.to === '*' || msg.from === props.currentUser) {
      // 群聊消息
      groupMessages.value.push(msg)
    } else if (msg.to === props.currentUser) {
      // 接收到的私聊消息
      if (!privateMessages.value[msg.from]) {
        privateMessages.value[msg.from] = []
      }
      privateMessages.value[msg.from].push(msg)
    } else if (msg.from === props.currentUser) {
      // 发送的私聊消息
      if (!privateMessages.value[msg.to]) {
        privateMessages.value[msg.to] = []
      }
      privateMessages.value[msg.to].push(msg)
    }

    // 保持旧的messages数组用于向后兼容
    messages.value.push(msg)
    updateFilteredMessages()
    nextTick(() => {
      messagesRef.value?.scrollTo(0, messagesRef.value.scrollHeight)
    })
  })

  // 简单连接状态检测
  setTimeout(() => {
    connected.value = true
    // 连接成功后请求在线用户列表
    requestOnlineUsers()
  }, 1000)

  // 初始化过滤消息
  filteredMessages.value = messages.value

  // 定期更新在线用户列表
  setInterval(requestOnlineUsers, 10000) // 每10秒更新一次
})

onUnmounted(() => {
  ws?.disconnect()
})

function sendMessage() {
  if (!inputMessage.value.trim()) return

  const msg = {
    type: "MSG_DIRECT",
    from: props.currentUser,
    to: currentChatTarget.value,
    ts: Date.now(),
    payload: {
      ciphertext: inputMessage.value, // 简化：直接发明文
      sender_pub: `${props.currentUser}-pubkey`,
      content_sig: `${props.currentUser}-sig`
    },
    sig: "dev-mock"
  }

  ws.send(msg)
  inputMessage.value = ''
}

function sendHeartbeat() {
  const hb = {
    type: "HEARTBEAT",
    from: props.currentUser,
    to: "server",
    ts: Date.now(),
    payload: {},
    sig: "dev-mock"
  }
  ws.send(hb)
}

function handleLogout() {
  emit('logout')
}

function formatTime(ts: number) {
  return new Date(ts).toLocaleTimeString()
}

// 文件传输相关方法
function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files) {
    for (const file of input.files) {
      startFileUpload(file)
    }
    input.value = '' // 重置input以允许选择相同文件
  }
}

function startFileUpload(file: File) {
  const fileId = generateFileId()
  const uploadInfo = {
    id: fileId,
    fileName: file.name,
    totalSize: file.size,
    progress: 0,
    status: 'uploading',
    statusText: 'Starting upload...',
    file: file
  }

  uploadingFiles.value.push(uploadInfo)

  // 发送FILE_START消息
  ws.sendFile(file, currentChatTarget.value)
}

function cancelUpload(fileId: string) {
  const index = uploadingFiles.value.findIndex(upload => upload.id === fileId)
  if (index !== -1) {
    uploadingFiles.value.splice(index, 1)
  }
}

function formatFileSize(bytes: number) {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function generateFileId() {
  return Date.now().toString(36) + Math.random().toString(36).substr(2)
}

function getFileFromId(fileId: string) {
  const transfer = fileTransfers.value[fileId]
  return transfer ? transfer.metadata.name : fileId
}

// 文件传输消息处理
function handleFileStart(msg: any) {
  const { file_id, name, size } = msg.payload
  fileTransfers.value[file_id] = {
    chunks: {},
    metadata: { name, size, totalChunks: Math.ceil(size / (256 * 1024)) }
  }
  console.log(`[FileTransfer] Started receiving: ${name} (${size} bytes)`)
}

function handleFileChunk(msg: any) {
  const { file_id, index, ciphertext } = msg.payload
  if (fileTransfers.value[file_id]) {
    fileTransfers.value[file_id].chunks[index] = ciphertext
    console.log(`[FileTransfer] Received chunk ${index} for ${file_id}`)
  }
}

function handleFileEnd(msg: any) {
  const { file_id } = msg.payload
  const transfer = fileTransfers.value[file_id]
  if (transfer) {
    // 重组文件
    const chunks = transfer.chunks
    const sortedIndices = Object.keys(chunks).map(Number).sort((a, b) => a - b)
    const binaryData = sortedIndices.map(index => atob(chunks[index])).join('')

    // 创建Blob并生成下载链接
    const bytes = new Uint8Array(binaryData.length)
    for (let i = 0; i < binaryData.length; i++) {
      bytes[i] = binaryData.charCodeAt(i)
    }

    const blob = new Blob([bytes])
    const url = URL.createObjectURL(blob)

    receivedFiles.value[file_id] = {
      url: url,
      name: transfer.metadata.name
    }

    console.log(`[FileTransfer] Completed: ${transfer.metadata.name}`)
  }
}

function handleAck(msg: any) {
  // 处理ACK消息，更新上传进度
  const msgRef = msg.payload.msg_ref
  console.log(`[FileTransfer] ACK received for: ${msgRef}`)
}

// 私聊UI相关方法
function selectGroupChat() {
  currentChatType.value = 'group'
  currentChatTarget.value = '*'
  updateFilteredMessages()
}

function selectPrivateChat(userId: string) {
  currentChatType.value = 'private'
  currentChatTarget.value = userId

  // 初始化私聊消息数组如果不存在
  if (!privateMessages.value[userId]) {
    privateMessages.value[userId] = []
  }

  updateFilteredMessages()
}

function updateFilteredMessages() {
  if (currentChatType.value === 'group') {
    filteredMessages.value = groupMessages.value
  } else {
    filteredMessages.value = privateMessages.value[currentChatTarget.value] || []
  }

  // 应用搜索过滤
  if (searchTerm.value) {
    searchMessages()
  }
}

function getInputPlaceholder() {
  if (currentChatType.value === 'group') {
    return 'Message #general'
  } else {
    const user = onlineUsers.value.find(u => u.id === currentChatTarget.value)
    return `Message @${user?.name || currentChatTarget.value}`
  }
}

function getLastGroupMessage() {
  const lastMsg = groupMessages.value[groupMessages.value.length - 1]
  if (!lastMsg) return 'No messages yet'

  if (lastMsg.type === 'MSG_DIRECT') {
    return `${lastMsg.from}: ${lastMsg.payload.ciphertext.substring(0, 30)}...`
  }
  return `${lastMsg.type}`
}

function getLastPrivateMessage(userId: string) {
  const msgs = privateMessages.value[userId]
  if (!msgs || msgs.length === 0) return 'No messages yet'

  const lastMsg = msgs[msgs.length - 1]
  if (lastMsg.type === 'MSG_DIRECT') {
    return lastMsg.payload.ciphertext.substring(0, 30) + '...'
  }
  return lastMsg.type
}

function getUnreadCount(target: string) {
  // 简化实现：总是返回0，实际实现需要跟踪已读状态
  return 0
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

function requestOnlineUsers() {
  if (!connected.value) return

  const userListRequest = {
    type: "USER_LIST_REQUEST",
    from: props.currentUser,
    to: "server",
    ts: Date.now(),
    payload: {},
    sig: "dev-mock"
  }

  ws.send(userListRequest)
  console.log('[WS] Requested online users list')
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
  if (!from) return 'blue' // 默认颜色
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

/* User Info Styles */
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.current-user {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: white;
  font-size: 12px;
}

.user-avatar.red { background-color: #ed4245; }
.user-avatar.blue { background-color: #5865f2; }
.user-avatar.green { background-color: #3ba55d; }
.user-avatar.purple { background-color: #9266cc; }
.user-avatar.orange { background-color: #faa61a; }
.user-avatar.pink { background-color: #eb459e; }

.username-display {
  font-size: 14px;
  font-weight: 500;
  color: #ffffff;
}

.logout-btn {
  background: none;
  border: none;
  color: #b9bbbe;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
}

.logout-btn:hover {
  background-color: #ed4245;
  color: white;
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

  .username-display {
    display: none;
  }
}

/* 文件传输样式 */
.file-upload-area {
  background-color: #2f3136;
  border-top: 1px solid #40444b;
  padding: 12px 16px;
  margin: 0;
}

.upload-item {
  background-color: #40444b;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
}

.upload-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.upload-name {
  font-weight: 500;
  color: #ffffff;
}

.upload-size {
  color: #b9bbbe;
  font-size: 12px;
}

.cancel-btn {
  background: #ed4245;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
}

.cancel-btn:hover {
  background: #c23616;
}

.upload-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background-color: #4f545c;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background-color: #5865f2;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 12px;
  color: #b9bbbe;
  min-width: 40px;
  text-align: right;
}

.upload-status {
  font-size: 12px;
  color: #b9bbbe;
}

.upload-status.uploading {
  color: #faa61a;
}

.upload-status.completed {
  color: #3ba55d;
}

.upload-status.error {
  color: #ed4245;
}

.file-btn {
  background: transparent;
  border: none;
  color: #b9bbbe;
  padding: 8px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s ease;
  margin-right: 8px;
}

.file-btn:hover {
  background-color: #4f545c;
  color: #dcddde;
}

.file-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.file-msg {
  color: #3ba55d;
  font-style: italic;
  padding: 8px;
  background-color: rgba(59, 165, 93, 0.1);
  border-radius: 4px;
  margin: 4px 0;
}

.download-link {
  color: #00b0f4;
  text-decoration: none;
  margin-left: 8px;
  font-weight: 500;
}

.download-link:hover {
  text-decoration: underline;
}

/* 聊天列表样式 */
.chat-list {
  width: 240px;
  background-color: #2f3136;
  border-right: 1px solid #202225;
  display: flex;
  flex-direction: column;
}

.chat-list-header {
  padding: 16px;
  border-bottom: 1px solid #40444b;
}

.chat-list-header h3 {
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.chat-items {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.chat-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  margin-bottom: 2px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.chat-item:hover {
  background-color: #40444b;
}

.chat-item.active {
  background-color: #5865f2;
}

.chat-item.active:hover {
  background-color: #4752c4;
}

.chat-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  font-weight: 600;
  font-size: 14px;
}

.group-avatar {
  background-color: #5865f2;
  color: white;
}

.user-avatar {
  background-color: #747f8d;
  color: white;
  position: relative;
}

.user-avatar.online::after {
  content: '';
  position: absolute;
  bottom: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  background-color: #3ba55d;
  border: 2px solid #2f3136;
  border-radius: 50%;
}

.chat-info {
  flex: 1;
  min-width: 0;
}

.chat-name {
  font-weight: 500;
  color: #ffffff;
  font-size: 14px;
  margin-bottom: 2px;
}

.chat-preview {
  font-size: 12px;
  color: #b9bbbe;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.unread-badge {
  background-color: #ed4245;
  color: white;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 8px;
  min-width: 16px;
  text-align: center;
}

.main-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
}
</style>
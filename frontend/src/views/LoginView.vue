<template>
  <div class="login-container">
    <div class="login-card">
      <div class="logo-section">
        <div class="logo">ECHO</div>
        <h1>SOCP Secure Chat</h1>
        <p>End-to-end encrypted messaging platform</p>
      </div>

      <div class="form-section">
        <div class="tab-switches">
          <button
            :class="['tab', { active: isLogin }]"
            @click="isLogin = true"
          >
            Login
          </button>
          <button
            :class="['tab', { active: !isLogin }]"
            @click="isLogin = false"
          >
            Register
          </button>
        </div>

        <form @submit.prevent="handleSubmit" class="auth-form">
          <div class="form-group">
            <label for="username">Username</label>
            <input
              id="username"
              v-model="username"
              type="text"
              placeholder="Enter your username"
              required
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label for="password">Password</label>
            <input
              id="password"
              v-model="password"
              type="password"
              placeholder="Enter your password"
              required
              :disabled="loading"
            />
          </div>

          <div v-if="!isLogin" class="form-group">
            <label for="confirmPassword">Confirm Password</label>
            <input
              id="confirmPassword"
              v-model="confirmPassword"
              type="password"
              placeholder="Confirm your password"
              required
              :disabled="loading"
            />
          </div>

          <div v-if="error" class="error-message">
            {{ error }}
          </div>

          <button
            type="submit"
            class="submit-btn"
            :disabled="loading"
          >
            <span v-if="loading">{{ isLogin ? 'Logging in...' : 'Registering...' }}</span>
            <span v-else>{{ isLogin ? 'Login' : 'Register' }}</span>
          </button>
        </form>

        <div class="demo-section">
          <p class="demo-text">Quick Demo Access:</p>
          <div class="demo-buttons">
            <button @click="demoLogin('alice')" class="demo-btn" :disabled="loading">
              Login as Alice
            </button>
            <button @click="demoLogin('bob')" class="demo-btn" :disabled="loading">
              Login as Bob
            </button>
            <button @click="demoLogin('charlie')" class="demo-btn" :disabled="loading">
              Login as Charlie
            </button>
          </div>
        </div>

        <div class="features-section">
          <h3>Security Features</h3>
          <ul>
            <li>RSA-OAEP + AES-256-GCM Encryption</li>
            <li>Digital Signatures</li>
            <li>Anti-Replay Protection</li>
            <li>DHT-based User Discovery</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { AuthService } from '../services/auth'

const emit = defineEmits<{
  login: [username: string]
}>()

const isLogin = ref(true)
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const error = ref('')

async function handleSubmit() {
  error.value = ''

  if (!username.value.trim()) {
    error.value = 'Username is required'
    return
  }

  if (!password.value) {
    error.value = 'Password is required'
    return
  }

  if (!isLogin.value) {
    if (password.value !== confirmPassword.value) {
      error.value = 'Passwords do not match'
      return
    }
    if (password.value.length < 6) {
      error.value = 'Password must be at least 6 characters'
      return
    }
  }

  loading.value = true

  try {
    if (isLogin.value) {
      // Login with JWT
      const response = await AuthService.login({
        username: username.value.trim(),
        password: password.value
      })
      console.log('Login successful:', response.username)
      emit('login', response.username)
    } else {
      // Register with JWT
      const response = await AuthService.register({
        username: username.value.trim(),
        password: password.value
      })
      console.log('Registration successful:', response.username)
      emit('login', response.username)
    }
  } catch (err: any) {
    error.value = err.message || 'Authentication failed. Please try again.'
  } finally {
    loading.value = false
  }
}

async function demoLogin(demoUsername: string) {
  username.value = demoUsername
  password.value = 'demo123'
  loading.value = true

  try {
    const response = await AuthService.login({
      username: demoUsername,
      password: 'demo123'
    })
    emit('login', response.username)
  } catch (err: any) {
    error.value = err.message || 'Demo login failed'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #141e30 0%, #243b55 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.login-card {
  background: #4a4e69;
  border-radius: 16px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  width: 100%;
  max-width: 400px;
  animation: slideUp 0.5s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.logo-section {
  background: #4a4e69;
  color: white;
  padding: 40px 30px 30px;
  text-align: center;
}

.logo {
  font-size: 48px;
  margin-bottom: 10px;
}

.logo-section h1 {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
}

.logo-section p {
  margin: 0;
  opacity: 0.9;
  font-size: 14px;
}

.form-section {
  padding: 30px;
  background: #4a4e69;
  color: #ffffff;
}

.form-section label {
  color: #ffffff;
}

.form-section .demo-text {
  color: #ffffff;
}

.form-section h3 {
  color: #ffffff;
}

.form-section li {
  color: #ffffff;
}

.form-section * {
  color: #ffffff !important;
}

.form-section input {
  background-color: #1a2332 !important;
  border-color: #2d3748 !important;
  color: #ffffff !important;
}

.form-section input::placeholder {
  color: #a0aec0 !important;
}

.tab.active {
  background: #4a4e69 !important;
  color: #ffffff !important;
}

.demo-btn {
  background: #4a4e69 !important;
  color: #ffffff !important;
}

.tab-switches {
  display: flex;
  background: #1a2332;
  border-radius: 8px;
  padding: 4px;
  margin-bottom: 24px;
}

.tab {
  flex: 1;
  background: none;
  border: none;
  padding: 10px;
  border-radius: 6px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  color: #6c757d;
}

.tab.active {
  background: white;
  color: #495057;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-weight: 500;
  color: #374151;
  font-size: 14px;
}

.form-group input {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e5e7eb;
  border-radius: 8px;
  font-size: 16px;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-group input:focus {
  outline: none;
  border-color: #243b55;
}

.form-group input:disabled {
  background-color: #f9fafb;
  cursor: not-allowed;
}

.error-message {
  background: #fee2e2;
  color: #991b1b;
  padding: 12px;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 20px;
  border: 1px solid #dc2626;
  font-weight: 500;
}

.submit-btn {
  width: 100%;
  background: linear-gradient(135deg, #141e30 0%, #243b55 100%);
  color: white;
  border: none;
  padding: 14px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}

.demo-section {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #e5e7eb;
}

.demo-text {
  text-align: center;
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 16px;
}

.demo-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demo-btn {
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  color: #374151;
  padding: 10px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.demo-btn:hover:not(:disabled) {
  background: #e5e7eb;
  border-color: #9ca3af;
}

.demo-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.features-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
}

.features-section h3 {
  margin: 0 0 12px;
  font-size: 16px;
  color: #374151;
}

.features-section ul {
  margin: 0;
  padding-left: 0;
  list-style: none;
}

.features-section li {
  padding: 4px 0;
  font-size: 14px;
  color: #6b7280;
}

@media (max-width: 480px) {
  .login-container {
    padding: 10px;
  }

  .form-section {
    padding: 20px;
  }

  .logo-section {
    padding: 30px 20px 20px;
  }
}
</style>
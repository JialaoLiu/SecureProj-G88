<template>
  <div id="app">
    <LoginView v-if="!isActuallyLoggedIn" @login="handleLogin" />
    <ChatView v-else :currentUser="currentUser" @logout="handleLogout" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import LoginView from './views/LoginView.vue'
import ChatView from './views/ChatView.vue'

const isLoggedIn = ref(false)
const currentUser = ref('')

// 从localStorage恢复登录状态
const savedUser = localStorage.getItem('currentUser')
if (savedUser) {
  currentUser.value = savedUser
  isLoggedIn.value = true
}

// 确保只有在有用户名时才认为已登录
const isActuallyLoggedIn = computed(() => isLoggedIn.value && currentUser.value.trim() !== '')

function handleLogin(username: string) {
  currentUser.value = username
  isLoggedIn.value = true
  localStorage.setItem('currentUser', username)
  console.log(`User logged in: ${username}`)
}

function handleLogout() {
  currentUser.value = ''
  isLoggedIn.value = false
  localStorage.removeItem('currentUser')
  console.log('User logged out')
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  margin: 0;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

#app {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
</style>
<template>
  <div id="app">
    <LoginView v-if="!isLoggedIn" @login="handleLogin" />
    <ChatView v-else :currentUser="currentUser" @logout="handleLogout" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import LoginView from './views/LoginView.vue'
import ChatView from './views/ChatView.vue'

const isLoggedIn = ref(false)
const currentUser = ref('')

function handleLogin(username: string) {
  currentUser.value = username
  isLoggedIn.value = true
  console.log(`User logged in: ${username}`)
}

function handleLogout() {
  currentUser.value = ''
  isLoggedIn.value = false
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
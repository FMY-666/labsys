<script setup>
import { ref, computed } from 'vue'
import DeviceList from './components/DeviceList.vue'

const username = ref('')
const password = ref('')
const message = ref('')
const token = ref(localStorage.getItem('token') || '')
const currentUser = ref(localStorage.getItem('username') || '')
const currentRole = ref(localStorage.getItem('role') || '')

const isAdmin = computed(() => currentRole.value === 'ADMIN')
const avatarText = computed(() => (currentUser.value || '?').charAt(0).toUpperCase())

async function login() {
  message.value = ''

  try {
    const res = await fetch('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value })
    })

    if (!res.ok) {
      message.value = '服务器出错了（' + res.status + '）'
      return
    }

    const data = await res.json()

    if (data.code !== 200) {
      message.value = data.message
      return
    }

    token.value = data.data.token
    currentUser.value = data.data.username
    currentRole.value = data.data.role
    localStorage.setItem('token', token.value)
    localStorage.setItem('username', currentUser.value)
    localStorage.setItem('role', currentRole.value)
  } catch (e) {
    message.value = '连不上服务器，检查后端是否启动了'
  }
}

function logout() {
  token.value = ''
  currentUser.value = ''
  currentRole.value = ''
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('role')
}
</script>

<template>
  <div v-if="!token" class="login-wrap">
    <el-card class="login-card" shadow="always">
      <h1 class="title">实验室设备管理系统</h1>
      <p class="subtitle">labsys · 设备台账</p>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="username" placeholder="请输入用户名" size="large" @keyup.enter="login" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            @keyup.enter="login"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="submit" @click="login">登 录</el-button>
        <p class="msg">{{ message }}</p>
      </el-form>

      <div class="hint">
        <span>管理员：admin / 123456</span>
        <span>普通用户：student / 123456</span>
      </div>
    </el-card>
  </div>

  <div v-else class="layout">
    <header class="topbar">
      <div class="brand">实验室设备管理系统</div>

      <div class="right">
        <el-avatar :size="30" class="avatar">{{ avatarText }}</el-avatar>
        <div class="who">
          <span class="user">{{ currentUser || '已登录' }}</span>
          <el-tag size="small" :type="isAdmin ? 'danger' : 'info'" effect="light">
            {{ isAdmin ? '管理员' : '普通用户' }}
          </el-tag>
        </div>
        <el-button size="small" @click="logout">退出登录</el-button>
      </div>
    </header>

    <main class="content">
      <DeviceList />
    </main>
  </div>
</template>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.login-card {
  width: 380px;
  border-radius: 12px;
}

.title {
  margin: 8px 0 4px;
  font-size: 20px;
  font-weight: 500;
  text-align: center;
  color: #303133;
}

.subtitle {
  margin: 0 0 24px;
  font-size: 13px;
  text-align: center;
  color: #909399;
}

.submit {
  width: 100%;
  margin-top: 4px;
}

.msg {
  min-height: 20px;
  margin-top: 12px;
  font-size: 13px;
  text-align: center;
  color: #f56c6c;
}

.hint {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px dashed #ebeef5;
  font-size: 12px;
  color: #a8abb2;
}

.layout {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.topbar {
  flex: none;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.brand {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar {
  background: #409eff;
  font-size: 13px;
}

.who {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user {
  font-size: 13px;
  color: #606266;
}

.content {
  flex: 1;
  padding: 20px;
  overflow: auto;
}
</style>

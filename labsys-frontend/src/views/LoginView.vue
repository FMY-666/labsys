<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { setSession, clearSession } from '../stores/session'
import { bootstrapRoutes } from '../router'

const router = useRouter()

const username = ref('')
const password = ref('')
const message = ref('')
const loading = ref(false)

async function login() {
  message.value = ''
  loading.value = true

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

    setSession(data.data)
    // 登录接口只给了路径列表，路由要用菜单树来注册
    const ok = await bootstrapRoutes()
    if (!ok) {
      clearSession()
      message.value = '读取菜单失败，请重新登录'
      return
    }

    router.replace('/')
  } catch (e) {
    message.value = '连不上服务器，检查后端是否启动了'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
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
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="login">
          登 录
        </el-button>
        <p class="msg">{{ message }}</p>
      </el-form>

      <div class="hint">
        <span>管理员：admin / 123456</span>
        <span>实验员：keeper / 123456</span>
        <span>学生：student / 123456</span>
      </div>
    </el-card>
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
</style>

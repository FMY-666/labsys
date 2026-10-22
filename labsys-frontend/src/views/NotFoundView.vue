<script setup>
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

function goHome() {
  router.replace('/')
}
</script>

<template>
  <div class="nf">
    <h2 class="code">进不去</h2>
    <p class="text">当前账号没有「{{ route.fullPath }}」的访问权限，或者这个页面不存在。</p>
    <el-button type="primary" @click="goHome">回到首页</el-button>
  </div>
</template>

<style scoped>
.nf {
  height: 100%;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.code {
  margin: 0;
  font-size: 22px;
  font-weight: 500;
  color: #303133;
}

.text {
  margin: 0 0 8px;
  font-size: 13px;
  color: #909399;
}
</style>

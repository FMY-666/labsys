<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <el-card shadow="never">
    <el-empty :description="'「' + route.path + '」还没有实现'">
      <p class="tip">菜单和路由已经通了，页面内容等到后面部分再补。</p>
    </el-empty>
  </el-card>
</template>

<style scoped>
.tip {
  margin: 0;
  font-size: 13px;
  color: #909399;
}
</style>

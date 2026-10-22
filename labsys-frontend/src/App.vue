<script setup>
// 按需引入后不再有 app.use(ElementPlus, { locale })，中文语言包改由 el-config-provider 提供
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

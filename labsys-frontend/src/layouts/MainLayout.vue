<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session, clearSession } from '../stores/session'
import { resetRoutes } from '../router'

const route = useRoute()
const router = useRouter()

const isAdmin = computed(() => session.roles.includes('ADMIN'))
// 侧边栏直接读登录时拿到的菜单树，后端给什么就渲染什么
const menus = computed(() => session.menus)

const menuRef = ref(null)

/** 当前页面属于哪个分组，进来就把它展开 */
const openeds = computed(() => {
  const parent = menus.value.find((m) => (m.children || []).some((c) => c.path === route.path))
  return parent ? ['dir-' + parent.id] : []
})

// default-openeds 只在首次渲染生效，切换页面时要手动把新分组展开
watch(
  () => route.path,
  async () => {
    await nextTick()
    if (!menuRef.value) return
    // 点到一级单页（如设备台账）时没有所属分组，把之前展开的分组收起来，
    // 否则 unique-opened 的手风琴效果会残留一个张开的旧分组
    if (openeds.value.length === 0) {
      menus.value.forEach((m) => {
        if (m.children && m.children.length) menuRef.value.close('dir-' + m.id)
      })
      return
    }
    openeds.value.forEach((idx) => menuRef.value.open(idx))
  }
)

const roleLabel = computed(() => {
  if (session.roles.includes('ADMIN')) return '管理员'
  if (session.roles.includes('KEEPER')) return '实验员'
  return '学生'
})

const avatarText = computed(() => (session.username || '?').charAt(0).toUpperCase())

function logout() {
  clearSession()
  // 关键：先把动态路由摘掉，否则下一个登录的人会继承上一个账号的页面
  resetRoutes()
  router.replace('/login')
}
</script>

<template>
  <div class="layout">
    <header class="topbar">
      <div class="brand">实验室设备管理系统</div>

      <div class="right">
        <el-avatar :size="30" class="avatar">{{ avatarText }}</el-avatar>
        <div class="who">
          <span class="user">{{ session.username || '已登录' }}</span>
          <el-tag size="small" :type="isAdmin ? 'danger' : 'info'" effect="light">
            {{ roleLabel }}
          </el-tag>
        </div>
        <el-button size="small" @click="logout">退出登录</el-button>
      </div>
    </header>

    <div class="body">
      <aside class="sidebar">
        <el-menu
          ref="menuRef"
          :default-active="route.path"
          :default-openeds="openeds"
          unique-opened
          router
          class="menu"
        >
          <!-- 当前菜单只有两级；以后要支持更深层级得换成递归组件 -->
          <template v-for="item in menus" :key="item.id">
            <el-sub-menu v-if="item.children && item.children.length" :index="'dir-' + item.id">
              <template #title>{{ item.menuName }}</template>
              <el-menu-item v-for="child in item.children" :key="child.id" :index="child.path">
                {{ child.menuName }}
              </el-menu-item>
            </el-sub-menu>

            <el-menu-item v-else :index="item.path">
              {{ item.menuName }}
            </el-menu-item>
          </template>
        </el-menu>
      </aside>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
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

.body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.sidebar {
  flex: none;
  width: 200px;
  background: #fff;
  border-right: 1px solid #ebeef5;
  overflow: auto;
}

.menu {
  border-right: none;
}

.content {
  flex: 1;
  padding: 20px;
  overflow: auto;
}
</style>

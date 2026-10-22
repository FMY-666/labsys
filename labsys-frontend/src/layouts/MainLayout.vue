<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post, put } from '../api'
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

const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' })

function openPassword() {
  pwdForm.value = { oldPassword: '', newPassword: '', confirm: '' }
  pwdVisible.value = true
}

async function submitPassword() {
  const f = pwdForm.value
  if (!f.oldPassword) return ElMessage.warning('请填写原密码')
  if (f.newPassword.length < 6) return ElMessage.warning('新密码至少 6 位')
  if (f.newPassword !== f.confirm) return ElMessage.warning('两次输入的新密码不一致')

  pwdSaving.value = true
  const res = await put('/api/me/password', { oldPassword: f.oldPassword, newPassword: f.newPassword })
  pwdSaving.value = false

  if (res.code === 200) {
    pwdVisible.value = false
    // 后端会把所有令牌都清掉，所以这里直接回登录页，别让用户对着一个已经失效的会话发呆
    ElMessage.success('密码已修改，请重新登录')
    doLogout()
  } else {
    ElMessage.error(res.message || '修改失败')
  }
}

async function doLogout() {
  // 先让服务端把票删掉，再清本地。接口失败也照样退出，不能因为网络问题把人锁在页面里
  await post('/api/logout')
  clearSession()
  // 关键：先把动态路由摘掉，否则下一个登录的人会继承上一个账号的页面
  resetRoutes()
  router.replace('/login')
}

function logout() {
  doLogout()
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
        <el-button size="small" @click="openPassword">修改密码</el-button>
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

    <el-dialog v-model="pwdVisible" title="修改密码" width="440px" class="pwd-dialog">
      <!-- 标签给到 104px：「确认新密码」五个字 + 必填星号，88px 会折行 -->
      <el-form label-width="104px" @submit.prevent>
        <el-form-item label="原密码" required>
          <el-input
            v-model="pwdForm.oldPassword"
            type="password"
            show-password
            placeholder="请输入当前密码"
            @keyup.enter="submitPassword"
          />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            placeholder="至少 6 位"
            @keyup.enter="submitPassword"
          />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input
            v-model="pwdForm.confirm"
            type="password"
            show-password
            placeholder="再输一次"
            @keyup.enter="submitPassword"
          />
        </el-form-item>
      </el-form>
      <p class="pwd-tip">改完会退出登录，其他设备上的登录也会一起失效。</p>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="submitPassword">保存</el-button>
      </template>
    </el-dialog>
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

.pwd-tip {
  margin: 2px 0 0;
  padding: 9px 12px;
  border-radius: 8px;
  background: #f5f7fa;
  font-size: 12.5px;
  color: #5b6470;
  line-height: 1.7;
}
</style>

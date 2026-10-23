<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, post, put } from '../api'
import { session, clearSession, setAvatar } from '../stores/session'
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

const ROLE_NAMES = { ADMIN: '管理员', KEEPER: '实验员', STUDENT: '学生' }

function roleName(code) {
  return ROLE_NAMES[code] || code
}

const avatarText = computed(() => (session.username || '?').charAt(0).toUpperCase())

/** 有头像就显示图片，没有就让 el-avatar 退回到首字母插槽 */
const avatarSrc = computed(() => session.avatar || undefined)

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

// ---------------- 个人信息 ----------------
const profileVisible = ref(false)
const profile = ref({})

async function openProfile() {
  profileVisible.value = true
  profile.value = {}
  const res = await get('/api/me')
  if (res.code === 200) {
    profile.value = res.data || {}
  } else {
    ElMessage.error(res.message || '读取个人信息失败')
  }
}

// ---------------- 更换头像 ----------------
const AVATAR_SIZE = 160

const avatarVisible = ref(false)
const avatarSaving = ref(false)
const avatarPreview = ref('')
const uploadRef = ref(null)

function openAvatar() {
  avatarPreview.value = ''
  avatarVisible.value = true
}

/**
 * 裁正方形再缩放，而不是直接压成 160 宽：
 * 长图压成 160 宽会变成一条，人像直接糊掉。
 */
function toAvatar(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()

    img.onload = () => {
      // svg 之类可能没有固有尺寸，拿不到宽高就没法裁剪
      if (!img.naturalWidth || !img.naturalHeight) {
        URL.revokeObjectURL(url)
        reject(new Error('这张图读不出尺寸，换一张试试'))
        return
      }

      const side = Math.min(img.naturalWidth, img.naturalHeight)
      const sx = (img.naturalWidth - side) / 2
      const sy = (img.naturalHeight - side) / 2

      const canvas = document.createElement('canvas')
      canvas.width = AVATAR_SIZE
      canvas.height = AVATAR_SIZE
      const ctx = canvas.getContext('2d')
      // jpeg 没有透明通道，先铺白底，否则带透明的 png 会变成一片黑
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, AVATAR_SIZE, AVATAR_SIZE)
      ctx.drawImage(img, sx, sy, side, side, 0, 0, AVATAR_SIZE, AVATAR_SIZE)

      URL.revokeObjectURL(url)
      // 用 jpeg 不用 png：同样 160×160，jpeg 两三万字节，png 能到十几万
      resolve(canvas.toDataURL('image/jpeg', 0.86))
    }

    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('这张图读不出来，换一张试试'))
    }

    img.src = url
  })
}

async function onPickAvatar(uploadFile) {
  const raw = uploadFile && uploadFile.raw
  // 清掉 upload 自己的队列，否则同一个文件第二次选不会触发 change
  await nextTick()
  uploadRef.value?.clearFiles()
  if (!raw) return

  if (!raw.type.startsWith('image/')) return ElMessage.warning('只能选图片')
  if (raw.size > 5 * 1024 * 1024) return ElMessage.warning('图片别超过 5MB')

  try {
    avatarPreview.value = await toAvatar(raw)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function saveAvatar() {
  if (!avatarPreview.value) return ElMessage.warning('先选一张图片')

  avatarSaving.value = true
  const res = await put('/api/me/avatar', { avatar: avatarPreview.value })
  avatarSaving.value = false

  if (res.code === 200) {
    setAvatar(avatarPreview.value)
    avatarPreview.value = ''
    avatarVisible.value = false
    ElMessage.success('头像已更新')
  } else {
    ElMessage.error(res.message || '保存失败')
  }
}

async function resetAvatar() {
  avatarSaving.value = true
  const res = await put('/api/me/avatar', { avatar: '' })
  avatarSaving.value = false

  if (res.code === 200) {
    setAvatar('')
    avatarPreview.value = ''
    avatarVisible.value = false
    ElMessage.success('已恢复默认头像')
  } else {
    ElMessage.error(res.message || '操作失败')
  }
}

// ---------------- 改密码 / 退出 ----------------
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

function onUserCommand(cmd) {
  if (cmd === 'profile') return openProfile()
  if (cmd === 'password') return openPassword()
  if (cmd === 'avatar') return openAvatar()
  if (cmd === 'logout') return doLogout()
}
</script>

<template>
  <div class="layout">
    <header class="topbar">
      <div class="brand">实验室设备管理系统</div>

      <div class="right">
        <!-- 头像做成下拉，把「账号相关」的操作都收进一个入口，顶栏不再堆一排按钮 -->
        <el-dropdown trigger="click" placement="bottom-end" @command="onUserCommand">
          <div class="user-chip">
            <el-avatar :size="30" :src="avatarSrc" class="avatar">{{ avatarText }}</el-avatar>
            <div class="who">
              <span class="user">{{ session.username || '已登录' }}</span>
              <el-tag size="small" :type="isAdmin ? 'danger' : 'info'" effect="light">
                {{ roleLabel }}
              </el-tag>
            </div>
            <span class="caret"></span>
          </div>

          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人信息</el-dropdown-item>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="avatar">更换头像</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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

    <el-dialog v-model="profileVisible" title="个人信息" width="440px">
      <div class="profile-head">
        <el-avatar :size="56" :src="avatarSrc" class="avatar">{{ avatarText }}</el-avatar>
        <div class="profile-name">
          <div class="p-main">{{ profile.username || session.username }}</div>
          <div class="p-sub">{{ roleLabel }}</div>
        </div>
      </div>

      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="用户名">
          {{ profile.username || session.username || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="角色">
          {{ (profile.roles || session.roles || []).map(roleName).join(' / ') || roleLabel }}
        </el-descriptions-item>
        <el-descriptions-item label="用户 ID">{{ profile.id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ fmt(profile.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="profileVisible = false">关闭</el-button>
        <el-button type="primary" @click="profileVisible = false; openAvatar()">更换头像</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="avatarVisible" title="更换头像" width="420px">
      <div class="avatar-box">
        <el-avatar :size="96" :src="avatarPreview || avatarSrc" class="avatar avatar-big">
          {{ avatarText }}
        </el-avatar>

        <div class="avatar-ops">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            :on-change="onPickAvatar"
          >
            <el-button size="small">选择图片</el-button>
          </el-upload>
          <el-button size="small" :disabled="!avatarPreview" @click="avatarPreview = ''">撤销选择</el-button>
          <el-button size="small" type="danger" plain @click="resetAvatar">恢复默认</el-button>
        </div>
      </div>

      <p class="pwd-tip avatar-tip">选好会自动裁成正方形、压到 160×160，超过 5MB 的图请先自己裁一下。</p>

      <template #footer>
        <el-button @click="avatarVisible = false">取消</el-button>
        <el-button type="primary" :loading="avatarSaving" @click="saveAvatar">保存</el-button>
      </template>
    </el-dialog>

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

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 10px 4px 4px;
  border-radius: 18px;
  cursor: pointer;
  transition: background-color 0.2s;
  outline: none;
}

.user-chip:hover {
  background: #f5f7fa;
}

/* 用纯 CSS 三角形代替下拉箭头图标，省一个图标依赖 */
.caret {
  width: 0;
  height: 0;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 5px solid #a8abb2;
}

.avatar {
  background: #409eff;
  font-size: 13px;
  /* 头像是 img 渲染的，撑满并裁剪，避免非正方形图片被压扁 */
  flex: none;
}

.avatar-big {
  font-size: 32px;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.profile-name .p-main {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.profile-name .p-sub {
  margin-top: 2px;
  font-size: 12.5px;
  color: #909399;
}

.avatar-box {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar-ops {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

/* Element Plus 给相邻按钮加了 12px 左边距，竖排时会把按钮顶歪 */
.avatar-ops :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 两个类一起写才压得住下面的 .pwd-tip（单类选择器、写在后面） */
.pwd-tip.avatar-tip {
  margin-top: 16px;
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

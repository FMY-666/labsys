<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post, put } from '../api'
import { session } from '../stores/session'

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const loading = ref(false)

/** 角色下拉，两个弹窗共用 */
const roles = ref([])

const createVisible = ref(false)
const saving = ref(false)
const form = ref({ username: '', password: '', roleIds: [] })

const roleVisible = ref(false)
const roleTarget = ref(null)
const roleForm = ref([])

const pwdVisible = ref(false)
const pwdTarget = ref(null)
const pwdForm = ref('')

/** 当前登录的是谁，用来把“停用自己”这一行的按钮换掉（后端也有一道拦截） */
const myName = session.username

async function load() {
  loading.value = true
  const q = new URLSearchParams({ pageNum: pageNum.value, pageSize: pageSize.value })
  if (keyword.value.trim()) q.set('keyword', keyword.value.trim())
  const res = await get(`/api/users?${q}`)
  loading.value = false
  if (res.code === 200) {
    rows.value = res.data.list
    total.value = res.data.total
  } else {
    ElMessage.error(res.message || '加载失败')
  }
}

async function loadRoles() {
  const res = await get('/api/roles')
  if (res.code === 200) roles.value = res.data
}

function search() {
  pageNum.value = 1
  load()
}

function openCreate() {
  form.value = { username: '', password: '', roleIds: [] }
  createVisible.value = true
}

async function submitCreate() {
  if (!form.value.username.trim()) return ElMessage.warning('请填写用户名')
  if (form.value.password.length < 6) return ElMessage.warning('密码至少 6 位')
  if (!form.value.roleIds.length) return ElMessage.warning('请至少选一个角色')

  saving.value = true
  const res = await post('/api/users', {
    username: form.value.username.trim(),
    password: form.value.password,
    roleIds: form.value.roleIds
  })
  saving.value = false

  if (res.code === 200) {
    ElMessage.success('已创建')
    createVisible.value = false
    load()
  } else {
    ElMessage.error(res.message || '创建失败')
  }
}

function openRoles(row) {
  roleTarget.value = row
  roleForm.value = row.roles.map((r) => r.roleId)
  roleVisible.value = true
}

async function submitRoles() {
  if (!roleForm.value.length) return ElMessage.warning('请至少选一个角色')
  const res = await put(`/api/users/${roleTarget.value.id}/roles`, { roleIds: roleForm.value })
  if (res.code === 200) {
    ElMessage.success('角色已更新，对方重新登录后生效')
    roleVisible.value = false
    load()
  } else {
    ElMessage.error(res.message || '保存失败')
  }
}

function openPwd(row) {
  pwdTarget.value = row
  pwdForm.value = ''
  pwdVisible.value = true
}

async function submitPwd() {
  if (pwdForm.value.length < 6) return ElMessage.warning('密码至少 6 位')
  const res = await put(`/api/users/${pwdTarget.value.id}/password`, { password: pwdForm.value })
  if (res.code === 200) {
    ElMessage.success('密码已重置')
    pwdVisible.value = false
  } else {
    ElMessage.error(res.message || '重置失败')
  }
}

async function toggle(row) {
  const to = row.enabled ? 0 : 1
  if (to === 0) {
    try {
      await ElMessageBox.confirm(
        `停用后「${row.username}」立刻无法登录，已发出的令牌也会失效。确定吗？`,
        '停用账号',
        { type: 'warning', confirmButtonText: '停用' }
      )
    } catch (e) {
      return
    }
  }
  const res = await put(`/api/users/${row.id}/enabled?enabled=${to}`)
  if (res.code === 200) {
    ElMessage.success(to === 1 ? '已启用' : '已停用')
    load()
  } else {
    ElMessage.error(res.message || '操作失败')
  }
}

/** 解禁：违规清零、解除封禁，信用分回到 100 */
async function unban(row) {
  try {
    await ElMessageBox.confirm(
      `给「${row.username}」解禁？违规次数清零，信用分回到 100，可以重新预约。`,
      '解除封禁',
      { type: 'warning', confirmButtonText: '解禁' }
    )
  } catch (e) {
    return
  }
  const res = await post(`/api/credit/${row.id}/reset`)
  if (res.code === 200) {
    ElMessage.success('已解禁')
    load()
  } else {
    ElMessage.error(res.message || '操作失败')
  }
}

/** 90 分以上绿色，警告橙，冻结/封禁红 —— 和“我的预约”页的配色保持一致 */
function scoreClass(score) {
  if (score >= 100) return 's-ok'
  if (score >= 90) return 's-warn'
  return 's-bad'
}

function levelType(level) {
  if (level === 'WARN') return 'warning'
  if (level === 'FROZEN' || level === 'BANNED') return 'danger'
  return 'success'
}

/** 后端给的 levelText 带时间戳（「禁约中（到 10-23 22:51）」），列里放不下，压缩成四个字 */
function levelShort(level) {
  if (level === 'WARN') return '警告'
  if (level === 'FROZEN') return '禁约中'
  if (level === 'BANNED') return '封禁'
  return '正常'
}

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

onMounted(() => {
  loadRoles()
  load()
})
</script>

<template>
  <div>
    <div class="head">
      <h2>用户管理</h2>
      <div class="filters">
        <el-input
          v-model="keyword"
          placeholder="按用户名搜索"
          clearable
          style="width:200px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button @click="search">搜索</el-button>
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <!-- 列宽全部用 min-width，按内容长短给值：多出来的空间按比例分摊，
           「编号」不会白占 200px，「信用」这种两行内容也不会被挤到折行 -->
      <el-table-column prop="id" label="编号" min-width="56" />
      <el-table-column label="用户名" min-width="110">
        <template #default="{ row }">
          <span>{{ row.username }}</span>
          <el-tag v-if="!row.enabled" size="small" type="info" style="margin-left:6px">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="130">
        <template #default="{ row }">
          <el-tag
            v-for="r in row.roles"
            :key="r.roleId"
            size="small"
            :type="r.roleCode === 'ADMIN' ? 'danger' : (r.roleCode === 'KEEPER' ? 'warning' : 'info')"
            style="margin-right:4px"
          >{{ r.roleName }}</el-tag>
          <span v-if="!row.roles.length" style="color:#b7c0cc">未分配</span>
        </template>
      </el-table-column>
      <el-table-column label="信用" min-width="190">
        <template #default="{ row }">
          <span class="score" :class="scoreClass(row.credit.score)">{{ row.credit.score }}</span>
          <el-tooltip :disabled="!row.credit.blocked" :content="row.credit.levelText" placement="top">
            <el-tag size="small" :type="levelType(row.credit.level)" style="margin-left:6px">
              {{ levelShort(row.credit.level) }}
            </el-tag>
          </el-tooltip>
          <div class="sub">
            违规 {{ row.credit.violations }} 次 · 按时 {{ row.credit.onTimeStreak }}/3
            <span v-if="row.credit.level === 'FROZEN'"> · 解禁到 {{ fmt(row.credit.bannedUntil) }}</span>
            <span v-else-if="row.credit.maxHours < 14"> · 单次≤{{ row.credit.maxHours }}h</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="120">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openRoles(row)">分配角色</el-button>
          <el-button link type="primary" @click="openPwd(row)">重置密码</el-button>
          <el-button
            v-if="row.credit.violations > 0 || row.credit.permanentBan"
            link
            type="warning"
            @click="unban(row)"
          >解禁</el-button>
          <el-button
            v-if="row.username !== myName"
            link
            :type="row.enabled ? 'danger' : 'success'"
            @click="toggle(row)"
          >{{ row.enabled ? '停用' : '启用' }}</el-button>
          <span v-else style="color:#b7c0cc;font-size:12.5px">当前账号</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top:14px"
      @current-change="() => load()"
    />

    <el-dialog v-model="createVisible" title="新增用户" width="460px">
      <el-form label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="登录名，唯一" maxlength="50" />
        </el-form-item>
        <el-form-item label="初始密码" required>
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.roleIds" multiple placeholder="至少选一个" style="width:100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配角色" width="440px">
      <p class="hint">
        给「{{ roleTarget?.username }}」分配角色。多选时权限取并集，左侧菜单按并集渲染。
      </p>
      <el-select v-model="roleForm" multiple placeholder="至少选一个" style="width:100%">
        <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
      </el-select>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRoles">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="重置密码" width="420px">
      <p class="hint">给「{{ pwdTarget?.username }}」设置新密码，旧密码立即失效。</p>
      <el-input v-model="pwdForm" type="password" show-password placeholder="至少 6 位" />
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPwd">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.head h2 { margin: 0; font-size: 20px; }
.filters { display: flex; gap: 10px; }
.hint { margin: 0 0 12px; font-size: 13px; color: #5b6470; line-height: 1.6; }

.score { font-size: 16px; font-weight: 700; font-variant-numeric: tabular-nums; }
.s-ok { color: #0f8a5f; }
.s-warn { color: #a35c00; }
.s-bad { color: #c1352b; }
.sub { font-size: 12px; color: #8a939f; margin-top: 2px; }
</style>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post, put } from '../api'
import { session } from '../stores/session'

const RES_STATUS = {
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已通过', type: 'primary' },
  BORROWED: { label: '使用中', type: 'danger' },
  RETURNING: { label: '待确认归还', type: 'warning' },
  RETURNED: { label: '已归还', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'info' },
  CANCELLED: { label: '已取消', type: 'info' },
  OVERDUE: { label: '超时未还', type: 'danger' }
}

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const filterStatus = ref('')
const filterDevice = ref(null)

/** 有几条归还申请等着确认，显示在顶部提示条上 */
const returningCount = ref(0)

const devices = ref([])
const users = ref([])

const deviceName = computed(() => {
  const m = {}
  devices.value.forEach((d) => { m[d.id] = d.name })
  return m
})
const userName = computed(() => {
  const m = {}
  users.value.forEach((u) => { m[u.id] = u.username })
  return m
})

// 每秒刷新，让“剩余时间”真的在走
const now = ref(Date.now())
let timer = null
// 每 10 秒静默拉一次：学生在另一个窗口提交归还申请后，这边的提示条能自动冒出来
let refreshTimer = null

/** 违规名单：解禁是管理员专属操作 */
const isAdmin = computed(() => session.roles.includes('ADMIN'))
const creditVisible = ref(false)
const creditRows = ref([])
const creditLoading = ref(false)

async function openCredit() {
  creditVisible.value = true
  await loadCredit()
}

async function loadCredit() {
  creditLoading.value = true
  const res = await get('/api/credit/users')
  creditLoading.value = false
  if (res.code === 200) {
    creditRows.value = res.data
  } else {
    ElMessage.error(res.message || '加载违规名单失败')
  }
}

async function unban(row) {
  try {
    await ElMessageBox.confirm(
      `确定给「${row.username}」解禁吗？违规次数会清零，信用分回到 100。`,
      '解除封禁',
      { type: 'warning', confirmButtonText: '解禁' }
    )
  } catch (e) {
    return
  }
  const res = await post(`/api/credit/${row.userId}/reset`)
  if (res.code === 200) {
    ElMessage.success('已解禁')
    loadCredit()
  } else {
    ElMessage.error(res.message || '操作失败')
  }
}

async function loadRefs() {
  const [d, u] = await Promise.all([
    get('/api/devices?pageNum=1&pageSize=200'),
    get('/api/users/options')
  ])
  if (d.code === 200) devices.value = d.data.list
  if (u.code === 200) users.value = u.data
}

async function loadReturningCount() {
  const res = await get('/api/reservations/returning-count')
  if (res.code === 200) returningCount.value = res.data
}

async function load(silent = false) {
  if (!silent) loading.value = true
  let url = `/api/reservations?pageNum=${pageNum.value}&pageSize=${pageSize.value}`
  if (filterStatus.value) url += `&status=${filterStatus.value}`
  if (filterDevice.value) url += `&deviceId=${filterDevice.value}`

  const res = await get(url)
  if (!silent) loading.value = false
  if (res.code === 200) {
    rows.value = res.data.list
    total.value = res.data.total
  } else if (!silent) {
    ElMessage.error(res.message || '加载失败')
  }
  loadReturningCount()
}

function search() {
  pageNum.value = 1
  load()
}

/** 顶部提示条里点“只看这些” */
function showReturning() {
  filterStatus.value = 'RETURNING'
  search()
}

const ACTION_TEXT = {
  APPROVED: { text: '通过', type: 'success' },
  REJECTED: { text: '拒绝', type: 'danger' },
  BORROWED: { text: '借出', type: 'primary' },
  RETURNING: { text: '收回', type: 'primary' },
  RETURNED: { text: '归还', type: 'success' },
  CANCELLED: { text: '取消', type: 'info' }
}

async function doAction(row, to) {
  const cfg = ACTION_TEXT[to]
  const name = deviceName.value[row.deviceId] || row.deviceId

  // 审批前把借出理由摆在管理员眼前，比只看设备名更容易判断该不该批
  const reason = row.purpose
    ? `<div style="margin-top:10px;color:#5b6470;font-size:13px">借出理由：${esc(row.purpose)}</div>`
    : ''

  const tip = (row.status === 'RETURNING'
    ? `确认「${esc(name)}」已经收回了吗？确认后设备会变成在库，其他人可以继续预约。`
    : `确定把「${esc(name)}」这条预约${cfg.text}吗？`) + reason

  try {
    await ElMessageBox.confirm(tip, cfg.text + '预约', {
      type: 'warning',
      dangerouslyUseHTMLString: true
    })
  } catch (e) {
    return
  }
  const res = await put(`/api/reservations/${row.id}/status?to=${to}`)
  if (res.code === 200) {
    ElMessage.success('已' + cfg.text)
    load()
  } else {
    ElMessage.error(res.message || '操作失败')
  }
}

/** 理由与学生填写的内容会拼进确认框的 HTML 里，必须转义，否则有人填个 <img onerror> 就执行了 */
function esc(s) {
  return String(s).replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ))
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function humanDuration(ms) {
  const total = Math.max(0, Math.floor(ms / 1000))
  const d = Math.floor(total / 86400)
  const h = Math.floor((total % 86400) / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  if (d > 0) return `${d} 天 ${h} 小时`
  if (h > 0) return `${h} 小时 ${m} 分`
  if (m > 0) return `${m} 分 ${s} 秒`
  return `${s} 秒`
}

function countdown(row) {
  if (!row.startTime) return null
  const s = new Date(row.startTime).getTime()
  const e = new Date(row.endTime).getTime()
  const n = now.value

  if (row.status === 'APPROVED') {
    if (n < s) return { text: '距开始 ' + humanDuration(s - n), cls: 'wait' }
    if (n < e) return { text: '剩 ' + humanDuration(e - n), cls: 'live' }
    return { text: '时间已过', cls: 'done' }
  }
  if (row.status === 'BORROWED' || row.status === 'OVERDUE' || row.status === 'RETURNING') {
    if (n < e) return { text: '剩 ' + humanDuration(e - n), cls: 'live' }
    return { text: '已超时 ' + humanDuration(n - e), cls: 'done' }
  }
  return null
}

/** 「10月23日 14:00–17:00 · 3 小时」——开始时间由学生自选，必须把钟点写出来 */
function humanRange(row) {
  if (!row.startTime) return '-'
  const s = new Date(row.startTime)
  const e = new Date(row.endTime)
  const hm = (d) => `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  return `${s.getMonth() + 1}月${s.getDate()}日 ${hm(s)}–${hm(e)}`
}

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(5, 16) : '-'
}

onMounted(() => {
  loadRefs()
  load()
  timer = setInterval(() => { now.value = Date.now() }, 1000)
  refreshTimer = setInterval(() => load(true), 10000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<template>
  <div>
    <div class="head">
      <div class="title-wrap">
        <h2>预约管理</h2>
        <router-link to="/reservation-archive" class="archive-link">已完成的预约？去归档看</router-link>
      </div>
      <div class="filters">
        <el-select v-model="filterStatus" placeholder="全部进行中" clearable style="width:130px" @change="search">
          <el-option label="待审批" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="使用中" value="BORROWED" />
          <el-option label="待确认归还" value="RETURNING" />
          <el-option label="超时未还" value="OVERDUE" />
        </el-select>
        <el-select v-model="filterDevice" placeholder="全部设备" clearable style="width:160px" @change="search">
          <el-option v-for="d in devices" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-button @click="search">刷新</el-button>
        <el-button @click="openCredit">违规记录</el-button>
      </div>
    </div>

    <el-alert
      v-if="returningCount > 0"
      type="warning"
      :closable="false"
      show-icon
      class="alert"
    >
      <template #title>
        有 <b>{{ returningCount }}</b> 条归还申请等待确认
        <el-button link type="primary" style="margin-left:10px" @click="showReturning">只看这些</el-button>
      </template>
    </el-alert>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="编号" min-width="56" />
      <el-table-column label="设备" min-width="116">
        <template #default="{ row }">{{ deviceName[row.deviceId] || '设备 ' + row.deviceId }}</template>
      </el-table-column>
      <el-table-column label="预约人" min-width="76">
        <template #default="{ row }">{{ userName[row.userId] || '#' + row.userId }}</template>
      </el-table-column>
      <el-table-column label="借出理由" min-width="96" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.purpose">{{ row.purpose }}</span>
          <span v-else style="color:#b7c0cc">未填写</span>
        </template>
      </el-table-column>
      <el-table-column label="使用时间" min-width="166">
        <template #default="{ row }">{{ humanRange(row) }}</template>
      </el-table-column>
      <el-table-column label="倒计时" min-width="120">
        <template #default="{ row }">
          <span v-if="countdown(row)" :class="'cd ' + countdown(row).cls">{{ countdown(row).text }}</span>
          <span v-else style="color:#b7c0cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="归还时间" min-width="116">
        <template #default="{ row }">
          <span v-if="row.status === 'RETURNING'" style="color:#a35c00">
            申请 {{ fmt(row.returnRequestedAt) }}
          </span>
          <span v-else-if="row.status === 'RETURNED'" style="color:#0f8a5f">
            已还 {{ fmt(row.returnedAt) }}
          </span>
          <span v-else style="color:#b7c0cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="108">
        <template #default="{ row }">
          <el-tag :type="RES_STATUS[row.status]?.type" size="small">
            {{ RES_STATUS[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="130" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button link type="success" @click="doAction(row, 'APPROVED')">通过</el-button>
            <el-button link type="danger" @click="doAction(row, 'REJECTED')">拒绝</el-button>
          </template>
          <template v-else-if="row.status === 'APPROVED'">
            <el-button link type="primary" @click="doAction(row, 'BORROWED')">借出</el-button>
            <el-button link type="info" @click="doAction(row, 'CANCELLED')">取消</el-button>
          </template>
          <template v-else-if="row.status === 'RETURNING'">
            <el-button link type="success" @click="doAction(row, 'RETURNED')">确认归还</el-button>
          </template>
          <template v-else-if="row.status === 'BORROWED' || row.status === 'OVERDUE'">
            <el-button link type="success" @click="doAction(row, 'RETURNED')">收回</el-button>
          </template>
          <span v-else style="color:#b7c0cc">—</span>
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

    <el-dialog v-model="creditVisible" title="预约违规记录" width="780px">
      <el-table :data="creditRows" v-loading="creditLoading" border size="small"
                empty-text="暂无违规记录">
        <el-table-column prop="username" label="用户" min-width="96" />
        <el-table-column label="信用分" min-width="78">
          <template #default="{ row }">
            <span :style="{ color: row.score >= 90 ? '#0f8a5f' : '#c1352b', fontWeight: 500 }">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column label="违规" min-width="68">
          <template #default="{ row }">{{ row.violations }} 次</template>
        </el-table-column>
        <el-table-column label="按时归还进度" min-width="106">
          <template #default="{ row }">{{ row.onTimeStreak }} / 3</template>
        </el-table-column>
        <el-table-column label="状态" min-width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="row.level === 'NORMAL' ? 'success' : (row.level === 'WARN' ? 'warning' : 'danger')">
              {{ row.levelText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可约时长" min-width="80">
          <template #default="{ row }">{{ row.maxHours }} 小时</template>
        </el-table-column>
        <el-table-column label="操作" min-width="80">
          <template #default="{ row }">
            <el-button v-if="isAdmin" link type="primary" @click="unban(row)">解禁</el-button>
            <span v-else style="color:#b7c0cc;font-size:12.5px">仅管理员</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="credit-tip">
        规则：超时 30 分钟未还记 1 次违规 —— 1 次警告；2、3 次禁约 24 小时；4 次封禁，需管理员解禁。
        按时归还每满 3 次抵掉 1 次违规。违规满 2 次起，单次最长预约降为 3 小时。
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.head h2 { margin: 0; font-size: 20px; }
.title-wrap { display: flex; align-items: baseline; gap: 14px; flex-wrap: wrap; }
.archive-link { font-size: 13px; color: #2f6feb; text-decoration: none; }
.archive-link:hover { text-decoration: underline; }
.filters { display: flex; gap: 10px; }
.alert { margin-bottom: 12px; }

.cd { font-variant-numeric: tabular-nums; font-size: 13px; }
.cd.wait { color: #5b6470; }
.cd.live { color: #c1352b; font-weight: 700; }
.cd.done { color: #a35c00; }

.credit-tip {
  margin-top: 12px; padding: 9px 12px; border-radius: 8px;
  background: #f5f7fa; font-size: 12.5px; color: #5b6470; line-height: 1.7;
}
</style>

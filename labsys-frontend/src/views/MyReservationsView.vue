<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post, put } from '../api'

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

/** 实验室开放时段：最早 08:00 开始，最晚 22:00 结束。
 *  两个边界一起决定“能填多长”，所以不写死最大时长，按开始时间算。 */
const OPEN_FROM = 8
const OPEN_TO = 22

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

/** active = 进行中；archived = 已归档（已归还/已取消/已拒绝） */
const tab = ref('active')
const archiveStatus = ref('')

const devices = ref([])
const deviceName = computed(() => {
  const m = {}
  devices.value.forEach((d) => { m[d.id] = d.name })
  return m
})

/** 预约信用：分数、违规次数、是否被禁约、还能约多久，全由后端算 */
const credit = ref(null)
const creditTag = computed(() => {
  const l = credit.value?.level
  if (l === 'WARN') return 'warning'
  if (l === 'FROZEN' || l === 'BANNED') return 'danger'
  return 'success'
})

/** 违规明细：只给汇总数字的话，学生不知道自己哪次超时导致扣分 */
const violationVisible = ref(false)
const violationLoading = ref(false)
const violationRows = ref([])

/** 续借：把自己的预约往后延几小时 */
const extendVisible = ref(false)
const extendTarget = ref(null)
const extendHours = ref(1)
const extending = ref(false)

function openExtend(row) {
  extendTarget.value = row
  extendHours.value = 1
  extendVisible.value = true
}

async function submitExtend() {
  extending.value = true
  const res = await put(`/api/reservations/${extendTarget.value.id}/extend?hours=${extendHours.value}`)
  extending.value = false
  if (res.code === 200) {
    ElMessage.success(res.message || '已延长')
    extendVisible.value = false
    load()
  } else {
    // 409 是「后面那位已经约了」，和学生自身操作无关，用 warning 区分开
    ElMessage({ type: res.code === 409 ? 'warning' : 'error', message: res.message || '续借失败', duration: 4000 })
  }
}

async function openViolations() {
  violationVisible.value = true
  violationLoading.value = true
  const res = await get('/api/credit/my-violations')
  violationLoading.value = false
  if (res.code === 200) {
    violationRows.value = res.data
  } else {
    ElMessage.error(res.message || '加载违规明细失败')
  }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const form = ref(emptyForm())

// 每秒刷新一次，“倒计时”才是活的
const now = ref(Date.now())
let timer = null
// 每 10 秒静默拉一次列表：管理员在另一个窗口审批/确认归还后，这边能自动看到结果，不用手动 F5
let refreshTimer = null

function emptyForm() {
  return { deviceId: null, date: '', startTime: '08:00', hours: 2, purpose: '' }
}

function pad(n) {
  return String(n).padStart(2, '0')
}

/** 后端给的单次时长上限：有违规会从 14 小时降到 3 小时。拿不到就先按满额算 */
const creditLimit = computed(() => credit.value?.maxHours ?? (OPEN_TO - OPEN_FROM))

/** 两个上限取小的：一个来自“现在几点到 22:00 还剩多久”，一个来自信用分 */
const maxHours = computed(() => {
  const [h, m] = (form.value.startTime || '08:00').split(':').map(Number)
  const byClock = Math.floor((OPEN_TO * 60 - (h * 60 + m)) / 60)
  return Math.max(1, Math.min(byClock, creditLimit.value))
})

/** 结束时刻。只用小时数算，不 new Date，避免跨天时被 Date 静默滚到第二天 */
const endText = computed(() => {
  const [h, m] = (form.value.startTime || '08:00').split(':').map(Number)
  const total = h * 60 + m + form.value.hours * 60
  return `${pad(Math.floor(total / 60))}:${pad(total % 60)}`
})

function todayStr() {
  const d = new Date(now.value)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 严格晚于此刻的第一个半点，单位是分钟。
 * 当前 17:27 -> 17:30；正好 17:30:00 -> 18:00。
 * 不取当前时刻本身：下拉只有半小时刻度，落在刻度之间的时间选不出来；
 * 而取已经过去的那个刻度，提交又会被后端按“不能预约过去的时间”打回。
 * 依赖 now 而不是 new Date()，否则 computed 会缓存到页面刷新为止。
 */
const nextSlot = computed(() => {
  const d = new Date(now.value)
  const minutes = d.getHours() * 60 + d.getMinutes()
  const rounded = Math.ceil(minutes / 30) * 30
  return rounded === minutes ? rounded + 30 : rounded
})

/**
 * 今天可选的最早钟点，夹在开放时段内。
 * 上限必须夹到 21:00：21:30 虽然还在“现在之后”，但再加最少的 1 小时就跨过 22:00 了。
 */
const earliestToday = computed(() => {
  const r = Math.min(Math.max(nextSlot.value, OPEN_FROM * 60), (OPEN_TO - 1) * 60)
  return `${pad(Math.floor(r / 60))}:${pad(r % 60)}`
})

/** 今天已经排不下任何时段（过了 21:00），日期选择器要连今天一起禁掉 */
const noSlotToday = computed(() => nextSlot.value > (OPEN_TO - 1) * 60)

/** 开始时间的可选下限：当天从“现在之后”算起，其他日期从 08:00 算起 */
const minStart = computed(() => (form.value.date === todayStr() ? earliestToday.value : '08:00'))

/** 选日期时同步一个合理的默认开始时间，省得用户每次都手动改 */
watch(() => form.value.date, (d) => {
  if (!d) return
  form.value.startTime = d === todayStr() ? earliestToday.value : '08:00'
})

/**
 * 弹窗开着不动，时间也在走：选了今天的 17:30，过了 17:31 这个点就作废了。
 * 与其等提交时被打回，不如直接把选择推到当前可用的最早刻度。
 */
watch(minStart, (v) => {
  if (!dialogVisible.value) return
  if (form.value.startTime && form.value.startTime < v) form.value.startTime = v
})

watch(() => form.value.startTime, () => {
  if (form.value.hours > maxHours.value) form.value.hours = maxHours.value
})

function disabledDate(d) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  if (d.getTime() < today.getTime()) return true
  // 今天已经排不下任何时段，把今天也禁掉，免得点进去发现没得选
  const isToday = d.toDateString() === new Date(now.value).toDateString()
  return isToday && noSlotToday.value
}

async function loadDevices() {
  const res = await get('/api/devices?pageNum=1&pageSize=200')
  if (res.code === 200) devices.value = res.data.list
}

async function loadCredit() {
  const res = await get('/api/credit/me')
  if (res.code === 200) credit.value = res.data
}

async function load(silent = false) {
  if (!silent) loading.value = true

  const q = new URLSearchParams({ pageNum: pageNum.value, pageSize: pageSize.value })
  // 不传 status 时后端按 archived 决定要不要终态，口径与管理员的预约管理一致
  if (tab.value === 'archived') {
    q.set('archived', 'true')
    if (archiveStatus.value) q.set('status', archiveStatus.value)
  }

  const res = await get(`/api/reservations/mine?${q}`)
  if (!silent) loading.value = false
  if (res.code === 200) {
    rows.value = res.data.list
    total.value = res.data.total
  } else if (!silent) {
    ElMessage.error(res.message || '加载失败')
  }
}

/** 切标签/切筛选都要回到第一页，否则会停在一个不存在的页码上 */
function reload() {
  pageNum.value = 1
  load()
}

function openCreate() {
  form.value = emptyForm()
  dialogVisible.value = true
}

async function submit() {
  if (!form.value.deviceId) return ElMessage.warning('请选择设备')
  if (!form.value.date) return ElMessage.warning('请选择日期')
  if (!form.value.startTime) return ElMessage.warning('请选择开始时间')
  if (!form.value.hours || form.value.hours < 1) return ElMessage.warning('请填写使用时长')
  if (form.value.hours > maxHours.value) {
    return ElMessage.warning(`从 ${form.value.startTime} 开始最多用 ${maxHours.value} 小时（${OPEN_TO}:00 前要结束）`)
  }
  // 必填：管理员审批时要看理由判断该不该批
  if (!form.value.purpose || !form.value.purpose.trim()) return ElMessage.warning('请填写借出理由')

  const start = new Date(`${form.value.date}T${form.value.startTime}:00`)
  const end = new Date(start.getTime() + form.value.hours * 3600 * 1000)

  // 日期选择器只挡了“今天之前”，今天已经过去的钟点还得单独拦一次，
  // 否则要等后端返回 400 用户才知道哪里错了
  if (start.getTime() < Date.now()) {
    return ElMessage.warning('这个时间已经过去了，请换一个日期或钟点')
  }

  submitting.value = true
  const res = await post('/api/reservations', {
    deviceId: form.value.deviceId,
    startTime: toIso(start),
    endTime: toIso(end),
    purpose: form.value.purpose
  })
  submitting.value = false

  if (res.code === 200) {
    ElMessage.success('已提交，等待审批')
    dialogVisible.value = false
    // 在归档标签下提交会“看不到自己刚提的单”，所以固定跳回进行中
    tab.value = 'active'
    archiveStatus.value = ''
    reload()
    loadCredit()
  } else {
    ElMessage({ type: res.code === 409 ? 'warning' : 'error', message: res.message || '提交失败', duration: 4000 })
  }
}

function toIso(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm(`确定取消「${deviceName.value[row.deviceId] || row.deviceId}」这次预约吗？`, '取消预约', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  const res = await put(`/api/reservations/${row.id}/cancel`)
  if (res.code === 200) {
    ElMessage.success('已取消，可在「已归档」里查看')
    load()
  } else {
    ElMessage.error(res.message || '取消失败')
  }
}

/** 申请归还：提交后状态变“待确认归还”，管理员确认后设备才真正释放 */
async function doReturn(row) {
  try {
    await ElMessageBox.confirm(
      `确定归还「${deviceName.value[row.deviceId] || row.deviceId}」吗？` +
      '提交后需要管理员确认，管理员确认后设备才会重新变成可预约状态。',
      '申请归还',
      { type: 'warning', confirmButtonText: '提交申请' }
    )
  } catch (e) {
    return
  }
  const res = await put(`/api/reservations/${row.id}/return`)
  if (res.code === 200) {
    ElMessage.success('已提交归还申请，等管理员确认')
    load()
  } else {
    ElMessage.error(res.message || '提交失败')
  }
}

/** 把毫秒差写成「2 天 3 小时」这种 */
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

/**
 * 倒计时。三种情况：
 *   还没到开始时间 -> 距开始多久
 *   正在时段内     -> 还剩多久（红色，最需要被看见）
 *   已经过了结束   -> 已结束
 * 只有“已通过 / 使用中”才显示，待审批的还没定下来，倒计时没有意义。
 */
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

/** 「10月23日 14:00–17:00 · 3 小时」——开始时间不再固定 08:00，所以必须把钟点写出来 */
function humanRange(row) {
  if (!row.startTime) return '-'
  const s = new Date(row.startTime)
  const e = new Date(row.endTime)
  const hm = (d) => `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return `${s.getMonth() + 1}月${s.getDate()}日 ${hm(s)}–${hm(e)}`
}

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

onMounted(() => {
  loadDevices()
  loadCredit()
  load()
  timer = setInterval(() => { now.value = Date.now() }, 1000)
  refreshTimer = setInterval(() => { load(true); loadCredit() }, 10000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<template>
  <div>
    <div class="head">
      <h2>我的预约</h2>
      <el-tooltip :disabled="!credit?.blocked" :content="credit?.blockReason || ''" placement="bottom">
        <span>
          <el-button type="primary" :disabled="credit?.blocked" @click="openCreate">预约设备</el-button>
        </span>
      </el-tooltip>
    </div>

    <div class="credit-bar">
      <div class="credit-score">
        <b :class="'sc-' + (credit?.level || 'NORMAL')">{{ credit?.score ?? 100 }}</b>
        <span>信用分</span>
      </div>
      <el-tag size="small" :type="creditTag">{{ credit?.levelText || '信用正常' }}</el-tag>
      <span class="sep">
        违规 <b>{{ credit?.violations ?? 0 }}</b> 次
        <el-button v-if="credit?.violations" link type="primary" size="small"
                   style="margin-left:2px" @click="openViolations">明细</el-button>
      </span>
      <span class="sep">
        按时归还 <b>{{ credit?.onTimeStreak ?? 0 }}</b> / 3
        <em v-if="credit?.violations">（再 {{ credit?.offsetRemain }} 次抵 1 次）</em>
      </span>
      <span class="sep">单次最长 <b>{{ credit?.maxHours ?? 14 }}</b> 小时</span>
    </div>

    <div v-if="credit?.blocked" class="blocked-tip">{{ credit.blockReason }}</div>

    <div class="toolbar">
      <el-tabs v-model="tab" class="tabs" @tab-change="reload">
        <el-tab-pane label="进行中" name="active" />
        <el-tab-pane label="已归档" name="archived" />
      </el-tabs>

      <el-select
        v-if="tab === 'archived'"
        v-model="archiveStatus"
        placeholder="全部终态"
        clearable
        style="width:150px; margin-bottom:8px"
        @change="reload"
      >
        <el-option label="已归还" value="RETURNED" />
        <el-option label="已取消" value="CANCELLED" />
        <el-option label="已拒绝" value="REJECTED" />
      </el-select>
    </div>

    <div v-if="tab === 'archived'" class="archived-tip">
      已归还、已取消、已拒绝的预约不再出现在「进行中」，在这里查历史记录。
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="编号" min-width="60" />
      <el-table-column label="设备" min-width="112">
        <template #default="{ row }">{{ deviceName[row.deviceId] || '设备 ' + row.deviceId }}</template>
      </el-table-column>
      <el-table-column label="使用时间" min-width="170">
        <template #default="{ row }">{{ humanRange(row) }}</template>
      </el-table-column>
      <el-table-column label="倒计时" min-width="134">
        <template #default="{ row }">
          <span v-if="countdown(row)" :class="'cd ' + countdown(row).cls">{{ countdown(row).text }}</span>
          <span v-else style="color:#b7c0cc">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="purpose" label="借出理由" min-width="100" show-overflow-tooltip />
      <el-table-column label="状态" min-width="104">
        <template #default="{ row }">
          <el-tag :type="RES_STATUS[row.status]?.type" size="small">
            {{ RES_STATUS[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="归还" min-width="116">
        <template #default="{ row }">
          <span v-if="row.status === 'RETURNING'" style="color:#a35c00">
            已申请 {{ fmt(row.returnRequestedAt) }}
          </span>
          <span v-else-if="row.status === 'RETURNED' && row.returnedAt" style="color:#0f8a5f">
            已还 {{ fmt(row.returnedAt) }}
          </span>
          <span v-else style="color:#b7c0cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link type="danger" @click="cancel(row)">取消</el-button>
          <template v-else-if="row.status === 'APPROVED'">
            <el-button link type="primary" @click="openExtend(row)">续借</el-button>
          </template>
          <template v-else-if="row.status === 'BORROWED'">
            <el-button link type="primary" @click="openExtend(row)">续借</el-button>
            <el-button link type="success" @click="doReturn(row)">申请归还</el-button>
          </template>
          <el-button v-else-if="row.status === 'OVERDUE'" link type="success" @click="doReturn(row)">申请归还</el-button>
          <span v-else-if="row.status === 'RETURNING'" style="color:#a35c00;font-size:12.5px">等确认</span>
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

    <el-dialog v-model="dialogVisible" title="预约设备" width="500px">
      <el-form label-width="88px">
        <el-form-item label="设备">
          <el-select v-model="form.deviceId" placeholder="选择要用的设备" style="width:100%">
            <el-option
              v-for="d in devices"
              :key="d.id"
              :label="`${d.deviceNo} ${d.name}`"
              :value="d.id"
              :disabled="d.status === 'REPAIRING' || d.status === 'SCRAPPED'"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="日期">
          <el-date-picker
            v-model="form.date"
            type="date"
            placeholder="选哪一天"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledDate"
            style="width:100%"
          />
        </el-form-item>

        <el-form-item label="开始时间">
          <el-time-select
            v-model="form.startTime"
            :start="`${pad(OPEN_FROM)}:00`"
            step="00:30"
            :end="`${pad(OPEN_TO - 1)}:00`"
            :min-time="minStart"
            placeholder="几点开始"
            style="width:150px"
          />
          <span class="hint">半小时一档，{{ minStart }} ~ {{ pad(OPEN_TO - 1) }}:00</span>
        </el-form-item>

        <el-form-item label="用多久">
          <el-input-number
            v-model="form.hours"
            :min="1"
            :max="maxHours"
            :step="1"
            :precision="0"
            controls-position="right"
            style="width:150px"
          />
          <span class="hint">小时（最多 {{ maxHours }}）</span>
        </el-form-item>

        <el-form-item label="借出理由" required>
          <el-input v-model="form.purpose" type="textarea" :rows="3" maxlength="255" show-word-limit
                    placeholder="写给管理员看，比如「网络实验课，测三层交换配置」" />
        </el-form-item>
      </el-form>

      <div class="preview" v-if="form.date && form.startTime">
        这次预约：<b>{{ form.date }}</b> 从 <b>{{ form.startTime }}</b> 开始，用 <b>{{ form.hours }} 小时</b>，
        到 <b>{{ endText }}</b> 结束。
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="extendVisible" title="续借" width="420px">
      <p class="violation-tip">
        当前：<b>{{ humanRange(extendTarget || {}) }}</b><br>
        续借不能跨天，最晚到 22:00，且后面的时段不能已经被人约走。
      </p>
      <el-form label-width="80px">
        <el-form-item label="延长">
          <el-input-number v-model="extendHours" :min="1" :max="8" :step="1" :precision="0"
                           controls-position="right" style="width:130px" />
          <span class="hint">小时</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="extendVisible = false">取消</el-button>
        <el-button type="primary" :loading="extending" @click="submitExtend">确认续借</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="violationVisible" title="违规明细" width="620px">
      <p class="violation-tip">
        下面这些预约超过结束时间 30 分钟仍未归还，各记 1 次违规。
        按时归还满 3 次可以抵掉 1 次。
      </p>
      <el-table :data="violationRows" v-loading="violationLoading" size="small" border
                empty-text="没有违规记录">
        <el-table-column label="设备" min-width="130">
          <template #default="{ row }">{{ deviceName[row.deviceId] || '设备 ' + row.deviceId }}</template>
        </el-table-column>
        <el-table-column label="使用时间" min-width="170">
          <template #default="{ row }">{{ humanRange(row) }}</template>
        </el-table-column>
        <el-table-column label="归还时间" min-width="120">
          <template #default="{ row }">{{ row.returnedAt ? fmt(row.returnedAt) : '未归还' }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="96">
          <template #default="{ row }">
            <el-tag size="small" :type="RES_STATUS[row.status]?.type">
              {{ RES_STATUS[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.head h2 { margin: 0; font-size: 20px; }

.toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.tabs { flex: 1; }
.tabs :deep(.el-tabs__header) { margin-bottom: 10px; }
.archived-tip {
  background: #f2f4f7; border-radius: 8px; padding: 8px 12px;
  font-size: 12.5px; color: #5b6470; margin-bottom: 12px;
}

.preview { background: #eaf1fe; border-radius: 8px; padding: 10px 14px; font-size: 13.5px; color: #2b3340; }
.hint { margin-left: 8px; font-size: 12.5px; color: #8a939f; }

.credit-bar {
  display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  background: #fff; border: 1px solid #e3e7ec; border-radius: 11px;
  padding: 10px 16px; margin-bottom: 12px; font-size: 13px; color: #5b6470;
}
.credit-score { display: flex; align-items: baseline; gap: 6px; }
.credit-score b { font-size: 24px; font-weight: 700; font-variant-numeric: tabular-nums; }
.credit-score span { font-size: 12px; color: #8a939f; }
.sc-NORMAL { color: #0f8a5f; }
.sc-WARN { color: #a35c00; }
.sc-FROZEN { color: #c1352b; }
.sc-BANNED { color: #c1352b; }
.credit-bar .sep b { color: #2b3340; }
.credit-bar em { font-style: normal; color: #8a939f; }

.blocked-tip {
  background: #fdecec; border: 1px solid #f5c2c2; color: #a32d2d;
  border-radius: 8px; padding: 9px 14px; font-size: 13px; margin-bottom: 12px;
}

.violation-tip { margin: 0 0 12px; font-size: 12.5px; color: #5b6470; line-height: 1.7; }

.cd { font-variant-numeric: tabular-nums; font-size: 13px; }
.cd.wait { color: #5b6470; }
.cd.live { color: #c1352b; font-weight: 700; }
.cd.done { color: #a35c00; }
</style>

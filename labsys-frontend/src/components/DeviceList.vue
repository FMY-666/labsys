<script setup>
import { computed, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get } from '../api'
import { session } from '../stores/session'

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const keyword = ref('')
const status = ref('')

// 改设备只有管理员能做；看借用/报修历史实验员也要看，所以分成两个判断
const isAdmin = computed(() => session.roles.includes('ADMIN'))
const isStaff = computed(() => session.roles.includes('ADMIN') || session.roles.includes('KEEPER'))

const statusOptions = [
  { value: 'AVAILABLE', label: '可借用' },
  { value: 'BORROWED', label: '已借出' },
  { value: 'REPAIRING', label: '维修中' },
  { value: 'SCRAPPED', label: '已报废' }
]

const statusMap = {
  AVAILABLE: { text: '可借用', type: 'success' },
  BORROWED: { text: '已借出', type: 'warning' },
  REPAIRING: { text: '维修中', type: 'danger' },
  SCRAPPED: { text: '已报废', type: 'info' }
}

function statusOf(s) {
  return statusMap[s] || { text: s, type: 'info' }
}

async function load() {
  const token = sessionStorage.getItem('token')
  const params = new URLSearchParams()
  params.append('pageNum', pageNum.value)
  params.append('pageSize', pageSize.value)
  if (keyword.value) params.append('keyword', keyword.value)
  if (status.value) params.append('status', status.value)

  const res = await fetch('/api/devices?' + params.toString(), {
    headers: { Authorization: 'Bearer ' + token }
  })
  const data = await res.json()

  if (data.code === 401) {
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('username')
    location.reload()
    return
  }

  list.value = data.data.list
  total.value = data.data.total
}

function search() {
  pageNum.value = 1
  load()
}

function reset() {
  keyword.value = ''
  status.value = ''
  pageNum.value = 1
  load()
}

function handlePageChange(p) {
  pageNum.value = p
  load()
}

function handleSizeChange(s) {
  pageSize.value = s
  pageNum.value = 1
  load()
}

const dialogVisible = ref(false)
const editingId = ref(null)
const form = ref({})

// ---- 设备历史：这台机器被谁借过、修过几次 ----
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyTab = ref('reservation')
const historyDevice = ref(null)
const historyStats = ref({ borrowCount: 0, repairCount: 0 })
const historyRes = ref([])
const historyTickets = ref([])
const userNames = ref({})

const RES_STATUS = {
  PENDING: '待审批', APPROVED: '已通过', BORROWED: '使用中', RETURNING: '待确认归还',
  RETURNED: '已归还', REJECTED: '已拒绝', CANCELLED: '已取消', OVERDUE: '超时未还'
}
const TICKET_STATUS = {
  PENDING: '待受理', ACCEPTED: '处理中', RESOLVED: '已解决', REJECTED: '已驳回'
}

async function openHistory(row) {
  historyDevice.value = row
  historyTab.value = 'reservation'
  historyVisible.value = true
  historyLoading.value = true

  // 借用记录里只有 userId，人名单独取一次下拉接口（实验员/管理员才有权限）
  if (!Object.keys(userNames.value).length) {
    const u = await get('/api/users/options')
    if (u.code === 200) {
      const m = {}
      u.data.forEach((x) => { m[x.id] = x.username })
      userNames.value = m
    }
  }

  const res = await get(`/api/devices/${row.id}/history`)
  historyLoading.value = false
  if (res.code === 200) {
    historyStats.value = res.data.stats
    historyRes.value = res.data.reservations
    historyTickets.value = res.data.tickets
  } else {
    ElMessage.error(res.message || '加载历史失败')
  }
}

function hm(d) {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function range(r) {
  if (!r.startTime) return '-'
  const s = new Date(r.startTime)
  const e = new Date(r.endTime)
  return `${s.getMonth() + 1}月${s.getDate()}日 ${hm(s)}–${hm(e)}`
}

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(5, 16) : '-'
}

// ---- 分类 / 位置字典 ----
const categories = ref([])
const locations = ref([])

async function loadDicts() {
  const [c, l] = await Promise.all([
    get('/api/device-dict?type=CATEGORY'),
    get('/api/device-dict?type=LOCATION')
  ])
  if (c.code === 200) categories.value = c.data.map((x) => x.value)
  if (l.code === 200) locations.value = l.data.map((x) => x.value)
}

// ---- Excel 导入导出 ----
const exporting = ref(false)
const importing = ref(false)
const fileInput = ref(null)
const importVisible = ref(false)
const importResult = ref({ inserted: 0, failed: [] })

async function exportExcel() {
  exporting.value = true
  try {
    const res = await fetch('/api/devices/export', {
      headers: { Authorization: 'Bearer ' + session.token }
    })
    if (!res.ok) {
      ElMessage.error('导出失败（HTTP ' + res.status + '）')
      return
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '设备台账.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败，请检查后端是否启动')
  } finally {
    exporting.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

async function onFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''            // 清掉，否则同一个文件第二次选不会触发 change
  if (!file) return

  const fd = new FormData()
  fd.append('file', file)

  importing.value = true
  try {
    // 不要手动设 Content-Type，浏览器要自己加 boundary
    const res = await fetch('/api/devices/import', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + session.token },
      body: fd
    })
    const data = await res.json()
    if (data.code !== 200) {
      ElMessage.error(data.message || '导入失败')
      return
    }
    const { inserted, failed } = data.data
    if (failed.length) {
      importResult.value = { inserted, failed }
      importVisible.value = true
    } else {
      ElMessage.success(`导入成功，新增 ${inserted} 台设备`)
    }
    load()
    loadDicts()
  } catch (e) {
    ElMessage.error('导入失败，请检查后端是否启动')
  } finally {
    importing.value = false
  }
}

function emptyForm() {
  return {
    deviceNo: '', name: '', category: '', status: 'AVAILABLE',
    location: '', brand: '', model: ''
  }
}

function openAdd() {
  editingId.value = null
  form.value = emptyForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = {
    deviceNo: row.deviceNo,
    name: row.name,
    category: row.category,
    status: row.status,
    location: row.location,
    brand: row.brand,
    model: row.model
  }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.deviceNo || !form.value.name) {
    ElMessage.warning('设备编号和名称不能为空')
    return
  }

  const token = sessionStorage.getItem('token')
  const isEdit = editingId.value !== null
  const url = isEdit ? '/api/devices/' + editingId.value : '/api/devices'

  const res = await fetch(url, {
    method: isEdit ? 'PUT' : 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + token
    },
    body: JSON.stringify(form.value)
  })
  const data = await res.json()

  if (data.code !== 200) {
    ElMessage.error(data.message || '保存失败')
    return
  }

  ElMessage.success(isEdit ? '修改成功' : '新增成功')
  dialogVisible.value = false
  load()
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确定删除设备「' + row.name + '」吗？', '提示', { type: 'warning' })
  } catch {
    return
  }

  const token = sessionStorage.getItem('token')
  const res = await fetch('/api/devices/' + row.id, {
    method: 'DELETE',
    headers: { Authorization: 'Bearer ' + token }
  })
  const data = await res.json()

  if (data.code !== 200) {
    ElMessage.error(data.message || '删除失败')
    return
  }

  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  load()
  loadDicts()
})
</script>

<template>
  <el-card shadow="never" class="panel">
    <template #header>
      <div class="panel-head">
        <span class="panel-title">设备台账</span>
        <span class="panel-count">共 {{ total }} 台</span>
        <el-tag v-if="!isAdmin" size="small" type="info" effect="plain">只读</el-tag>
      </div>
    </template>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="设备编号 / 名称"
        clearable
        class="w220"
        @keyup.enter="search"
      />
      <el-select v-model="status" placeholder="全部状态" clearable class="w150">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <div class="spacer"></div>
      <template v-if="isAdmin">
        <el-button :loading="exporting" @click="exportExcel">导出</el-button>
        <el-button :loading="importing" @click="pickFile">导入</el-button>
        <el-button type="success" @click="openAdd">新增设备</el-button>
        <input ref="fileInput" type="file" accept=".xlsx" style="display:none" @change="onFileChange" />
      </template>
    </div>

    <el-table :data="list" border stripe empty-text="暂无设备数据">
      <el-table-column prop="deviceNo" label="设备编号" min-width="104" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="category" label="分类" min-width="84" />
      <el-table-column label="状态" min-width="88">
        <template #default="{ row }">
          <el-tag :type="statusOf(row.status).type" effect="light">
            {{ statusOf(row.status).text }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="location" label="存放位置" min-width="110" />
      <el-table-column prop="brand" label="品牌" min-width="88" />
      <el-table-column prop="model" label="型号" min-width="96" />
      <el-table-column v-if="isStaff" label="操作" min-width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openHistory(row)">历史</el-button>
          <el-button v-if="isAdmin" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="isAdmin" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :current-page="pageNum"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增设备' : '编辑设备'"
      width="480px"
      align-center
    >
      <el-form label-width="80px">
        <el-form-item label="设备编号">
          <el-input v-model="form.deviceNo" placeholder="例如 NET-SW-0001" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="例如 三层核心交换机" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" filterable allow-create default-first-option clearable
                     placeholder="选一个，或直接输入新的" class="full">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="full">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="存放位置">
          <el-select v-model="form.location" filterable allow-create default-first-option clearable
                     placeholder="选一个，或直接输入新的" class="full">
            <el-option v-for="l in locations" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="form.brand" />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="form.model" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="historyVisible"
      :title="'设备历史 · ' + (historyDevice?.name || '')"
      width="720px"
      align-center
    >
      <div class="hist-head">
        <span>{{ historyDevice?.deviceNo }}</span>
        <span>被借出 <b>{{ historyStats.borrowCount }}</b> 次</span>
        <span>报修 <b>{{ historyStats.repairCount }}</b> 次</span>
      </div>

      <el-tabs v-model="historyTab">
        <el-tab-pane :label="'借用记录（' + historyRes.length + '）'" name="reservation">
          <el-table :data="historyRes" v-loading="historyLoading" size="small" border
                    empty-text="这台设备还没有借用记录">
            <el-table-column label="借用人" min-width="88">
              <template #default="{ row }">{{ userNames[row.userId] || '#' + row.userId }}</template>
            </el-table-column>
            <el-table-column label="使用时间" min-width="160">
              <template #default="{ row }">{{ range(row) }}</template>
            </el-table-column>
            <el-table-column label="状态" min-width="96">
              <template #default="{ row }">{{ RES_STATUS[row.status] || row.status }}</template>
            </el-table-column>
            <el-table-column label="归还" min-width="110">
              <template #default="{ row }">{{ row.returnedAt ? fmt(row.returnedAt) : '—' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="'报修记录（' + historyTickets.length + '）'" name="ticket">
          <el-table :data="historyTickets" v-loading="historyLoading" size="small" border
                    empty-text="这台设备还没有报修记录">
            <el-table-column prop="faultDesc" label="故障描述" min-width="200" show-overflow-tooltip />
            <el-table-column label="报修时间" min-width="110">
              <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="状态" min-width="90">
              <template #default="{ row }">{{ TICKET_STATUS[row.status] || row.status }}</template>
            </el-table-column>
            <el-table-column label="处理完成" min-width="110">
              <template #default="{ row }">{{ row.resolvedAt ? fmt(row.resolvedAt) : '—' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <el-dialog v-model="importVisible" title="导入结果" width="560px">
      <p class="import-tip">
        成功导入 <b>{{ importResult.inserted }}</b> 台，失败 <b>{{ importResult.failed.length }}</b> 行。
        失败的行不会影响其他行，改好后再导一次即可（已导入的会因编号重复被跳过）。
      </p>
      <el-table :data="importResult.failed" size="small" border max-height="320">
        <el-table-column prop="row" label="行号" width="70" />
        <el-table-column prop="deviceNo" label="设备编号" width="140" />
        <el-table-column prop="reason" label="失败原因" min-width="200" />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="importVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.panel {
  border-radius: 10px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.panel-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.panel-count {
  font-size: 12px;
  color: #909399;
}

.hist-head {
  display: flex;
  gap: 18px;
  align-items: baseline;
  font-size: 13px;
  color: #5b6470;
  margin-bottom: 6px;
}
.hist-head b { color: #2b3340; }

.import-tip { margin: 0 0 12px; font-size: 13px; color: #5b6470; line-height: 1.7; }

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.spacer {
  flex: 1;
}

.w220 {
  width: 220px;
}

.w150 {
  width: 150px;
}

.full {
  width: 100%;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, put } from '../api'

const TICKET_STATUS = {
  PENDING: { label: '待受理', type: 'warning' },
  ACCEPTED: { label: '处理中', type: 'primary' },
  RESOLVED: { label: '已解决', type: 'success' },
  REJECTED: { label: '已驳回', type: 'info' }
}

const PRIORITY = {
  LOW: { label: '低', type: 'info' },
  NORMAL: { label: '普通', type: 'primary' },
  HIGH: { label: '高', type: 'warning' },
  URGENT: { label: '紧急', type: 'danger' }
}

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const filterStatus = ref('')
const filterOverdue = ref(null)

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

const actionVisible = ref(false)
const actionForm = ref({ id: null, to: '', remark: '' })
const actionTitle = ref('')
const submitting = ref(false)

const detailVisible = ref(false)
const detail = ref(null)

async function loadRefs() {
  const [d, u] = await Promise.all([
    get('/api/devices?pageNum=1&pageSize=200'),
    get('/api/users/options')
  ])
  if (d.code === 200) devices.value = d.data.list
  if (u.code === 200) users.value = u.data
}

async function load() {
  loading.value = true
  let url = `/api/tickets?pageNum=${pageNum.value}&pageSize=${pageSize.value}`
  if (filterStatus.value) url += `&status=${filterStatus.value}`
  if (filterOverdue.value !== null && filterOverdue.value !== '') url += `&overdue=${filterOverdue.value}`

  const res = await get(url)
  loading.value = false
  if (res.code === 200) {
    rows.value = res.data.list
    total.value = res.data.total
  } else {
    ElMessage.error(res.message || '加载失败')
  }
}

function search() {
  pageNum.value = 1
  load()
}

function openAction(row, to) {
  actionForm.value = { id: row.id, to, remark: '' }
  actionTitle.value = { ACCEPTED: '受理工单', RESOLVED: '标记已解决', REJECTED: '驳回工单' }[to]
  actionVisible.value = true
}

async function submitAction() {
  submitting.value = true
  const res = await put(
    `/api/tickets/${actionForm.value.id}/status?to=${actionForm.value.to}` +
      `&remark=${encodeURIComponent(actionForm.value.remark || '')}`
  )
  submitting.value = false

  if (res.code === 200) {
    ElMessage.success('已更新')
    actionVisible.value = false
    load()
  } else {
    ElMessage.error(res.message || '操作失败')
  }
}

async function openDetail(row) {
  const res = await get(`/api/tickets/${row.id}`)
  if (res.code === 200) {
    detail.value = res.data
    detailVisible.value = true
  } else {
    ElMessage.error(res.message || '加载详情失败')
  }
}

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

onMounted(() => {
  loadRefs()
  load()
})
</script>

<template>
  <div>
    <div class="head">
      <h2>工单管理</h2>
      <div class="filters">
        <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width:130px" @change="search">
          <el-option label="待受理" value="PENDING" />
          <el-option label="处理中" value="ACCEPTED" />
          <el-option label="已解决" value="RESOLVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-select v-model="filterOverdue" placeholder="是否超时" clearable style="width:120px" @change="search">
          <el-option label="仅超时" :value="1" />
          <el-option label="未超时" :value="0" />
        </el-select>
        <el-button @click="search">刷新</el-button>
      </div>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="编号" min-width="60" />
      <el-table-column label="设备" min-width="112">
        <template #default="{ row }">{{ deviceName[row.deviceId] || '设备 ' + row.deviceId }}</template>
      </el-table-column>
      <el-table-column label="报修人" min-width="80">
        <template #default="{ row }">{{ userName[row.reporterId] || '#' + row.reporterId }}</template>
      </el-table-column>
      <el-table-column prop="faultDesc" label="故障描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="优先级" min-width="84">
        <template #default="{ row }">
          <el-tag :type="PRIORITY[row.priority]?.type" size="small">
            {{ PRIORITY[row.priority]?.label || row.priority }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="108">
        <template #default="{ row }">
          <el-tag :type="TICKET_STATUS[row.status]?.type" size="small">
            {{ TICKET_STATUS[row.status]?.label || row.status }}
          </el-tag>
          <el-tag v-if="row.overdue === 1" type="danger" size="small" style="margin-left:6px">超时</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="报修时间" min-width="132">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.status === 'PENDING'" link type="success" @click="openAction(row, 'ACCEPTED')">受理</el-button>
          <el-button v-if="row.status === 'ACCEPTED'" link type="success" @click="openAction(row, 'RESOLVED')">解决</el-button>
          <el-button
            v-if="row.status === 'PENDING' || row.status === 'ACCEPTED'"
            link type="danger" @click="openAction(row, 'REJECTED')"
          >驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top:14px"
      @current-change="load"
    />

    <el-dialog v-model="actionVisible" :title="actionTitle" width="480px">
      <el-form label-width="80px">
        <el-form-item label="处理说明">
          <el-input v-model="actionForm.remark" type="textarea" :rows="3" maxlength="255" show-word-limit
                    placeholder="会记进工单流水，比如「已更换电源模块」" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAction">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="工单详情" width="640px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="编号">{{ detail.ticket.id }}</el-descriptions-item>
          <el-descriptions-item label="设备">
            {{ deviceName[detail.ticket.deviceId] || detail.ticket.deviceId }}
          </el-descriptions-item>
          <el-descriptions-item label="报修人">
            {{ userName[detail.ticket.reporterId] || detail.ticket.reporterId }}
          </el-descriptions-item>
          <el-descriptions-item label="受理人">
            {{ detail.ticket.handlerId ? (userName[detail.ticket.handlerId] || detail.ticket.handlerId) : '未受理' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="TICKET_STATUS[detail.ticket.status]?.type" size="small">
              {{ TICKET_STATUS[detail.ticket.status]?.label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="优先级">
            {{ PRIORITY[detail.ticket.priority]?.label }}
          </el-descriptions-item>
          <el-descriptions-item label="故障" :span="2">{{ detail.ticket.faultDesc }}</el-descriptions-item>
        </el-descriptions>

        <h4 style="margin:18px 0 10px">处理流水</h4>
        <el-timeline>
          <el-timeline-item
            v-for="log in detail.logs"
            :key="log.id"
            :timestamp="fmt(log.createdAt) + ' · ' + (userName[log.operatorId] || '#' + log.operatorId)"
            placement="top"
          >
            <el-tag v-if="log.fromStatus" size="small" type="info">{{ log.fromStatus }}</el-tag>
            <span v-if="log.fromStatus" style="margin:0 6px">→</span>
            <el-tag size="small">{{ log.toStatus }}</el-tag>
            <span v-if="log.remark" style="margin-left:10px">{{ log.remark }}</span>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.head h2 { margin: 0; font-size: 20px; }
.filters { display: flex; gap: 10px; }
</style>

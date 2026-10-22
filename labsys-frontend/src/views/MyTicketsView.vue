<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { get, post } from '../api'

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

const devices = ref([])
const deviceName = computed(() => {
  const map = {}
  devices.value.forEach((d) => { map[d.id] = d.name })
  return map
})

const dialogVisible = ref(false)
const submitting = ref(false)
const form = ref(emptyForm())

const detailVisible = ref(false)
const detail = ref(null)

function emptyForm() {
  return { deviceId: null, faultDesc: '', priority: 'NORMAL' }
}

async function loadDevices() {
  const res = await get('/api/devices?pageNum=1&pageSize=200')
  if (res.code === 200) devices.value = res.data.list
}

async function load() {
  loading.value = true
  const res = await get(`/api/tickets/mine?pageNum=${pageNum.value}&pageSize=${pageSize.value}`)
  loading.value = false
  if (res.code === 200) {
    rows.value = res.data.list
    total.value = res.data.total
  } else {
    ElMessage.error(res.message || '加载失败')
  }
}

function openCreate() {
  form.value = emptyForm()
  dialogVisible.value = true
}

async function submit() {
  if (!form.value.deviceId) return ElMessage.warning('请选择设备')
  if (!form.value.faultDesc.trim()) return ElMessage.warning('请填写故障描述')

  submitting.value = true
  const res = await post('/api/tickets', form.value)
  submitting.value = false

  if (res.code === 200) {
    ElMessage.success('已提交，等待处理')
    dialogVisible.value = false
    pageNum.value = 1
    load()
  } else {
    ElMessage.error(res.message || '提交失败')
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
  loadDevices()
  load()
})
</script>

<template>
  <div>
    <div class="head">
      <h2>我的报修</h2>
      <el-button type="primary" @click="openCreate">报修</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="编号" min-width="60" />
      <el-table-column label="设备" min-width="112">
        <template #default="{ row }">{{ deviceName[row.deviceId] || '设备 ' + row.deviceId }}</template>
      </el-table-column>
      <el-table-column prop="faultDesc" label="故障描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="优先级" min-width="84">
        <template #default="{ row }">
          <el-tag :type="PRIORITY[row.priority]?.type" size="small">
            {{ PRIORITY[row.priority]?.label || row.priority }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="104">
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
      <el-table-column label="操作" min-width="96" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="dialogVisible" title="提交报修" width="520px">
      <el-form label-width="88px">
        <el-form-item label="设备">
          <el-select v-model="form.deviceId" placeholder="选择出故障的设备" style="width:100%">
            <el-option
              v-for="d in devices"
              :key="d.id"
              :label="`${d.deviceNo} ${d.name}`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述">
          <el-input v-model="form.faultDesc" type="textarea" :rows="4" maxlength="500" show-word-limit
                    placeholder="说清楚现象，比如「开机后屏幕不亮，电源灯也不亮」" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio-button value="LOW">低</el-radio-button>
            <el-radio-button value="NORMAL">普通</el-radio-button>
            <el-radio-button value="HIGH">高</el-radio-button>
            <el-radio-button value="URGENT">紧急</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="工单详情" width="620px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="编号">{{ detail.ticket.id }}</el-descriptions-item>
          <el-descriptions-item label="设备">
            {{ deviceName[detail.ticket.deviceId] || detail.ticket.deviceId }}
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

        <h4 style="margin:18px 0 10px">处理进度</h4>
        <el-timeline>
          <el-timeline-item
            v-for="log in detail.logs"
            :key="log.id"
            :timestamp="fmt(log.createdAt)"
            placement="top"
          >
            <div>
              <el-tag v-if="log.fromStatus" size="small" type="info">{{ log.fromStatus }}</el-tag>
              <span v-if="log.fromStatus" style="margin:0 6px">→</span>
              <el-tag size="small">{{ log.toStatus }}</el-tag>
              <span v-if="log.remark" style="margin-left:10px">{{ log.remark }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.head h2 { margin: 0; font-size: 20px; }
</style>

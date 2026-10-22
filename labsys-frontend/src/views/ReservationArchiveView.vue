<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { get } from '../api'

const RES_STATUS = {
  RETURNED: { label: '已归还', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' },
  REJECTED: { label: '已拒绝', type: 'info' }
}

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const filterStatus = ref('')
const filterDevice = ref(null)

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
  let url = `/api/reservations?archived=true&pageNum=${pageNum.value}&pageSize=${pageSize.value}`
  if (filterStatus.value) url += `&status=${filterStatus.value}`
  if (filterDevice.value) url += `&deviceId=${filterDevice.value}`

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

function pad(n) {
  return String(n).padStart(2, '0')
}

/** 「10月23日 14:00–17:00 · 3 小时」——与预约管理列表保持同一种写法 */
function humanRange(row) {
  if (!row.startTime) return '-'
  const s = new Date(row.startTime)
  const e = new Date(row.endTime)
  const hm = (d) => `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  return `${s.getMonth() + 1}月${s.getDate()}日 ${hm(s)}–${hm(e)}`
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
      <div>
        <h2>预约归档</h2>
        <p class="sub">已归还、已取消、已拒绝的预约放在这里，不在「预约管理」里占地方。</p>
      </div>
      <div class="filters">
        <el-select v-model="filterStatus" placeholder="全部归档状态" clearable style="width:140px" @change="search">
          <el-option label="已归还" value="RETURNED" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
        <el-select v-model="filterDevice" placeholder="全部设备" clearable style="width:160px" @change="search">
          <el-option v-for="d in devices" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-button @click="search">刷新</el-button>
      </div>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="编号" min-width="60" />
      <el-table-column label="设备" min-width="112">
        <template #default="{ row }">{{ deviceName[row.deviceId] || '设备 ' + row.deviceId }}</template>
      </el-table-column>
      <el-table-column label="预约人" min-width="80">
        <template #default="{ row }">{{ userName[row.userId] || '#' + row.userId }}</template>
      </el-table-column>
      <el-table-column label="借出理由" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.purpose">{{ row.purpose }}</span>
          <span v-else style="color:#b7c0cc">未填写</span>
        </template>
      </el-table-column>
      <el-table-column label="使用时间" min-width="170">
        <template #default="{ row }">{{ humanRange(row) }}</template>
      </el-table-column>
      <el-table-column label="申请时间" min-width="132">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="归还时间" min-width="132">
        <template #default="{ row }">
          <span v-if="row.returnedAt" style="color:#0f8a5f">{{ fmt(row.returnedAt) }}</span>
          <span v-else style="color:#b7c0cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="104">
        <template #default="{ row }">
          <el-tag :type="RES_STATUS[row.status]?.type" size="small">
            {{ RES_STATUS[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top:14px"
      @current-change="search"
    />
  </div>
</template>

<style scoped>
.head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.head h2 { margin: 0; font-size: 20px; }
.head .sub { margin: 4px 0 0; font-size: 13px; color: #8a939f; }
.filters { display: flex; gap: 10px; }
</style>

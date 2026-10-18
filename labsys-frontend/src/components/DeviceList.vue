<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const keyword = ref('')
const status = ref('')

const isAdmin = localStorage.getItem('role') === 'ADMIN'

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
  const token = localStorage.getItem('token')
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
    localStorage.removeItem('token')
    localStorage.removeItem('username')
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

  const token = localStorage.getItem('token')
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

  const token = localStorage.getItem('token')
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

onMounted(load)
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
      <el-button v-if="isAdmin" type="success" @click="openAdd">新增设备</el-button>
    </div>

    <el-table :data="list" border stripe empty-text="暂无设备数据">
      <el-table-column prop="deviceNo" label="设备编号" width="140" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusOf(row.status).type" effect="light">
            {{ statusOf(row.status).text }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="location" label="存放位置" min-width="140" />
      <el-table-column prop="brand" label="品牌" width="100" />
      <el-table-column prop="model" label="型号" width="120" />
      <el-table-column v-if="isAdmin" label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
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
          <el-input v-model="form.category" placeholder="例如 网络设备" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="full">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="存放位置">
          <el-input v-model="form.location" placeholder="例如 实验楼1楼-101" />
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

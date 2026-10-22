<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const loading = ref(false)

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  const token = sessionStorage.getItem('token')
  const params = new URLSearchParams()
  params.append('pageNum', pageNum.value)
  params.append('pageSize', pageSize.value)
  if (keyword.value) params.append('keyword', keyword.value)

  try {
    const res = await fetch('/api/logs?' + params.toString(), {
      headers: { Authorization: 'Bearer ' + token }
    })
    const data = await res.json()

    if (data.code === 401) {
      sessionStorage.removeItem('token')
      location.reload()
      return
    }
    if (data.code !== 200) {
      ElMessage.error(data.message)
      return
    }

    list.value = data.data.list
    total.value = data.data.total
  } catch (e) {
    ElMessage.error('加载失败，检查后端是否启动')
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

function reset() {
  keyword.value = ''
  pageNum.value = 1
  load()
}

function handlePageChange(p) {
  pageNum.value = p
  load()
}

function methodType(method) {
  if (method === 'POST') return 'success'
  if (method === 'PUT') return 'warning'
  if (method === 'DELETE') return 'danger'
  return 'info'
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按操作人或路径搜索"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <span class="count">共 {{ total }} 条</span>
    </div>

    <el-table :data="list" v-loading="loading" stripe border style="width: 100%">
      <el-table-column label="时间" min-width="136">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="username" label="操作人" min-width="88">
        <template #default="{ row }">{{ row.username || '-' }}</template>
      </el-table-column>
      <el-table-column label="方法" min-width="76">
        <template #default="{ row }">
          <el-tag size="small" :type="methodType(row.method)" effect="plain">{{ row.method }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路径" min-width="180" show-overflow-tooltip />
      <el-table-column label="结果" min-width="84">
        <template #default="{ row }">
          <el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'" effect="plain">
            {{ row.success === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" min-width="84">
        <template #default="{ row }">{{ row.costMs }} ms</template>
      </el-table-column>
      <el-table-column prop="ip" label="来源 IP" min-width="112" />
    </el-table>

    <div class="pager">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        @current-change="handlePageChange"
      />
    </div>
  </el-card>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.count {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

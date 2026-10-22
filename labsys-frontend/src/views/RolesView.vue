<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { get, put } from '../api'

const rows = ref([])
const loading = ref(false)

/** 全部菜单（扁平），用来拼授权勾选框的树 */
const menus = ref([])

const editVisible = ref(false)
const target = ref(null)
const treeRef = ref(null)
const saving = ref(false)

/** 扁平列表拼成树：先建节点表，再按 parentId 挂，顶级是 parentId 为 0 的那些 */
const treeData = computed(() => {
  const nodeById = {}
  menus.value.forEach((m) => { nodeById[m.id] = { ...m, children: [] } })
  const roots = []
  menus.value.forEach((m) => {
    const node = nodeById[m.id]
    if (m.parentId === 0) {
      roots.push(node)
    } else if (nodeById[m.parentId]) {
      nodeById[m.parentId].children.push(node)
    }
  })
  // 叶子节点不留空 children，否则 el-tree 会给它画一个能点开的箭头
  Object.values(nodeById).forEach((n) => { if (!n.children.length) delete n.children })
  return roots
})

/** 选了子页面但没选目录时，el-tree 会把它算成“半选”，所以后端负责把父目录补齐 */
async function load() {
  loading.value = true
  const res = await get('/api/roles')
  loading.value = false
  if (res.code === 200) {
    rows.value = res.data
  } else {
    ElMessage.error(res.message || '加载失败')
  }
}

async function loadMenus() {
  const res = await get('/api/menus/all')
  if (res.code === 200) menus.value = res.data
}

async function openEdit(row) {
  target.value = row
  editVisible.value = true
  await nextTick()
  // 先清掉上一次的残留，再按这个角色已有的授权打勾
  treeRef.value?.setCheckedKeys(row.menuIds || [])
}

async function save() {
  const checked = treeRef.value.getCheckedKeys()
  if (!checked.length) return ElMessage.warning('至少要勾选一个菜单')

  saving.value = true
  const res = await put(`/api/roles/${target.value.id}/menus`, { menuIds: checked })
  saving.value = false

  if (res.code === 200) {
    ElMessage.success('已保存，相关用户重新登录后生效')
    editVisible.value = false
    load()
  } else {
    ElMessage.error(res.message || '保存失败')
  }
}

onMounted(() => {
  loadMenus()
  load()
})
</script>

<template>
  <div>
    <div class="head">
      <h2>角色管理</h2>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="alert"
      title="角色本身的名称和编码写死在初始化脚本里，这一页只调整「每个角色能看到哪些菜单」。保存后相关用户需要重新登录才生效。"
    />

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="编号" min-width="56" />
      <el-table-column prop="roleCode" label="角色编码" min-width="110" />
      <el-table-column prop="roleName" label="角色名称" min-width="120" />
      <el-table-column label="已授权菜单" min-width="104">
        <template #default="{ row }">{{ row.menuIds.length }} 项</template>
      </el-table-column>
      <el-table-column label="状态" min-width="88">
        <template #default="{ row }">
          <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">菜单授权</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editVisible" :title="'菜单授权 · ' + (target?.roleName || '')" width="560px">
      <p class="hint">
        勾选这个角色能看到的菜单。父目录会自动带上，不用手动勾 ——
        只给子页面不给目录的话，子菜单在拼树时会找不到父节点而被丢掉。
      </p>

      <el-tree
        ref="treeRef"
        :data="treeData"
        :props="{ label: 'menuName', children: 'children' }"
        node-key="id"
        show-checkbox
        default-expand-all
        class="tree"
      >
        <template #default="{ data }">
          <span>{{ data.menuName }}</span>
          <span class="path">{{ data.path || '（目录）' }}</span>
        </template>
      </el-tree>

      <p v-if="target?.roleCode === 'ADMIN'" class="warn">
        管理员角色必须保留「用户管理」和「角色管理」，去掉这两项会被后端拒绝 —— 否则下次谁也改不了授权。
      </p>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.head h2 { margin: 0; font-size: 20px; }
.alert { margin-bottom: 14px; }
.hint { margin: 0 0 12px; font-size: 13px; color: #5b6470; line-height: 1.7; }
.tree { max-height: 380px; overflow: auto; padding: 6px 0; border: 1px solid #ebeef5; border-radius: 8px; }
.path { margin-left: 8px; font-size: 12px; color: #b7c0cc; }
.warn { margin: 12px 0 0; font-size: 12.5px; color: #a35c00; line-height: 1.7; }
</style>

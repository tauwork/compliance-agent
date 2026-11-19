<template>
  <div>
    <h2 class="pt">角色权限</h2>
    <p class="pdesc">菜单决定侧栏里能打开什么。知识库权限决定政策法规库里能进入哪些子库。</p>
    <div class="wrap">
      <aside class="roles">
        <div v-for="r in roles" :key="r.id" class="role" :class="{ on: current?.id === r.id }" @click="pick(r)">
          <b>{{ r.name }}</b>
          <div class="remark">{{ r.remark }}</div>
        </div>
      </aside>
      <el-card v-if="current" shadow="never" class="panel">
        <template #header>
          <span>{{ current.name }}</span>
          <el-button type="primary" size="small" style="float: right" :loading="saving" @click="save">保存权限</el-button>
        </template>
        <h3>菜单访问权限</h3>
        <div v-for="g in session.catalog" :key="g.name" class="block">
          <div class="g">{{ g.name }}</div>
          <el-checkbox v-for="item in g.items" :key="item.key" :model-value="menus.includes(item.key)"
                       :disabled="locked(item.key)" @change="toggleMenu(item.key, $event)">{{ item.name }}</el-checkbox>
        </div>
        <h3>知识库访问权限</h3>
        <div class="block">
          <el-checkbox v-for="lib in libraries" :key="lib.id" :model-value="libraryIds.includes(lib.id)"
                       @change="toggleLib(lib.id, $event)">{{ lib.name }}</el-checkbox>
          <div v-if="!libraries.length" class="remark">还没有知识库</div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoleGrants, getRoles, saveRoleGrants } from '../api'
import { loadSession, session } from '../session'

const roles = ref([])
const current = ref(null)
const menus = ref([])
const libraryIds = ref([])
const libraries = ref([])
const saving = ref(false)

onMounted(async () => {
  roles.value = await getRoles()
  if (roles.value[0]) await pick(roles.value[0])
})

async function pick(role) {
  current.value = role
  const data = await getRoleGrants(role.id)
  menus.value = [...data.menus]
  libraryIds.value = [...data.libraryIds]
  libraries.value = data.libraries || []
}
function locked(key) {
  return current.value?.code === 'admin' && (key === '/system/users' || key === '/system/roles')
}
function toggleMenu(key, checked) {
  menus.value = checked ? [...menus.value, key] : menus.value.filter(k => k !== key)
}
function toggleLib(id, checked) {
  libraryIds.value = checked ? [...libraryIds.value, id] : libraryIds.value.filter(k => k !== id)
}
async function save() {
  saving.value = true
  try {
    await saveRoleGrants(current.value.id, { menus: menus.value, libraryIds: libraryIds.value })
    if (session.user?.roleId === current.value.id) await loadSession()
    ElMessage.success('权限已保存')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally { saving.value = false }
}
</script>

<style scoped>
.wrap { display: flex; gap: 14px; align-items: flex-start; }
.roles { width: 220px; flex: none; background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 8px; }
.role { padding: 10px 12px; border-radius: 8px; cursor: pointer; }
.role.on { background: #eef3f8; }
.remark { color: #8b99ad; font-size: 12px; margin-top: 4px; }
.panel { flex: 1; min-width: 0; }
h3 { margin: 8px 0 10px; font-size: 14px; }
.block { margin-bottom: 14px; }
.g { color: #6d7d92; font-size: 12px; margin-bottom: 6px; }
.block .el-checkbox { margin-right: 16px; }
</style>

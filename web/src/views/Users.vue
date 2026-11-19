<template>
  <div>
    <h2 class="pt">用户管理</h2>
    <p class="pdesc">给用户指定一个角色。角色决定能打开哪些菜单、能进入哪些知识库。</p>
    <el-button type="primary" style="margin-bottom: 14px" @click="openCreate">＋ 新建用户</el-button>
    <el-card shadow="never">
      <el-table :data="session.users" stripe>
        <el-table-column prop="displayName" label="姓名" width="140" />
        <el-table-column prop="username" label="账号" width="140" />
        <el-table-column prop="roleName" label="角色" width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">调整角色</el-button>
            <el-button link @click="toggle(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="form.id ? '调整角色' : '新建用户'" width="440px">
      <el-form label-width="70px">
        <el-form-item label="姓名"><el-input v-model="form.displayName" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="账号"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleId" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createUser, getRoles, updateUser } from '../api'
import { loadSession, session } from '../session'

const roles = ref([])
const dlg = ref(false)
const form = ref({ id: null, displayName: '', username: '', roleId: null })

onMounted(async () => { roles.value = await getRoles() })

function openCreate() {
  form.value = { id: null, displayName: '', username: '', roleId: roles.value[0]?.id || null }
  dlg.value = true
}
function openEdit(row) {
  form.value = { id: row.id, displayName: row.displayName, username: row.username, roleId: row.roleId }
  dlg.value = true
}
async function save() {
  try {
    if (form.value.id) await updateUser(form.value.id, { roleId: form.value.roleId, displayName: form.value.displayName })
    else await createUser(form.value)
    dlg.value = false
    await loadSession()
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}
async function toggle(row) {
  try {
    await updateUser(row.id, { enabled: !row.enabled })
    await loadSession()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}
</script>

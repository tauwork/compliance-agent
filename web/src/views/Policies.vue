<template>
  <div class="wrap">
    <aside class="libs">
      <div class="lib-actions">
        <el-button size="small" @click="catDlg = true">新建类别</el-button>
        <el-button size="small" type="primary" @click="libDlg = true">新建子库</el-button>
      </div>
      <div v-for="c in visibleTree" :key="c.id" class="cat">
        <div class="cat-name">{{ c.name }}</div>
        <div v-for="lib in c.libraries" :key="lib.id" class="lib" :class="{ on: libraryId === lib.id }"
             @click="pick(c, lib)">
          <span>{{ lib.name }}</span>
          <em>{{ lib.policyCount }}</em>
        </div>
      </div>
    </aside>

    <div class="main">
      <h2 class="pt">政策法规库</h2>
      <p class="pdesc">{{ currentLabel }}。失效日期早于今天的归入过期政策，未填失效日期的长期有效。</p>
      <div class="bar">
        <el-radio-group v-model="validity" @change="loadPolicies">
          <el-radio-button value="effective">有效政策</el-radio-button>
          <el-radio-button value="expired">过期政策</el-radio-button>
          <el-radio-button value="all">全部</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="dlg = true">＋ 上传政策文本</el-button>
      </div>

      <el-empty v-if="!policies.length" description="这个子库下没有符合条件的政策" />
      <el-card v-for="p in policies" :key="p.id" shadow="never" class="card" @click="openDoc(p.id)">
        <div class="row">
          <div class="fico">{{ p.title.includes('担保') ? '金' : p.title.includes('租赁') ? '租' : '资' }}</div>
          <div style="flex: 1; min-width: 0">
            <b>{{ p.title }}</b>
            <div class="meta">{{ p.docNo || '—' }} · {{ p.issuer }} · 生效 {{ p.effectDate || '—' }} · 失效 {{ p.expireDate || '长期' }}</div>
          </div>
          <el-tag :type="p.validity === 'expired' ? 'info' : 'success'" size="small">{{ p.validity === 'expired' ? '过期' : '有效' }}</el-tag>
          <el-tag :type="statusType(p.status)" size="small">{{ statusText(p.status) }}</el-tag>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="dlg" title="上传政策文本" width="640px">
      <el-form label-width="90px">
        <el-form-item label="所属子库"><el-input :model-value="libraryName" disabled /></el-form-item>
        <el-form-item label="政策标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="文号"><el-input v-model="form.docNo" placeholder="如：演示〔2026〕3号" /></el-form-item>
        <el-form-item label="发文机关"><el-input v-model="form.issuer" /></el-form-item>
        <el-form-item label="生效日期"><el-date-picker v-model="form.effectDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="失效日期"><el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="不填则长期有效" /></el-form-item>
        <el-form-item label="政策全文">
          <el-input v-model="form.sourceText" type="textarea" :rows="8" placeholder="粘贴政策全文。入库后按条款自动分片，规则仍通过「发起拆解」生成。" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="upload">上传并入库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="catDlg" title="新建类别" width="420px">
      <el-input v-model="catName" placeholder="例如：财务制度" />
      <template #footer>
        <el-button @click="catDlg = false">取消</el-button>
        <el-button type="primary" @click="addCategory">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="libDlg" title="新建子政策法规库" width="460px">
      <el-form label-width="80px">
        <el-form-item label="所属分类">
          <el-select v-model="libForm.categoryId" style="width: 100%">
            <el-option v-for="c in visibleTree" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="子库名称"><el-input v-model="libForm.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="libForm.description" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="libDlg = false">取消</el-button>
        <el-button type="primary" @click="addLibrary">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createCategory, createLibrary, createPolicy, getPolicies, getPolicyTree } from '../api'
import { session } from '../session'

const router = useRouter()
const tree = ref([])
const policies = ref([])
const libraryId = ref(null)
const categoryName = ref('')
const libraryName = ref('')
const validity = ref('effective')
const dlg = ref(false)
const catDlg = ref(false)
const libDlg = ref(false)
const catName = ref('')
const libForm = ref({ categoryId: null, name: '', description: '' })
const form = ref({ title: '', docNo: '', issuer: '', effectDate: '', expireDate: '', sourceText: '' })

const currentLabel = computed(() => `${categoryName.value || '分类'} / ${libraryName.value || '子库'}`)
const visibleTree = computed(() => tree.value
  .map(c => ({ ...c, libraries: (c.libraries || []).filter(lib => session.libraryIds.includes(lib.id)) }))
  .filter(c => c.libraries.length))

onMounted(loadTree)
watch(() => session.libraryIds.join(','), () => reconcile())

async function loadTree() {
  tree.value = await getPolicyTree()
  reconcile()
}
function reconcile() {
  const flat = visibleTree.value.flatMap(c => c.libraries.map(lib => ({ c, lib })))
  if (flat.some(x => x.lib.id === libraryId.value)) return
  if (flat[0]) pick(flat[0].c, flat[0].lib)
  else {
    libraryId.value = null
    policies.value = []
  }
}
function pick(category, lib) {
  libraryId.value = lib.id
  categoryName.value = category.name
  libraryName.value = lib.name
  libForm.value.categoryId = category.id
  loadPolicies()
}
async function loadPolicies() {
  if (!libraryId.value) return
  policies.value = await getPolicies({ libraryId: libraryId.value, validity: validity.value })
}
function openDoc(id) {
  router.push(`/policies/${id}`)
}
async function upload() {
  if (!form.value.title || !form.value.sourceText) {
    ElMessage.warning('标题与全文必填'); return
  }
  const p = await createPolicy({ ...form.value, libraryId: libraryId.value })
  dlg.value = false
  form.value = { title: '', docNo: '', issuer: '', effectDate: '', expireDate: '', sourceText: '' }
  await loadTree()
  await loadPolicies()
  ElMessage.success(`已入库并完成分片：${p.title}`)
  router.push(`/policies/${p.id}`)
}
async function addCategory() {
  if (!catName.value.trim()) return
  await createCategory(catName.value.trim())
  catName.value = ''
  catDlg.value = false
  await loadTree()
}
async function addLibrary() {
  if (!libForm.value.name.trim() || !libForm.value.categoryId) {
    ElMessage.warning('选择分类并填写子库名称'); return
  }
  const lib = await createLibrary({ ...libForm.value, name: libForm.value.name.trim() })
  libDlg.value = false
  libForm.value = { ...libForm.value, name: '', description: '' }
  await loadTree()
  const category = tree.value.find(c => c.id === lib.categoryId)
  const created = category?.libraries.find(l => l.id === lib.id)
  if (category && created) pick(category, created)
}
const statusType = (s) => s === 'decomposed' ? 'success' : s === 'decomposing' ? 'primary' : 'info'
const statusText = (s) => s === 'decomposed' ? '已拆解' : s === 'decomposing' ? '拆解中' : '待拆解'
</script>

<style scoped>
.wrap { display: flex; gap: 14px; align-items: flex-start; }
.libs { width: 220px; flex: none; background: #fff; border-radius: 8px; padding: 12px; }
.lib-actions { display: flex; gap: 8px; margin-bottom: 12px; }
.cat { margin-bottom: 12px; }
.cat-name { font-size: 12px; color: #8b99ad; margin-bottom: 6px; }
.lib { display: flex; justify-content: space-between; padding: 8px 10px; border-radius: 6px; cursor: pointer; font-size: 13px; }
.lib:hover { background: #f2f6fb; }
.lib.on { background: #eaf2fc; color: #2C75B8; font-weight: 700; }
.lib em { font-style: normal; color: #8b99ad; }
.main { flex: 1; min-width: 0; }
.bar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 14px; }
.card { margin-bottom: 10px; cursor: pointer; }
.row { display: flex; align-items: center; gap: 14px; }
.fico { width: 42px; height: 42px; border-radius: 8px; background: #eaf2fc; color: #2C75B8;
  display: flex; align-items: center; justify-content: center; font-size: 19px; font-weight: 800; flex-shrink: 0; }
.meta { font-size: 12px; color: #8b99ad; margin-top: 4px; }
</style>

<template>
  <div>
    <h2 class="pt">AI规则库</h2>
    <p class="pdesc">预审和巡检共用这里的规则。内置规则已发布即可生效；政策拆解产出的草稿以 D- 编号入库，发布后才参与判定。</p>

    <el-row :gutter="12">
      <el-col :span="6" v-for="c in cards" :key="c.label">
        <el-card shadow="never" class="kpi"><div class="k">{{ c.label }}</div><div class="v">{{ c.value }}</div></el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 14px">
      <div class="bar">
        <el-radio-group v-model="source">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="ai">AI拆解</el-radio-button>
          <el-radio-button value="builtin">内置</el-radio-button>
        </el-radio-group>
        <el-select v-model="scenario" style="width: 140px" placeholder="场景">
          <el-option label="全部场景" value="all" />
          <el-option label="资产处置" value="asset" />
          <el-option label="对外担保" value="guarantee" />
          <el-option label="资产租赁" value="lease" />
        </el-select>
        <el-select v-model="status" style="width: 140px">
          <el-option label="全部状态" value="all" />
          <el-option label="草稿" value="draft" />
          <el-option label="已发布" value="published" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
      </div>
      <el-table :data="shown" stripe style="margin-top: 12px" @row-click="openRow" empty-text="没有符合条件的规则">
        <el-table-column prop="code" label="编号" width="120" />
        <el-table-column prop="name" label="规则" min-width="180" show-overflow-tooltip />
        <el-table-column label="场景" width="110">
          <template #default="{ row }">{{ scenarioName(row.scenario) }}</template>
        </el-table-column>
        <el-table-column label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.severity === '红' ? 'danger' : 'warning'" size="small">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-tag :type="isAi(row) ? 'success' : 'info'" size="small">{{ isAi(row) ? 'AI拆解' : '内置' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="clauseRef" label="依据" min-width="180" show-overflow-tooltip />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @click.stop @change="toggle(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button v-if="row.status === 'draft'" link type="primary" @click.stop="transit(row, 'publish')">发布</el-button>
            <el-button v-if="row.status === 'draft'" link type="danger" @click.stop="transit(row, 'reject')">驳回</el-button>
            <el-button link @click.stop="openRow(row)">条件</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="open" :title="active ? `${active.code} ${active.name}` : '规则'" width="640px">
      <template v-if="active">
        <div class="meta">{{ scenarioName(active.scenario) }} · {{ statusText(active.status) }} · {{ active.clauseRef || '未填依据' }}</div>
        <div v-for="(c, i) in conditions" :key="i" class="cond">
          <b>{{ c.field }}</b>
          <span>{{ opText(c.op) }}</span>
          <b>{{ c.value }}</b>
        </div>
        <div v-if="!conditions.length" class="meta">这条规则没有可解析的条件。</div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRules, ruleTransition, toggleRule } from '../api'
import { scenarioName } from '../revise'

const rules = ref([])
const source = ref('all')
const scenario = ref('all')
const status = ref('all')
const open = ref(false)
const active = ref(null)

const cards = computed(() => [
  { label: '规则', value: rules.value.length },
  { label: '已发布', value: rules.value.filter(r => r.status === 'published' && r.enabled).length },
  { label: 'AI草稿', value: rules.value.filter(r => isAi(r) && r.status === 'draft').length },
  { label: '已停用', value: rules.value.filter(r => !r.enabled).length }
])

const shown = computed(() => rules.value.filter(r => {
  if (source.value === 'ai' && !isAi(r)) return false
  if (source.value === 'builtin' && isAi(r)) return false
  if (scenario.value !== 'all' && r.scenario !== scenario.value) return false
  if (status.value !== 'all' && r.status !== status.value) return false
  return true
}))

const conditions = computed(() => {
  if (!active.value?.dsl) return []
  try {
    const parsed = JSON.parse(active.value.dsl)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
})

function isAi(row) {
  return String(row.code || '').startsWith('D-')
}
function statusText(s) {
  return { draft: '草稿', trial: '试跑', published: '已发布', rejected: '已驳回' }[s] || s
}
function opText(op) {
  return { eq: '等于', neq: '不等于', gt: '大于', gte: '大于等于', lt: '小于', lte: '小于等于', in: '属于', lt_field: '低于' }[op] || op
}

async function load() {
  rules.value = await getRules()
}
function openRow(row) {
  active.value = row
  open.value = true
}
async function toggle(row) {
  try {
    await toggleRule(row.id)
    await load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '启停失败')
  }
}
async function transit(row, action) {
  const publish = action === 'publish'
  try {
    await ElMessageBox.confirm(
      publish ? `发布「${row.name}」后，预审和巡检会按这条规则判定。` : `驳回「${row.name}」后，它不会参与判定。`,
      publish ? '发布规则' : '驳回规则')
  } catch { return }
  try {
    await ruleTransition(row.id, action)
    ElMessage.success(publish ? '已发布' : '已驳回')
    await load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

onMounted(load)
</script>

<style scoped>
.kpi .k { color: #7b8aa0; font-size: 12px; }
.kpi .v { font-size: 22px; font-weight: 700; margin-top: 4px; }
.bar { display: flex; align-items: center; gap: 12px; }
.meta { color: #7b8aa0; font-size: 13px; margin-bottom: 10px; }
.cond { display: flex; gap: 8px; padding: 8px 0; border-bottom: 1px solid #f0f3f8; font-size: 13px; }
.cond span { color: #7b8aa0; }
</style>

<template>
  <div>
    <h2 class="pt">全量巡检</h2>
    <p class="pdesc">对已入库申报按当前生效规则重新扫描，与前置预审共用同一套判定。已有风险事件不重复生成，新命中会补记并留痕。</p>

    <el-card shadow="never">
      <div class="bar">
        <el-radio-group v-model="scenario">
          <el-radio-button value="all">全部场景</el-radio-button>
          <el-radio-button value="asset">资产处置</el-radio-button>
          <el-radio-button value="guarantee">对外担保</el-radio-button>
          <el-radio-button value="lease">资产租赁</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :loading="running" @click="start">开始巡检</el-button>
      </div>
    </el-card>

    <template v-if="detail">
      <el-row :gutter="12" style="margin-top: 14px">
        <el-col :span="4" v-for="c in cards" :key="c.label">
          <el-card shadow="never" class="kpi"><div class="k">{{ c.label }}</div><div class="v">{{ c.value }}</div></el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" style="margin-top: 14px">
        <template #header>
          <span>巡检明细 · 批次 {{ detail.run.id }}</span>
          <el-switch v-model="onlyHits" active-text="仅看命中" style="float: right" />
        </template>
        <el-empty v-if="!detail.run.scanned" description="该范围内还没有已入库申报。先到前置预审提交后再巡检。" />
        <el-table v-else :data="shown" stripe empty-text="本次没有命中" @row-click="openRow" style="cursor: pointer">
          <el-table-column prop="declNo" label="申报编号" width="150" />
          <el-table-column label="场景" width="110">
            <template #default="{ row }">{{ scenarioName(row.scenario) }}</template>
          </el-table-column>
          <el-table-column prop="subject" label="标的" min-width="180" show-overflow-tooltip />
          <el-table-column label="结论" width="110">
            <template #default="{ row }">
              <el-tag :type="tagOf(row.result)" size="small">{{ textOf(row.result) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="命中" min-width="220">
            <template #default="{ row }">
              <span v-if="!row.hits.length" style="color:#1E9E6A">未命中</span>
              <el-tag v-for="h in row.hits" :key="h.code" :type="h.severity === '红' ? 'danger' : 'warning'" size="small" style="margin-right: 4px">
                {{ h.code }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="建议修正" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ suggestText(row) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-card shadow="never" style="margin-top: 14px">
      <template #header>巡检记录</template>
      <el-table :data="runs" stripe @row-click="loadRun" style="cursor: pointer" empty-text="还没有巡检记录">
        <el-table-column prop="id" label="批次" width="80" />
        <el-table-column label="范围" width="120">
          <template #default="{ row }">{{ row.scenario === 'all' ? '全部场景' : scenarioName(row.scenario) }}</template>
        </el-table-column>
        <el-table-column prop="scanned" label="扫描" width="80" />
        <el-table-column prop="redCount" label="红" width="70" />
        <el-table-column prop="yellowCount" label="黄" width="70" />
        <el-table-column prop="passCount" label="通过" width="80" />
        <el-table-column prop="newRisks" label="新风险" width="90" />
        <el-table-column prop="createdAt" label="时间" min-width="160" />
      </el-table>
    </el-card>

    <el-dialog v-model="open" :title="active ? `巡检意见 · ${active.declNo}` : '巡检意见'" width="920px">
      <el-row v-if="active" :gutter="14">
        <el-col :span="12">
          <div class="dlg-h">命中明细</div>
          <div v-if="!active.hits.length" style="color:#1E9E6A">未命中任何风险规则。</div>
          <div v-for="h in active.hits" :key="h.code" class="hit">
            <el-tag :type="h.severity === '红' ? 'danger' : 'warning'" size="small">{{ h.severity }}</el-tag>
            <b style="margin-left: 8px">{{ h.code }} {{ h.name }}</b>
            <div>{{ h.advice }}</div>
            <div class="law"><b>依据条款：</b>{{ h.clauseRef }}</div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="dlg-h">建议修正后的内容</div>
          <div v-if="!changedRows.length" style="color:#1E9E6A">无需修正。</div>
          <div v-for="row in changedRows" :key="row.key" class="fix-row">
            <div class="fix-k">{{ row.label }}</div>
            <div class="fix-v">
              <span class="old">{{ row.before }}</span>
              <span class="neu">{{ row.after }}</span>
              <div class="why">{{ row.why }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getInspection, listInspections, runInspection } from '../api'
import { revisionRows, scenarioName } from '../revise'

const scenario = ref('all')
const running = ref(false)
const runs = ref([])
const detail = ref(null)
const onlyHits = ref(true)
const open = ref(false)
const active = ref(null)

const cards = computed(() => {
  const r = detail.value?.run || {}
  return [
    { label: '扫描份数', value: r.scanned ?? 0 },
    { label: '命中', value: r.hitDecls ?? 0 },
    { label: '红', value: r.redCount ?? 0 },
    { label: '黄', value: r.yellowCount ?? 0 },
    { label: '通过', value: r.passCount ?? 0 },
    { label: '新风险', value: r.newRisks ?? 0 }
  ]
})

const shown = computed(() => {
  const rows = detail.value?.findings || []
  const list = onlyHits.value ? rows.filter(r => r.result !== 'PASS') : rows
  const order = { RED: 0, YELLOW: 1, PASS: 2 }
  return [...list].sort((a, b) => (order[a.result] ?? 9) - (order[b.result] ?? 9))
})

const changedRows = computed(() => {
  if (!active.value) return []
  return revisionRows(active.value.scenario, active.value.form, active.value.hits).filter(r => r.changed)
})

function tagOf(result) {
  return result === 'RED' ? 'danger' : result === 'YELLOW' ? 'warning' : 'success'
}
function textOf(result) {
  return { RED: '红', YELLOW: '黄', PASS: '通过' }[result] || result
}
function suggestText(row) {
  const rows = revisionRows(row.scenario, row.form, row.hits).filter(r => r.changed)
  if (!rows.length) return row.result === 'PASS' ? '—' : '请核对条款'
  return rows.map(r => `${r.label}：${r.after}`).join('；')
}

async function refreshRuns() {
  runs.value = await listInspections()
}

async function start() {
  running.value = true
  try {
    detail.value = await runInspection(scenario.value)
    onlyHits.value = true
    await refreshRuns()
    const r = detail.value.run
    ElMessage.success(`巡检完成：扫描 ${r.scanned} 份，命中 ${r.hitDecls} 份，新风险 ${r.newRisks} 条`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '巡检失败')
  } finally {
    running.value = false
  }
}

async function loadRun(row) {
  detail.value = await getInspection(row.id)
}

function openRow(row) {
  active.value = row
  open.value = true
}

onMounted(refreshRuns)
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.kpi .k { color: #7b8aa0; font-size: 12px; }
.kpi .v { font-size: 22px; font-weight: 700; margin-top: 4px; }
.hit { border-bottom: 1px solid #f0f3f8; padding: 10px 0; }
.law { font-size: 12.5px; color: #5d6f88; background: #f6f8fb; border-radius: 6px; padding: 8px 12px; margin-top: 8px; }
.dlg-h { font-weight: 700; margin-bottom: 8px; }
.fix-row { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid #f0f3f8; font-size: 13px; }
.fix-k { color: #7b8aa0; flex: 0 0 42%; }
.fix-v { text-align: right; }
.old { color: #b0b8c4; text-decoration: line-through; margin-right: 6px; }
.neu { color: #1E9E6A; font-weight: 700; }
.why { font-size: 11.5px; color: #5d8f78; margin-top: 2px; font-weight: 400; }
</style>

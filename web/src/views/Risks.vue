<template>
  <div>
    <h2 class="pt">风险预警</h2>
    <p class="pdesc">全量扫描命中的风险事件，附条款依据与证据链；AI归因分析后经人工审批派发工单（AI提效、人定性）</p>

    <el-card shadow="never">
      <template #header>
        风险事件列表
        <el-radio-group v-model="filter" size="small" style="margin-left: 14px" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="new">待处置</el-radio-button>
          <el-radio-button value="analyzing">归因中</el-radio-button>
          <el-radio-button value="ticketed">已派单</el-radio-button>
          <el-radio-button value="closed">已闭环</el-radio-button>
        </el-radio-group>
      </template>
      <el-table :data="events" size="small">
        <el-table-column prop="eventRef" label="来源申报单" width="180" show-overflow-tooltip />
        <el-table-column prop="ruleCode" label="规则" width="80" />
        <el-table-column prop="ruleName" label="命中" min-width="130" show-overflow-tooltip />
        <el-table-column label="级别" width="70">
          <template #default="{ row }">
            <el-tag :type="row.severity === '红' ? 'danger' : 'warning'" size="small">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button v-if="row.status === 'new'" size="small" type="primary" @click="doAnalyze(row)">AI归因分析</el-button>
            <el-button v-if="row.status === 'analyzing'" size="small" type="success" @click="showAnalysis(row)">审批派单</el-button>
            <el-button size="small" @click="showDetail(row)">证据链</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="analysisDlg" title="AI归因分析 · 人工门禁" width="640px">
      <template v-if="analysis">
        <el-tag size="small" type="info">{{ analysis.model === 'deepseek' ? 'DeepSeek' : '降级模板' }}</el-tag>
        <div class="sec"><b>归因分析</b><div style="margin-top: 6px; line-height: 1.8">{{ analysis.result }}</div></div>
        <div class="sec"><b>整改建议</b><div style="margin-top: 6px; line-height: 1.8">{{ analysis.suggestion }}</div></div>
      </template>
      <template #footer>
        <el-button @click="analysisDlg = false">稍后处理</el-button>
        <el-button type="danger" plain @click="approveAnalysis(false)">驳回</el-button>
        <el-button type="primary" @click="approveAnalysis(true)">批准并派发工单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDlg" title="证据链（可回放）" width="640px">
      <div style="font-size: 13px; line-height: 2">
        触发事实快照：<pre class="dsl">{{ detail }}</pre>
      </div>
      <div class="kv">每一步操作（提交/预审/风险/归因/工单）均已写入MySQL哈希链，可在驾驶舱做全链防篡改校验。</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getRiskEvents, analyze, approveAnalyze, listAnalyze } from '../api'

const events = ref([])
const filter = ref('')
const analysisDlg = ref(false)
const analysis = ref(null)
const detailDlg = ref(false)
const detail = ref('')

onMounted(load)
async function load() { events.value = await getRiskEvents(filter.value ? { status: filter.value } : {}) }

async function doAnalyze(row) {
  analysis.value = await analyze(row.id)
  analysisDlg.value = true
  await load()
}

async function showAnalysis(row) {
  const list = await listAnalyze(row.id)
  if (!list.length) { ElMessage.info('该事件暂无分析记录，请先执行AI归因分析'); return }
  analysis.value = list[0]
  analysisDlg.value = true
}

async function approveAnalysis(approved) {
  // analysis.value.id 为当前门禁对象
  const r = await approveAnalyze(analysis.value.id, approved, '合规管理员')
  analysisDlg.value = false
  ElMessage.success(approved ? `已批准并派发工单 TK-${r.ticketId}` : '已驳回，事件退回待处置')
  await load()
}

function showDetail(row) {
  detail.value = JSON.stringify(JSON.parse(row.detail || '{}'), null, 2)
  detailDlg.value = true
}

const statusText = (s) => ({ new: '待处置', analyzing: '归因中', ticketed: '已派单', closed: '已闭环' }[s] || s)
</script>

<style scoped>
.sec { margin-top: 14px; font-size: 13.5px; }
.kv { font-size: 12px; color: #8b99ad; margin-top: 10px; }
.dsl { font-family: Menlo, Consolas, monospace; font-size: 11.5px; background: #0A1B33; color: #9fd3ff;
  border-radius: 6px; padding: 8px 10px; white-space: pre-wrap; }
</style>

<template>
  <div>
    <h2 class="pt">合规驾驶舱</h2>
    <p class="pdesc">三资监管总览：资产处置、对外担保、资产租赁分口径统计，预审结论来自意见单（演示数据）</p>

    <el-row :gutter="14" class="kpis">
      <el-col :span="4" v-for="k in kpis" :key="k.label">
        <el-card shadow="never" class="kpi">
          <div class="l">{{ k.label }}</div>
          <div class="v" :style="{ color: k.color }">{{ k.value }}<small>{{ k.unit }}</small></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14" class="kpis">
      <el-col :span="8" v-for="s in scenarioCards" :key="s.key">
        <el-card shadow="never" class="kpi">
          <div class="l">{{ s.label }}</div>
          <div class="scene">
            申报 {{ s.declarations }}
            <span class="red">红 {{ s.red }}</span>
            <span class="yellow">黄 {{ s.yellow }}</span>
            <span class="pass">通过 {{ s.pass }}</span>
            · 规则 {{ s.publishedRules }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>三场景申报预审结果（资产处置/对外担保/资产租赁）</template>
          <el-table :data="allDeclarations" size="small" max-height="360">
            <el-table-column prop="declNo" label="申报编号" width="180" show-overflow-tooltip />
            <el-table-column label="标的" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.name }}</template>
            </el-table-column>
            <el-table-column prop="scenario" label="场景" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.scenario === 'guarantee' ? 'warning' : row.scenario === 'lease' ? 'success' : 'primary'">
                  {{ scenarioText(row.scenario) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="预审结果" width="90">
              <template #default="{ row }">
                <el-tag :type="resultType(row.result)" size="small">{{ resultText(row.result) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>风险事件（最新）</template>
          <el-table :data="risks" size="small" max-height="360">
            <el-table-column prop="ruleCode" label="规则" width="70" />
            <el-table-column prop="ruleName" label="命中" min-width="110" show-overflow-tooltip />
            <el-table-column label="级别" width="70">
              <template #default="{ row }">
                <el-tag :type="row.severity === '红' ? 'danger' : 'warning'" size="small">{{ row.severity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 14px">
      <template #header>
        证据链完整性（MySQL哈希链）
        <el-button size="small" style="float: right" @click="doVerify">校验</el-button>
      </template>
      <span v-if="chain">{{ chain.valid ? '✓ 全链校验通过' : '✗ 链已破坏于 #' + chain.brokenAt }}（共 {{ chain.total }} 行留痕）</span>
      <span v-else style="color: #909399; font-size: 12px">点击"校验"验证防篡改能力</span>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOverview, getRiskEvents, getPrecheckFeed, verifyChain } from '../api'

const stats = ref({})
const allDeclarations = ref([])
const risks = ref([])
const chain = ref(null)

onMounted(async () => {
  stats.value = await getOverview()
  risks.value = (await getRiskEvents()).slice(0, 20)
  allDeclarations.value = await getPrecheckFeed()
})

const scenarioCards = computed(() => {
  const s = stats.value.scenarios || {}
  return [
    { key: 'asset', label: '资产处置' },
    { key: 'guarantee', label: '对外担保' },
    { key: 'lease', label: '资产租赁' }
  ].map(item => ({ ...item, declarations: 0, red: 0, yellow: 0, pass: 0, publishedRules: 0, ...(s[item.key] || {}) }))
})

const kpis = [
  { label: '在库政策', key: 'policies', unit: '份' },
  { label: '生效规则', key: 'publishedRules', unit: '条', color: '#2C75B8' },
  { label: '预审拦截(红)', key: 'redPrechecks', unit: '单', color: '#D64541' },
  { label: '预审提示(黄)', key: 'yellowPrechecks', unit: '单', color: '#E08A1E' },
  { label: '待处置风险', key: 'pendingRisks', unit: '起', color: '#E08A1E' },
  { label: '风险闭环率', key: 'closedRate', unit: '%', color: '#1E9E6A' }
].map(k => ({ ...k, get value() { return stats.value[k.key] ?? 0 } }))

const scenarioText = (s) => ({ asset: '资产处置', guarantee: '对外担保', lease: '资产租赁' }[s] || s)
const resultType = (r) => r === 'RED' ? 'danger' : r === 'YELLOW' ? 'warning' : 'success'
const resultText = (r) => ({ RED: '红色', YELLOW: '黄色', PASS: '通过' }[r] || '通过')

const doVerify = async () => { chain.value = await verifyChain() }
</script>

<style scoped>
.pt { font-size: 18px; margin: 0 0 4px; }
.pdesc { color: #7b8aa0; font-size: 12.5px; margin: 0 0 18px; }
.kpis { margin-bottom: 14px; }
.kpi .l { font-size: 12px; color: #7b8aa0; margin-bottom: 8px; }
.kpi .v { font-size: 24px; font-weight: 800; color: #0A1B33; }
.kpi .v small { font-size: 12px; font-weight: 500; color: #8b99ad; margin-left: 4px; }
.scene { font-size: 13px; color: #0A1B33; line-height: 1.6; }
.scene .red { color: #D64541; margin-left: 8px; }
.scene .yellow { color: #E08A1E; margin-left: 8px; }
.scene .pass { color: #1E9E6A; margin-left: 8px; }
</style>

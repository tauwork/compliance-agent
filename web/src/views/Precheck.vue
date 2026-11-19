<template>
  <div>
    <h2 class="pt">前置预审 · 三资场景申报</h2>
    <p class="pdesc">决策与交易发起前的合规预检：资产处置（资产）/ 对外担保（资金）/ 资产租赁（资源）。红色意见不阻断提交，但将生成风险事件并留痕。</p>

    <el-card shadow="never">
      <el-tabs v-model="tab">
        <!-- ============ 场景B：资产处置 ============ -->
        <el-tab-pane label="资产处置（资产）" name="asset">
          <el-form :model="assetForm" label-width="150px" style="max-width: 940px">
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="资产名称"><el-input v-model="assetForm.assetName" /></el-form-item></el-col>
              <el-col :span="8">
                <el-form-item label="资产类别">
                  <el-select v-model="assetForm.assetCategory"><el-option label="房产" value="房产"/><el-option label="土地" value="土地"/><el-option label="股权" value="股权"/><el-option label="设备" value="设备"/></el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8"><el-form-item label="账面价值（万元）"><el-input-number v-model="assetForm.bookValue" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="评估值（万元）"><el-input-number v-model="assetForm.appraisedValue" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="评估基准日"><el-date-picker v-model="assetForm.assessmentDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8">
                <el-form-item label="处置方式">
                  <el-select v-model="assetForm.disposalMode"><el-option label="进场挂牌转让" value="进场挂牌转让"/><el-option label="非公开协议转让" value="非公开协议转让"/></el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="豁免情形">
                  <el-select v-model="assetForm.exemption"><el-option :value="false" label="无"/><el-option :value="true" label="符合国家规定非公开协议情形"/></el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8"><el-form-item label="决策决议编号"><el-input v-model="assetForm.resolutionNo" placeholder="党委会/董事会决议编号" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="拟挂牌价（万元）"><el-input-number v-model="assetForm.listingPrice" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="成交价（万元）"><el-input-number v-model="assetForm.dealPrice" :min="0" style="width:100%" /></el-form-item></el-col>
            </el-row>
          </el-form>
          <el-button type="primary" :loading="loading" @click="submitAsset">提交并预审</el-button>
        </el-tab-pane>

        <!-- ============ 场景A：对外担保 ============ -->
        <el-tab-pane label="对外担保（资金）" name="guarantee">
          <el-form :model="guaForm" label-width="170px" style="max-width: 940px">
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="被担保方"><el-input v-model="guaForm.guaranteeee" /></el-form-item></el-col>
              <el-col :span="8">
                <el-form-item label="担保类型">
                  <el-select v-model="guaForm.guaranteeType"><el-option label="融资担保" value="融资担保"/><el-option label="履约担保" value="履约担保"/></el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8"><el-form-item label="本次担保金额（万元）"><el-input-number v-model="guaForm.guaranteeAmount" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8">
                <el-form-item label="担保余额占净资产比例">
                  <el-input-number v-model="guaForm.balanceRatio" :min="0" :max="5" :step="0.05" :precision="2" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="被担保方评级">
                  <el-select v-model="guaForm.guaranteeeeRating">
                    <el-option label="AAA" value="AAA"/><el-option label="AA" value="AA"/><el-option label="BBB" value="BBB"/><el-option label="BB以下" value="BB以下"/><el-option label="未评级" value="未评级"/>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="是否本集团控股子企业">
                  <el-select v-model="guaForm.isHoldingSub"><el-option :value="true" label="是"/><el-option :value="false" label="否"/></el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="是否落实反担保">
                  <el-select v-model="guaForm.counterGuarantee"><el-option :value="true" label="已落实"/><el-option :value="false" label="未落实"/></el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8"><el-form-item label="决策决议编号"><el-input v-model="guaForm.resolutionNo" placeholder="党委会/董事会决议编号" /></el-form-item></el-col>
            </el-row>
          </el-form>
          <el-button type="primary" :loading="loading" @click="submitGua">提交并预审</el-button>
        </el-tab-pane>

        <!-- ============ 场景C：资产租赁 ============ -->
        <el-tab-pane label="资产租赁（资源）" name="lease">
          <el-form :model="leaseForm" label-width="170px" style="max-width: 940px">
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="租赁资产名称"><el-input v-model="leaseForm.assetName" /></el-form-item></el-col>
              <el-col :span="8">
                <el-form-item label="资产类型">
                  <el-select v-model="leaseForm.assetType"><el-option label="商铺" value="商铺"/><el-option label="仓库" value="仓库"/><el-option label="土地" value="土地"/><el-option label="广告位" value="广告位"/><el-option label="车位" value="车位"/></el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="定价方式">
                  <el-select v-model="leaseForm.pricingMode"><el-option label="公开竞价" value="公开竞价"/><el-option label="协议定价" value="协议定价"/></el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8"><el-form-item label="租期（月）"><el-input-number v-model="leaseForm.leaseTermMonths" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="租金（万元/月）"><el-input-number v-model="leaseForm.monthlyRent" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="市场参考租金（万元/月）"><el-input-number v-model="leaseForm.marketRentRef" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8">
                <el-form-item label="是否到期续签">
                  <el-select v-model="leaseForm.isRenewal"><el-option :value="true" label="是"/><el-option :value="false" label="否"/></el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8"><el-form-item label="决策决议编号"><el-input v-model="leaseForm.resolutionNo" placeholder="董事会决议编号" /></el-form-item></el-col>
            </el-row>
          </el-form>
          <el-button type="primary" :loading="loading" @click="onSubmitLease">提交并预审</el-button>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-row v-if="opinion" ref="resultRow" :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never" :class="opCard" class="result-card">
          <template #header>
            <span>预审意见单 · {{ declNo }}</span>
            <el-tag :type="opTag" style="margin-left: 10px">{{ opText }}</el-tag>
          </template>
          <div v-if="!hits.length" style="color: #1E9E6A">✓ 未命中任何风险规则，可进入后续审批流程。本意见单已随申报单快照留痕（哈希链）。</div>
          <div v-for="h in hits" :key="h.code" class="hit">
            <div style="margin-bottom: 6px">
              <el-tag :type="h.severity === '红' ? 'danger' : 'warning'" size="small">{{ h.severity }}</el-tag>
              <b style="margin-left: 8px">{{ h.code }} {{ h.name }}</b>
            </div>
            <div>{{ h.advice }}</div>
            <div class="law"><b>依据条款：</b>{{ h.clauseRef }}</div>
          </div>
          <el-alert v-if="hits.some(h => h.severity === '红')" type="error" :closable="false"
                    title="带病提交将生成风险事件并全程留痕，进入整改闭环流程" style="margin-top: 10px" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="result-card">
          <template #header>
            <span>建议修正后的内容</span>
            <el-button v-if="changeCount" size="small" type="primary" style="float: right" @click="applyRevision">回填到申报单</el-button>
          </template>
          <div v-if="!changeCount" style="color: #1E9E6A">当前申报已满足已发布规则，无需修正。</div>
          <p v-else class="hint">按本次命中规则整理的修正稿。绿色为相对原申报的调整，回填后可再次提交预审。修正稿不替代人工决策。</p>
          <div v-for="row in revisedRows" :key="row.key" class="fix-row" :class="{ changed: row.changed }">
            <div class="fix-k">{{ row.label }}</div>
            <div class="fix-v">
              <span v-if="row.changed" class="old">{{ row.before }}</span>
              <span :class="{ neu: row.changed }">{{ row.after }}</span>
              <div v-if="row.changed" class="why">{{ row.why }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { submitPrecheck, submitGuarantee, submitLease as postLease } from '../api'
import { FIELDS, reviseForm, showValue } from '../revise'

const tab = ref('asset')
const loading = ref(false)
const opinion = ref(null)
const hits = ref([])
const declNo = ref('')
const submitted = ref(null)
const resultRow = ref(null)

const assetForm = ref({
  assetName: '福田区某办公楼3-4层（演示）', assetCategory: '房产', bookValue: 680,
  appraisedValue: 720, assessmentDate: '2025-08-20', disposalMode: '非公开协议转让',
  exemption: false, resolutionNo: '', listingPrice: 650, dealPrice: null
})
const guaForm = ref({
  guaranteeee: '某某科技（深圳）有限公司（演示）', guaranteeType: '融资担保', guaranteeAmount: 800,
  balanceRatio: 0.62, guaranteeeeRating: '未评级', isHoldingSub: false,
  counterGuarantee: false, resolutionNo: ''
})
const leaseForm = ref({
  assetName: '南山某临街商铺（演示）', assetType: '商铺', pricingMode: '协议定价',
  leaseTermMonths: 84, monthlyRent: 2.8, marketRentRef: 4.2, isRenewal: true, resolutionNo: ''
})

async function run(fn) {
  loading.value = true
  try {
    const r = await fn()
    declNo.value = r.declaration.declNo
    opinion.value = r.opinion
    hits.value = JSON.parse(r.opinion.hits || '[]')
    submitted.value = { scenario: tab.value, form: JSON.parse(JSON.stringify(currentForm())) }
    ElMessage[r.opinion.result === 'PASS' ? 'success' : r.opinion.result === 'RED' ? 'error' : 'warning'](
      `预审完成：${r.opinion.result === 'PASS' ? '通过' : r.opinion.result === 'RED' ? '红色拦截提示' : '黄色整改提示'}（${hits.value.length}条命中）`)
    await nextTick()
    resultRow.value?.$el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '提交失败')
  } finally {
    loading.value = false
  }
}

function currentForm() {
  if (tab.value === 'guarantee') return guaForm.value
  if (tab.value === 'lease') return leaseForm.value
  return assetForm.value
}

const revision = computed(() => {
  if (!submitted.value) return { next: {}, reasons: {} }
  return reviseForm(submitted.value.scenario, submitted.value.form, hits.value)
})
const revisedRows = computed(() => {
  if (!submitted.value) return []
  const { scenario, form } = submitted.value
  const { next, reasons } = revision.value
  return (FIELDS[scenario] || []).map(([key, label]) => ({
    key, label,
    before: showValue(key, form[key]),
    after: showValue(key, next[key]),
    changed: !!reasons[key],
    why: reasons[key] || ''
  }))
})
const changeCount = computed(() => revisedRows.value.filter(r => r.changed).length)

function applyRevision() {
  if (!submitted.value) return
  const target = submitted.value.scenario === 'guarantee' ? guaForm : submitted.value.scenario === 'lease' ? leaseForm : assetForm
  Object.assign(target.value, JSON.parse(JSON.stringify(revision.value.next)))
  tab.value = submitted.value.scenario
  ElMessage.success('已按建议回填申报单，可再次提交预审')
}

const submitAsset = () => {
  if (!assetForm.value.assetName) { ElMessage.warning('资产名称必填'); return }
  run(() => submitPrecheck(assetForm.value))
}
const submitGua = () => {
  if (!guaForm.value.guaranteeee) { ElMessage.warning('被担保方必填'); return }
  run(() => submitGuarantee(guaForm.value))
}
const onSubmitLease = () => {
  if (!leaseForm.value.assetName) { ElMessage.warning('租赁资产名称必填'); return }
  run(() => postLease(leaseForm.value))
}

const opTag = computed(() => opinion.value?.result === 'RED' ? 'danger' : opinion.value?.result === 'YELLOW' ? 'warning' : 'success')
const opText = computed(() => ({ RED: '红色 · 拦截提示', YELLOW: '黄色 · 整改提示', PASS: '通过' }[opinion.value?.result]))
const opCard = computed(() => opinion.value?.result === 'RED' ? 'op-red' : opinion.value?.result === 'YELLOW' ? 'op-yellow' : 'op-pass')
</script>

<style scoped>
.hit { border-bottom: 1px solid #f0f3f8; padding: 12px 0; }
.hit:last-of-type { border-bottom: none; }
.law { font-size: 12.5px; color: #5d6f88; background: #f6f8fb; border-radius: 6px; padding: 8px 12px; margin-top: 8px; line-height: 1.7; }
.op-red :deep(.el-card__header) { background: #fdeceb; }
.op-yellow :deep(.el-card__header) { background: #fdf3e3; }
.op-pass :deep(.el-card__header) { background: #e8f7f0; }
.result-card { min-height: 100%; }
.hint { color: #7b8aa0; font-size: 12.5px; margin: 0 0 8px; line-height: 1.6; }
.fix-row { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid #f0f3f8; font-size: 13px; }
.fix-row:last-child { border-bottom: none; }
.fix-row.changed { background: #e8f7f0; margin: 0 -12px; padding: 8px 12px; border-radius: 6px; border-bottom-color: transparent; }
.fix-k { color: #7b8aa0; flex: 0 0 42%; }
.fix-v { text-align: right; }
.old { color: #b0b8c4; text-decoration: line-through; margin-right: 6px; }
.neu { color: #1E9E6A; font-weight: 700; }
.why { font-size: 11.5px; color: #5d8f78; margin-top: 2px; font-weight: 400; }
</style>

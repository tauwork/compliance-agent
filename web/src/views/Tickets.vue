<template>
  <div>
    <h2 class="pt">整改闭环</h2>
    <p class="pdesc">问题单工单化流转：派发 → 执行 → 复核 → 关闭，全程留痕；复核通过自动联动风险事件闭环</p>

    <el-row :gutter="12">
      <el-col v-for="lane in lanes" :key="lane.key" :span="6">
        <el-card shadow="never" class="lane">
          <template #header>{{ lane.title }}（{{ lane.items.length }}）</template>
          <div v-for="t in lane.items" :key="t.id" class="tk">
            <b>TK-{{ t.id }} {{ t.title }}</b>
            <div class="kv">来源：{{ t.riskEventId ? '风险事件 #' + t.riskEventId : '手工创建' }}</div>
            <div v-if="t.suggestion" class="kv" style="color: #2C75B8">AI建议：{{ t.suggestion.slice(0, 40) }}…</div>
            <div class="kv">状态：{{ t.status }}<span v-if="t.assignee"> ｜ {{ t.assignee }}</span></div>
            <div style="margin-top: 8px; display: flex; gap: 6px; flex-wrap: wrap">
              <el-button v-if="t.status === 'created'" size="small" type="primary"
                         @click="move(t, 'assign')">派发</el-button>
              <el-button v-if="t.status === 'assigned'" size="small" type="primary"
                         @click="move(t, 'execute')">开始执行</el-button>
              <el-button v-if="t.status === 'executing'" size="small" type="primary"
                         @click="move(t, 'resolve')">提交复核</el-button>
              <el-button v-if="t.status === 'resolved'" size="small" type="success"
                         @click="move(t, 'verify')">复核通过</el-button>
              <el-button v-if="t.status === 'verified'" size="small" type="success"
                         @click="move(t, 'close')">关闭归档</el-button>
              <el-button v-if="['created','assigned','executing','resolved'].includes(t.status)"
                         size="small" type="danger" plain @click="move(t, 'reject')">驳回</el-button>
            </div>
          </div>
          <el-empty v-if="!lane.items.length" :image-size="40" description=" " />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 14px">
      <template #header>闭环统计</template>
      <div class="kv">工单总数 {{ stats.total ?? '—' }} ｜ 已闭环 {{ stats.closed ?? '—' }} ｜ 待处理 {{ stats.pending ?? '—' }} ｜ 闭环率 {{ stats.closedRate ?? '—' }}%</div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getTickets, ticketTransition, getTicketStats } from '../api'

const tickets = ref([])
const stats = ref({})

onMounted(load)
async function load() {
  tickets.value = await getTickets()
  stats.value = await getTicketStats()
}

async function move(t, action) {
  try {
    await ticketTransition(t.id, action, '合规管理员', '界面操作')
    ElMessage.success(`TK-${t.id} 已${actionText(action)}`)
    await load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '流转失败')
  }
}

const actionText = (a) => ({
  assign: '派发', execute: '开始执行', resolve: '提交复核',
  verify: '复核通过（风险事件联动闭环）', close: '关闭归档', reject: '驳回'
}[a])

const lanes = computed(() => [
  { key: 'created', title: '待派发', items: tickets.value.filter(t => t.status === 'created') },
  { key: 'assigned', title: '执行中', items: tickets.value.filter(t => ['assigned', 'executing'].includes(t.status)) },
  { key: 'resolved', title: '待复核', items: tickets.value.filter(t => t.status === 'resolved') },
  { key: 'closed', title: '已闭环', items: tickets.value.filter(t => ['verified', 'rejected', 'closed'].includes(t.status)) }
])
</script>

<style scoped>
.lane { background: #f6f8fb; }
.lane :deep(.el-card__header) { padding: 10px 14px; font-size: 13px; font-weight: 700; color: #5d6f88; }
.tk { background: #fff; border: 1px solid #e4e9f1; border-radius: 6px; padding: 10px 12px; margin-bottom: 8px; font-size: 12.5px; }
.kv { font-size: 11.5px; color: #8b99ad; margin-top: 4px; }
</style>

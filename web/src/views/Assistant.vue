<template>
  <div style="max-width: 860px">
    <h2 class="pt">对话式合规助手</h2>
    <p class="pdesc">政策条款级问答，答案可溯源到原文条号；不替代合规定性，复杂问题转合规部门人工确认</p>

    <el-card shadow="never">
      <div class="chat">
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="av">{{ m.role === 'user' ? '问' : '答' }}</div>
          <div class="bb">
            <div style="line-height: 1.8">{{ m.text }}</div>
            <div v-if="m.sources?.length" class="src">
              <b>依据条款：</b>
              <div v-for="(s, j) in m.sources" :key="j" style="margin-top: 4px">
                《{{ s.policy }}》{{ s.clauseNo }}：{{ s.content.slice(0, 60) }}{{ s.content.length > 60 ? '…' : '' }}
              </div>
            </div>
            <div v-if="m.mode" class="mode">{{ m.mode === 'deepseek' ? 'DeepSeek生成' : '条款原文直接作答（降级模式）' }}</div>
          </div>
        </div>
      </div>

      <div style="display: flex; gap: 10px; margin-top: 16px">
        <el-input v-model="question" placeholder="例如：资产转让什么情况下可以不进场交易？" @keyup.enter="ask" />
        <el-button type="primary" :loading="loading" @click="ask">提问</el-button>
      </div>
      <div style="margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap">
        <el-button v-for="q in quicks" :key="q" size="small" @click="ask(q)">{{ q }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { chat } from '../api'

const messages = ref([
  { role: 'assistant', text: '您好，我是政策合规助手。可就三资政策条款提问，回答将附依据条款原文。' }
])
const question = ref('')
const loading = ref(false)
const quicks = ['资产转让什么情况下可以不进场交易？', '评估报告能用多久？', '挂牌价可以低于评估值吗？', '处置立项需要哪些决策要件？']

async function ask(q) {
  const text = q || question.value
  if (!text) return
  question.value = ''
  messages.value.push({ role: 'user', text })
  loading.value = true
  try {
    const r = await chat(text)
    messages.value.push({ role: 'assistant', text: r.answer, sources: r.sources, mode: r.mode })
  } catch (e) {
    messages.value.push({ role: 'assistant', text: '查询失败：' + (e.response?.data?.message || e.message) })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.chat { max-height: 460px; overflow-y: auto; }
.msg { margin-bottom: 16px; display: flex; gap: 10px; }
.msg .av { width: 34px; height: 34px; border-radius: 50%; flex-shrink: 0; display: flex;
  align-items: center; justify-content: center; font-size: 13px; font-weight: 700; }
.msg.user .av { background: #0A1B33; color: #fff; }
.msg.assistant .av { background: #eaf2fc; color: #2C75B8; }
.msg .bb { background: #fff; border: 1px solid #e4e9f1; border-radius: 0 8px 8px 8px;
  padding: 12px 16px; font-size: 13.5px; max-width: 700px; }
.msg.user .bb { background: #eaf2fc; border-color: #d4e5f7; }
.src { margin-top: 10px; background: #f6f8fb; border-left: 3px solid #2C75B8;
  padding: 8px 12px; font-size: 12.5px; color: #5d6f88; border-radius: 0 6px 6px 0; }
.mode { margin-top: 8px; font-size: 11px; color: #9aa7b8; }
</style>

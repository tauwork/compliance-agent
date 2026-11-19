<template>
  <el-empty v-if="denied" description="当前角色不能查看这份政策所在的知识库">
    <el-button type="primary" @click="$router.push('/policies')">返回政策法规库</el-button>
  </el-empty>
  <div v-else-if="policy">
    <div class="head">
      <div>
        <el-button link @click="$router.push('/policies')">返回政策法规库</el-button>
        <h2 class="pt">{{ policy.title }}</h2>
        <p class="pdesc">{{ policy.docNo || '—' }} · {{ policy.issuer }} · 生效 {{ policy.effectDate || '—' }}</p>
      </div>
      <div class="head-side">
        <el-tag :type="policy.validity === 'expired' ? 'info' : 'success'">{{ policy.validity === 'expired' ? '过期政策' : '有效政策' }}</el-tag>
        <el-date-picker v-model="expireDate" type="date" value-format="YYYY-MM-DD" placeholder="失效日期，空为长期有效"
                        @change="saveExpire" />
      </div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane :label="`分片 ${chunks.length}`" name="chunks">
        <div class="bar">
          <span class="hint">按「第X条」切开。改完点保存，重新分片会覆盖现有切片。</span>
          <el-button size="small" :loading="busy" @click="rebuild">重新分片</el-button>
        </div>
        <el-card v-for="c in chunks" :key="c.id" shadow="never" class="block">
          <div class="chunk-h">
            <b>#{{ c.seq }} {{ c.heading }}</b>
            <span>{{ c.charCount }} 字</span>
          </div>
          <el-input v-model="c.content" type="textarea" :rows="4" />
          <div class="ops">
            <el-button size="small" type="primary" @click="save(c)">保存</el-button>
            <el-button size="small" type="danger" plain @click="removeChunk(c)">删除</el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="`问答 ${qas.length}`" name="qa">
        <div class="bar">
          <span class="hint">问答从分片生成，助手回答时会一并检索。人工录入的问答在重新生成时保留。</span>
          <div>
            <el-button size="small" @click="qaDlg = true">新增问答</el-button>
            <el-button size="small" type="primary" :loading="busy" @click="generate(false)">生成问答对</el-button>
            <el-button size="small" :loading="busy" @click="generate(true)">重新生成</el-button>
          </div>
        </div>
        <el-empty v-if="!qas.length" description="还没有问答。可以按分片生成，也可以手工新增。" />
        <el-card v-for="q in qas" :key="q.id" shadow="never" class="block">
          <div class="chunk-h">
            <el-tag size="small" :type="q.source === 'human' ? 'warning' : 'success'">{{ q.source === 'human' ? '人工' : '由分片生成' }}</el-tag>
            <el-button link type="danger" @click="removeQa(q)">删除</el-button>
          </div>
          <div class="q">问：{{ q.question }}</div>
          <div class="a">答：{{ q.answer }}</div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="`规则 ${rules.length}`" name="rules">
        <div class="bar">
          <span class="hint">规则拆解保持原来的流水线。发布后进入 AI 规则库，并参与预审和巡检。</span>
          <el-button size="small" type="primary" :loading="decomposing" @click="runDecompose">发起拆解</el-button>
        </div>
        <el-table :data="rules" stripe empty-text="还没有规则。发起拆解后会出现在这里。">
          <el-table-column prop="code" label="编号" width="120" />
          <el-table-column prop="name" label="规则" min-width="180" />
          <el-table-column prop="severity" label="级别" width="80" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ { draft: '草稿', published: '已发布', rejected: '已驳回', trial: '试跑' }[row.status] || row.status }}</template>
          </el-table-column>
          <el-table-column prop="clauseRef" label="依据" min-width="180" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="qaDlg" title="新增问答" width="520px">
      <el-form label-width="60px">
        <el-form-item label="问题"><el-input v-model="qaForm.question" /></el-form-item>
        <el-form-item label="答案"><el-input v-model="qaForm.answer" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qaDlg = false">取消</el-button>
        <el-button type="primary" @click="addQa">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createQa, deleteChunk, deleteQa, decompose, generateQas, getChunks, getPolicy, getPolicyRules, getQas,
  rebuildChunks, saveChunk, updatePolicy
} from '../api'
import { session } from '../session'

const route = useRoute()
const denied = ref(false)
const policy = ref(null)
const expireDate = ref(null)
const tab = ref('chunks')
const chunks = ref([])
const qas = ref([])
const rules = ref([])
const busy = ref(false)
const decomposing = ref(false)
const qaDlg = ref(false)
const qaForm = ref({ question: '', answer: '' })

const id = () => route.params.id

onMounted(load)
async function load() {
  denied.value = false
  policy.value = await getPolicy(id())
  if (!session.libraryIds.map(Number).includes(Number(policy.value.libraryId))) {
    denied.value = true
    policy.value = null
    return
  }
  expireDate.value = policy.value.expireDate
  chunks.value = await getChunks(id())
  qas.value = await getQas(id())
  rules.value = await getPolicyRules(id())
}
async function saveExpire() {
  policy.value = await updatePolicy(id(), { expireDate: expireDate.value, libraryId: policy.value.libraryId })
  ElMessage.success(policy.value.validity === 'expired' ? '已标为过期政策' : '已标为有效政策')
}
async function rebuild() {
  try {
    await ElMessageBox.confirm('重新分片会覆盖当前切片，已关联的问答会断开分片。', '重新分片')
  } catch { return }
  busy.value = true
  try {
    chunks.value = await rebuildChunks(id())
    ElMessage.success(`已分成 ${chunks.value.length} 片`)
  } finally { busy.value = false }
}
async function save(c) {
  const saved = await saveChunk(id(), c.id, { content: c.content })
  Object.assign(c, saved)
  ElMessage.success('分片已保存')
}
async function removeChunk(c) {
  await deleteChunk(id(), c.id)
  chunks.value = chunks.value.filter(x => x.id !== c.id)
}
async function generate(replace) {
  busy.value = true
  try {
    qas.value = await generateQas(id(), replace)
    ElMessage.success(`问答 ${qas.value.length} 条`)
  } finally { busy.value = false }
}
async function addQa() {
  if (!qaForm.value.question || !qaForm.value.answer) return
  await createQa(id(), qaForm.value)
  qaDlg.value = false
  qaForm.value = { question: '', answer: '' }
  qas.value = await getQas(id())
}
async function removeQa(q) {
  await deleteQa(id(), q.id)
  qas.value = qas.value.filter(x => x.id !== q.id)
}
async function runDecompose() {
  decomposing.value = true
  try {
    const r = await decompose(id())
    rules.value = await getPolicyRules(id())
    policy.value = await getPolicy(id())
    ElMessage.success(`拆解完成（${r.mode === 'deepseek' ? 'DeepSeek' : '降级模式'}）：规则草稿 ${r.ruleDrafts} 条`)
    tab.value = 'rules'
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '拆解失败')
  } finally { decomposing.value = false }
}
</script>

<style scoped>
.head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.head-side { display: flex; align-items: center; gap: 10px; }
.bar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 12px; }
.hint { color: #7b8aa0; font-size: 12.5px; }
.block { margin-bottom: 10px; }
.chunk-h { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 13px; }
.ops { margin-top: 8px; }
.q { font-weight: 700; margin-bottom: 6px; }
.a { color: #3d4d63; line-height: 1.7; white-space: pre-wrap; }
</style>

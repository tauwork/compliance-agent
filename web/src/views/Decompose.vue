<template>
  <div v-if="!view.policy">
    <h2 class="pt">政策拆解工作台</h2>
    <p class="pdesc">政策拆解五步法：全文结构化 → 条款要素抽取 → 义务清单 → 控制点映射 → 规则DSL草稿；发布须人工门禁审批</p>
    <el-card shadow="never">
      <el-empty description="请从「政策法规库」选择政策进入拆解工作台">
        <el-button type="primary" @click="$router.push('/policies')">前往政策法规库</el-button>
      </el-empty>
    </el-card>
  </div>
  <div v-else>
    <h2 class="pt">政策拆解工作台</h2>
    <p class="pdesc">《{{ view.policy.title }}》 · 五级视图：条款 → 义务 → 控制点 → 规则草稿（AI产出与人工修正全程留痕）</p>

    <el-row :gutter="14">
      <el-col :span="6">
        <el-card shadow="never">
          <template #header>条款目录（{{ view.clauses.length }}）</template>
          <div v-for="cv in view.clauses" :key="cv.clause.id"
               class="cnode" :class="{ on: current?.clause.id === cv.clause.id }"
               @click="current = cv">
            <div style="display:flex; justify-content:space-between; align-items:center">
              <b>{{ cv.clause.clauseNo }}</b>
              <el-tag size="small" type="info">{{ typeText(cv.clause.clauseType) }}</el-tag>
            </div>
            <div class="ctext">{{ cv.clause.content }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card v-if="current" shadow="never">
          <template #header>
            <el-tag size="small" type="warning">条款原文</el-tag>
            <b style="margin-left: 8px">{{ current.clause.clauseNo }}</b>
          </template>
          <div style="line-height: 1.9">{{ current.clause.content }}</div>
        </el-card>

        <el-card v-if="current" shadow="never" style="margin-top: 14px">
          <template #header>AI拆解产出（可人工修正）</template>
          <div v-for="o in current.obligations" :key="o.id" class="li">
            <div style="margin-bottom: 6px">
              <el-tag size="small" :type="o.revisedBy === 'human' ? 'warning' : 'success'">
                {{ o.revisedBy === 'human' ? '人工已修正' : 'AI产出' }}
              </el-tag>
              <b style="margin-left: 6px">义务</b>
            </div>
            <div>{{ o.description }}</div>
            <div class="kv">责任部门：{{ o.dept }} ｜ 量化阈值：{{ o.threshold || '—' }} ｜ 时限：{{ o.deadline || '—' }}</div>
            <el-button size="small" style="margin-top: 6px" @click="editObligation(o)">修正义务</el-button>
          </div>
          <div v-if="currentRules.length" style="margin-top: 12px">
            <div v-for="r in currentRules" :key="r.id" class="li">
              <div style="margin-bottom: 6px">
                <el-tag size="small" :type="r.severity === '红' ? 'danger' : 'warning'">{{ r.severity }}</el-tag>
                <b style="margin-left: 6px">{{ r.name }}</b>
                <el-tag size="small" style="margin-left: 8px">{{ r.status === 'published' ? '已发布' : r.status === 'rejected' ? '已驳回' : '草稿' }}</el-tag>
              </div>
              <div class="kv">依据：{{ r.clauseRef }}</div>
              <pre class="dsl">{{ r.dsl }}</pre>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="never">
          <template #header>人工门禁</template>
          <p style="font-size: 12.5px; color: #5d6f88; line-height: 1.8; margin: 0 0 12px">
            规则草稿经人工审批后方可发布生效。审批动作全程留痕（哈希链）。
          </p>
          <div style="display: flex; gap: 10px">
            <el-button type="primary" @click="approve(true)">✓ 通过并发布全部草稿</el-button>
            <el-button type="danger" plain @click="approve(false)">驳回</el-button>
          </div>
          <el-divider />
          <div class="kv">草稿数：{{ drafts.length }} ｜ 已发布：{{ drafts.filter(d => d.status === 'published').length }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="editDlg" title="人工修正义务" width="560px">
      <el-form label-width="90px">
        <el-form-item label="义务描述"><el-input v-model="editForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="责任部门"><el-input v-model="editForm.dept" /></el-form-item>
        <el-form-item label="量化阈值"><el-input v-model="editForm.threshold" /></el-form-item>
        <el-form-item label="时限"><el-input v-model="editForm.deadline" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDlg = false">取消</el-button>
        <el-button type="primary" @click="saveObligation">保存修正</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDecomposeView, reviseObligation, approveDecompose } from '../api'

const route = useRoute()
const view = ref({ policy: null, clauses: [], ruleDrafts: [] })
const current = ref(null)
const editDlg = ref(false)
const editForm = ref({})

const drafts = computed(() => view.value.ruleDrafts.filter(r => r.code?.startsWith('D-')))
const currentRules = computed(() =>
  view.value.ruleDrafts.filter(r => current.value && r.clauseId === current.value.clause.id))

onMounted(load)
watch(() => route.query.policyId, load)

async function load() {
  const id = route.query.policyId
  if (!id) return
  view.value = await getDecomposeView(id)
  current.value = view.value.clauses[0] || null
}

function editObligation(o) {
  editForm.value = { ...o }
  editDlg.value = true
}

async function saveObligation() {
  await reviseObligation(editForm.value.id, editForm.value)
  editDlg.value = false
  ElMessage.success('已修正（标记：人工）')
  await load()
}

async function approve(approved) {
  const pending = drafts.value.filter(d => d.status === 'draft')
  if (!pending.length) { ElMessage.info('没有待审批的草稿'); return }
  try {
    await ElMessageBox.confirm(
      `${approved ? '发布' : '驳回'} ${pending.length} 条规则草稿？${approved ? '发布后即时生效参与预审与扫描。' : ''}`,
      '人工门禁审批', { confirmButtonText: approved ? '通过并发布' : '确认驳回' })
  } catch { return }
  const r = await approveDecompose(view.value.policy.id, approved, '拆解工作台审批')
  ElMessage.success(`已处理 ${r.rulesHandled} 条草稿（发布 ${r.published} 条），动作已留痕`)
  await load()
}

const typeText = (t) => ({ duty: '义务', forbid: '禁止', procedure: '程序', authorize: '授权' }[t] || t)
</script>

<style scoped>
.cnode { padding: 10px; border-radius: 6px; cursor: pointer; margin-bottom: 6px; }
.cnode:hover { background: #f2f6fb; }
.cnode.on { background: #eaf2fc; }
.ctext { font-size: 12px; color: #7b8aa0; margin-top: 4px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.li { border: 1px solid #e8edf4; border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; font-size: 13px; line-height: 1.7; }
.kv { font-size: 12px; color: #8b99ad; margin-top: 6px; }
.dsl { font-family: Menlo, Consolas, monospace; font-size: 11.5px; background: #0A1B33; color: #9fd3ff;
  border-radius: 6px; padding: 8px 10px; margin: 8px 0 0; white-space: pre-wrap; word-break: break-all; }
</style>

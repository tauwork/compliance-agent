<template>
  <div>
    <h2 class="pt">政策法规库</h2>
    <p class="pdesc">三资相关政策统一入库：上传后自动触发拆解流水线（条款→义务→控制点→规则草稿，发布须人工审批）</p>

    <el-button type="primary" style="margin-bottom: 14px" @click="dlg = true">＋ 上传政策文本</el-button>

    <el-card v-for="p in policies" :key="p.id" shadow="never" style="margin-bottom: 10px">
      <div style="display: flex; align-items: center; gap: 14px">
        <div class="fico">{{ p.title.includes('担保') ? '金' : '资' }}</div>
        <div style="flex: 1; min-width: 0">
          <b>{{ p.title }}</b>
          <div class="meta">{{ p.docNo || '—' }} · {{ p.issuer }} · 生效 {{ p.effectDate || '—' }} · {{ p.version }}</div>
        </div>
        <el-tag :type="statusType(p.status)" size="small">{{ statusText(p.status) }}</el-tag>
        <el-button size="small" @click="toWorkbench(p.id)">查看拆解</el-button>
        <el-button size="small" type="primary" :loading="decomposing === p.id"
                   @click="doDecompose(p.id)">发起拆解</el-button>
      </div>
    </el-card>

    <el-dialog v-model="dlg" title="上传政策文本" width="640px">
      <el-form label-width="90px">
        <el-form-item label="政策标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="文号"><el-input v-model="form.docNo" placeholder="如：演示〔2026〕3号" /></el-form-item>
        <el-form-item label="发文机关"><el-input v-model="form.issuer" /></el-form-item>
        <el-form-item label="生效日期"><el-date-picker v-model="form.effectDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="政策全文">
          <el-input v-model="form.sourceText" type="textarea" :rows="10"
                    placeholder="粘贴含『第X条』结构的政策全文，拆解流水线将按条款切分并抽取义务与规则草稿" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="upload">上传并入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPolicies, createPolicy, decompose } from '../api'

const router = useRouter()
const policies = ref([])
const dlg = ref(false)
const decomposing = ref(null)
const form = ref({ title: '', docNo: '', issuer: '', effectDate: '', sourceText: '' })

onMounted(load)
async function load() { policies.value = await getPolicies() }

async function upload() {
  if (!form.value.title || !form.value.sourceText) {
    ElMessage.warning('标题与全文必填'); return
  }
  const p = await createPolicy(form.value)
  dlg.value = false
  form.value = { title: '', docNo: '', issuer: '', effectDate: '', sourceText: '' }
  await load()
  ElMessage.success(`已入库：${p.title}（id=${p.id}），可发起拆解`)
}

async function doDecompose(id) {
  decomposing.value = id
  try {
    const r = await decompose(id)
    ElMessage.success(`拆解完成（${r.mode === 'deepseek' ? 'DeepSeek' : '降级模式'}）：条款${r.clauses} 义务${r.obligations} 规则草稿${r.ruleDrafts}，待人工门禁审批`)
    await load()
    router.push(`/decompose?policyId=${id}`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '拆解失败')
  } finally {
    decomposing.value = null
  }
}

const toWorkbench = (id) => router.push(`/decompose?policyId=${id}`)
const statusType = (s) => s === 'decomposed' ? 'success' : s === 'decomposing' ? 'primary' : 'info'
const statusText = (s) => s === 'decomposed' ? '已拆解' : s === 'decomposing' ? '拆解中' : '已入库 · 待拆解'
</script>

<style scoped>
.fico { width: 42px; height: 42px; border-radius: 8px; background: #eaf2fc; color: #2C75B8;
  display: flex; align-items: center; justify-content: center; font-size: 19px; font-weight: 800; flex-shrink: 0; }
.meta { font-size: 12px; color: #8b99ad; margin-top: 4px; }
</style>

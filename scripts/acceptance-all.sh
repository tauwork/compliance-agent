#!/bin/bash
# 政策合规智能体 MVP · M1+M2+M3 全链路验收（对应开发方案1.3节演示剧本）
# 用法: sh scripts/acceptance-all.sh [BASE_URL]   （无DEEPSEEK_API_KEY时LLM走降级路径）
BASE=${1:-http://127.0.0.1:8080}
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); echo "  ✓ $1"; }
bad() { FAIL=$((FAIL+1)); echo "  ✗ $1"; }
J='python3 -c'
P() { printf %s  "$1" | python3 -c "import json,sys; d=json.load(sys.stdin); $2"; }

echo "== 剧本1. 上传新政策 → AI拆解 → 门禁审批 → 规则发布 =="
PID=$(curl -s -X POST $BASE/api/policies -H 'Content-Type: application/json' -d '{
  "title":"市属国有企业资产处置补充规定（演示二）","docNo":"演示〔2026〕8号","issuer":"市国资委（虚构）","effectDate":"2026-06-01",
  "sourceText":"第一条（进场要求）企业转让资产账面价值达到80万元的，应当在产权交易机构公开进行，符合豁免情形经批准可以采取非公开协议转让方式。第二条（评估有效期）资产处置评估结果使用有效期为自评估基准日起1年，超过有效期应当重新评估。第三条（决议要求）资产处置应当履行董事会决策程序并形成书面决议。"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
D=$(curl -s -X POST $BASE/api/agent/decompose/$PID)
printf %s  "$D" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d['clauses']==3 and d['obligations']==3 and d['ruleDrafts']==3, d
print('  拆解(mode=%s): 条款3 义务3 规则草稿3' % d['mode'])
" && ok "政策上传+五步拆解" || bad "政策拆解"
AP=$(curl -s -X POST "$BASE/api/agent/decompose/$PID/approve?approved=true" --data-urlencode "comment=合规部审核通过")
printf %s  "$AP" | python3 -c "
import json,sys; d=json.load(sys.stdin)
assert d['published']==3, d
" && ok "人工门禁审批→3条规则发布" || bad "门禁审批"
N=$(curl -s $BASE/api/rules | python3 -c "import json,sys; print(len([r for r in json.load(sys.stdin) if r['status']=='published']))")
[ "$N" = "16" ] && ok "生效规则16条（13种子+3拆解）" || bad "生效规则数=$N"

echo "== 剧本2. 带病申报 → 红色预审意见单（附条款依据）=="
R=$(curl -s -X POST $BASE/api/precheck -H 'Content-Type: application/json' -d '{
  "assetName":"福田区某办公楼3-4层（演示）","bookValue":680,"appraisedValue":720,"assessmentDate":"2025-08-20",
  "disposalMode":"非公开协议转让","exemption":false,"resolutionNo":"","listingPrice":650,"submitter":"申报人A"}')
printf %s  "$R" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d['opinion']['result']=='RED'
codes={h['code'] for h in json.loads(d['opinion']['hits'])}
# 种子规则R1-R4必须命中；拆解发布的新规则（阈值更宽）允许同时命中
assert {'R1','R2','R3','R4'} <= codes, codes
assert all(h['clauseRef'] and h['advice'] for h in json.loads(d['opinion']['hits']))
print('  命中: %s' % sorted(codes))
" && ok "红色预审意见单（含种子与新发布规则，附条款依据）" || bad "带病预审"

echo "== 剧本3. AI归因 → 人工审批 → 派单 =="
RID=$(curl -s "$BASE/api/risk-events?status=new" | python3 -c "import json,sys; rs=json.load(sys.stdin); print([r['id'] for r in rs if r['ruleCode']=='R1'][0])")
A=$(curl -s -X POST $BASE/api/agent/analyze/$RID)
printf %s  "$A" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d['status']=='pending' and d['result'] and d['suggestion'], d
print('  归因模式: %s' % d['model'])
" && ok "AI归因分析（至人工门禁）" || bad "AI归因"
AID=$(printf %s  "$A" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
AP2=$(curl -s -X POST "$BASE/api/agent/analyze/$AID/approve?approved=true")
printf %s  "$AP2" | python3 -c "
import json,sys; d=json.load(sys.stdin)
assert d['ticketId'], d
" && ok "审批→自动创建工单TK-${TKID:-$TID}" || bad "归因审批派单"
ST=$(curl -s $BASE/api/risk-events/$RID | python3 -c "import json,sys; print(json.load(sys.stdin)['status'])")
[ "$ST" = "ticketed" ] && ok "风险事件→ticketed" || bad "风险事件状态=$ST"

echo "== 剧本4. 工单闭环 → 风险事件自动closed =="
TK=$(printf %s  "$AP2" | python3 -c "import json,sys; print(json.load(sys.stdin)['ticketId'])")
E=0
for a in assign execute resolve verify close; do
  S=$(curl -s -X POST "$BASE/api/tickets/$TK/transition" \
      --data-urlencode "action=$a" --data-urlencode "operator=合规管理员" --data-urlencode "comment=验收" \
      | python3 -c "import json,sys; print(json.load(sys.stdin).get('status','ERR'))" 2>/dev/null)
  [ -z "$S" ] || [ "$S" = "ERR" ] && E=1
done
[ $E -eq 0 ] && ok "工单五步流转" || bad "工单流转"
CS=$(curl -s $BASE/api/risk-events/$RID | python3 -c "import json,sys; print(json.load(sys.stdin)['status'])")
[ "$CS" = "closed" ] && ok "复核通过→风险事件closed" || bad "风险事件状态=$CS"

echo "== 剧本5. 对话助手（条款溯源）=="
C=$(curl -s -X POST $BASE/api/agent/chat -H 'Content-Type: application/json' -d '{"question":"评估报告能用多久？"}')
printf %s  "$C" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d['answer'] and d['sources'], d
assert any('第12条' in s['clauseNo'] or '第二条' in s['clauseNo'] for s in d['sources']), [s['clauseNo'] for s in d['sources']]
" && ok "条款问答+溯源条号" || bad "对话助手"

echo "== 剧本6. 担保/租赁带病申报 → 场景内红色意见 =="
G=$(curl -s -X POST $BASE/api/precheck/guarantee -H 'Content-Type: application/json' -d '{
  "guaranteeee":"某某科技（深圳）有限公司（演示）","guaranteeType":"融资担保","guaranteeAmount":800,
  "balanceRatio":0.62,"guaranteeeeRating":"未评级","isHoldingSub":false,"counterGuarantee":false,"resolutionNo":""}')
printf %s  "$G" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d['opinion']['result']=='RED' and d['opinion']['scenario']=='guarantee', d['opinion']
codes={h['code'] for h in json.loads(d['opinion']['hits'])}
assert {'G1','G2'} <= codes, codes
" && ok "担保红色预审（G1/G2，场景隔离）" || bad "担保预审"
L=$(curl -s -X POST $BASE/api/precheck/lease -H 'Content-Type: application/json' -d '{
  "assetName":"南山某临街商铺（演示）","assetType":"商铺","pricingMode":"协议定价",
  "leaseTermMonths":84,"monthlyRent":2.8,"marketRentRef":4.2,"isRenewal":true,"resolutionNo":""}')
printf %s  "$L" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d['opinion']['result']=='RED' and d['opinion']['scenario']=='lease', d['opinion']
codes={h['code'] for h in json.loads(d['opinion']['hits'])}
assert {'L2','L3'} <= codes, codes
" && ok "租赁红色预审（L2/L3，场景隔离）" || bad "租赁预审"

echo "== 附加: 哈希链 + 统计 =="
V=$(curl -s $BASE/api/audit/chain/verify)
printf %s  "$V" | python3 -c "
import json,sys; v=json.load(sys.stdin)
assert v['valid'] is True, v
" && ok "哈希链全链有效(${V#*)}" || bad "哈希链校验"
curl -s $BASE/api/stats/overview | python3 -c "
import json,sys; s=json.load(sys.stdin)
assert s['publishedRules']==16, s
assert s['policies']>=3, s
sc=s['scenarios']
assert sc['asset']['publishedRules']==8, sc
assert sc['guarantee']['publishedRules']==4, sc
assert sc['lease']['publishedRules']==4, sc
print('  规则%d | 政策%d | 红色预审%d | 风险%d | 闭环率%s%%' % (s['publishedRules'], s['policies'], s['redPrechecks'], s['totalRisks'], s['closedRate']))
" && ok "驾驶舱统计（拆解规则已计入）" || bad "统计"

echo
echo "==== 全链路验收: PASS=$PASS FAIL=$FAIL ===="

#!/bin/bash
# 政策合规智能体 MVP · M1 全链路验收脚本
# 用法: sh scripts/acceptance-m1.sh [BASE_URL]  （默认 http://127.0.0.1:8080）
BASE=${1:-http://127.0.0.1:8080}
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✓ $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ✗ $1"; }

echo "== 1. 带病申报：未进场+评估超期+挂牌价低+决议缺失（期望RED，命中R1/R2/R3/R4）=="
R=$(curl -s -X POST $BASE/api/precheck -H 'Content-Type: application/json' -d '{
  "assetName":"福田区某办公楼3-4层（演示）","assetCategory":"房产",
  "bookValue":680,"appraisedValue":720,"assessmentDate":"2025-08-20",
  "disposalMode":"非公开协议转让","exemption":false,
  "resolutionNo":"","listingPrice":650,"submitter":"申报人A"}')
printf %s  "$R" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d['opinion']['result']=='RED', '预审结果应为RED'
codes=[h['code'] for h in json.loads(d['opinion']['hits'])]
assert codes==['R1','R2','R3','R4'], codes
assert all(h['clauseRef'] for h in json.loads(d['opinion']['hits'])), '须附条款依据'
print('  申报单', d['declaration']['declNo'], '| 预审RED | 命中', codes)
" && ok "带病申报预审判定" || bad "带病申报预审判定"
DECL=$(printf %s  "$R" | python3 -c "import json,sys; print(json.load(sys.stdin)['declaration']['id'])")
REF=$(printf %s  "$R" | python3 -c "import json,sys; print(json.load(sys.stdin)['declaration']['declNo'])")

echo "== 2. 合规申报（期望PASS）=="
curl -s -X POST $BASE/api/precheck -H 'Content-Type: application/json' -d '{
  "assetName":"南山科技园厂房（演示）","assetCategory":"房产",
  "bookValue":120,"appraisedValue":150,"assessmentDate":"2026-10-01",
  "disposalMode":"进场挂牌转让","exemption":false,
  "resolutionNo":"党决〔2026〕18号","listingPrice":155}' \
| python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d['opinion']['result']=='PASS', d
print('  合规申报 PASS，零命中')
" && ok "合规申报判定" || bad "合规申报判定"

echo "== 3. R5成交价异常（期望YELLOW，命中R5）=="
curl -s -X POST $BASE/api/precheck -H 'Content-Type: application/json' -d '{
  "assetName":"宝安某地块（演示）","bookValue":300,"appraisedValue":310,
  "assessmentDate":"2026-09-01","disposalMode":"进场挂牌转让",
  "resolutionNo":"党决〔2026〕9号","listingPrice":310,"dealPrice":260}' \
| python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d['opinion']['result']=='YELLOW', d
assert [h['code'] for h in json.loads(d['opinion']['hits'])]==['R5'], d['opinion']['hits']
print('  成交价异常 YELLOW，命中R5')
" && ok "R5判定" || bad "R5判定"

echo "== 4. 风险事件生成（期望5条：R1-R4各1条 + R5一条）=="
N=$(curl -s $BASE/api/risk-events | python3 -c "import json,sys; print(len(json.load(sys.stdin)))")
[ "$N" = "5" ] && ok "风险事件5条" || bad "风险事件数=$N"

echo "== 5. 工单闭环：创建→派发→执行→复核→关闭，风险事件联动closed =="
R1ID=$(curl -s $BASE/api/risk-events | python3 -c "import json,sys; print([r['id'] for r in json.load(sys.stdin) if r['ruleCode']=='R1'][0])")
TK=$(curl -s -X POST $BASE/api/tickets -H 'Content-Type: application/json' \
  -d "{\"riskEventId\":$R1ID,\"title\":\"福田办公楼进场交易整改\",\"description\":\"撤回非公开协议申报，转入进场挂牌流程\"}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
ERR=0
for a in assign execute resolve verify close; do
  ST=$(curl -s -X POST "$BASE/api/tickets/$TK/transition" \
       --data-urlencode "action=$a" --data-urlencode "operator=合规管理员" --data-urlencode "comment=M1验收" \
       | python3 -c "import json,sys; print(json.load(sys.stdin).get('status','ERR'))" 2>/dev/null)
  [ -z "$ST" ] || [ "$ST" = "ERR" ] && ERR=1
done
[ $ERR -eq 0 ] && ok "工单五步流转" || bad "工单流转"
CS=$(curl -s "$BASE/api/risk-events/$R1ID" | python3 -c "import json,sys; print(json.load(sys.stdin)['status'])")
[ "$CS" = "closed" ] && ok "复核通过联动风险事件closed" || bad "风险事件状态=$CS"

echo "== 6. 篡改检测：修改一行payload后哈希链应校验失败 =="
mysql -h127.0.0.1 -P3399 -uroot pcm -e "UPDATE audit_log SET payload='TAMPERED' WHERE id=1" 2>/dev/null
V=$(curl -s $BASE/api/audit/chain/verify)
printf %s  "$V" | python3 -c "
import json,sys
v=json.load(sys.stdin)
assert v['valid'] is False and v['brokenAt']==1, v
print('  篡改后校验: brokenAt=#1')
" && ok "防篡改检测" || bad "防篡改检测"
mysql -h127.0.0.1 -P3399 -uroot pcm -e "UPDATE audit_log SET payload=(SELECT payload FROM (SELECT payload FROM audit_log WHERE id=2) t) WHERE id=1" 2>/dev/null
echo "  (已尝试恢复演示数据，正式验证请重建库)"

echo "== 7. 驾驶舱统计 =="
curl -s $BASE/api/stats/overview | python3 -c "
import json,sys
s=json.load(sys.stdin)
assert s['publishedRules']==5 and s['declarations']==3 and s['totalRisks']==5, s
assert s['closedRisks']==1 and s['closedRate']==20.0, s
print('  规则5 | 申报3 | 风险5 | 闭环1 | 闭环率', s['closedRate'])
" && ok "驾驶舱统计" || bad "驾驶舱统计"

echo "== 8. 政策库五级视图（M1简化：政策→条款→义务）=="
curl -s $BASE/api/policies/1/decompose | python3 -c "
import json,sys
d=json.load(sys.stdin)
clauses=d['clauses']
assert len(clauses)==5, len(clauses)
print('  演示政策：', d['policy']['title'], '| 条款', len(clauses), '| 义务', sum(len(c['obligations']) for c in clauses))
" && ok "政策五级视图" || bad "政策五级视图"

echo
echo "==== M1验收结果: PASS=$PASS FAIL=$FAIL ===="

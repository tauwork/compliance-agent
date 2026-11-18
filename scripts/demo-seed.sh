#!/bin/bash
# 演示数据灌入（M1-M3完整剧本，全部虚构）
# 用法: sh scripts/demo-seed.sh [BASE_URL]
BASE=${1:-http://127.0.0.1:8080}
echo "== 演示数据灌入（政策→拆解→发布→申报→预审→归因→派单→闭环）=="

# 1. 上传第二份政策并拆解发布（体现AI拆解链路）
PID=$(curl -s -X POST $BASE/api/policies -H 'Content-Type: application/json' -d '{
  "title":"市属国有企业资产处置补充规定（演示二）","docNo":"演示〔2026〕8号","issuer":"市国资委（虚构）","effectDate":"2026-06-01",
  "sourceText":"第一条（进场要求）企业转让资产账面价值达到80万元的，应当在产权交易机构公开进行，符合豁免情形经批准可以采取非公开协议转让方式。第二条（评估有效期）资产处置评估结果使用有效期为自评估基准日起1年，超过有效期应当重新评估。第三条（决议要求）资产处置应当履行董事会决策程序并形成书面决议。"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
curl -s -X POST $BASE/api/agent/decompose/$PID | python3 -c "import json,sys; d=json.load(sys.stdin); print('拆解: 条款%d 义务%d 规则草稿%d (mode=%s)' % (d['clauses'], d['obligations'], d['ruleDrafts'], d['mode']))"
curl -s -X POST "$BASE/api/agent/decompose/$PID/approve?approved=true" --data-urlencode "comment=合规部审核通过" | python3 -c "import json,sys; d=json.load(sys.stdin); print('门禁审批: 发布%d条' % d['published'])"

# 2. 三份申报单
R1=$(curl -s -X POST $BASE/api/precheck -H 'Content-Type: application/json' -d '{
  "assetName":"福田区某办公楼3-4层（演示）","assetCategory":"房产",
  "bookValue":680,"appraisedValue":720,"assessmentDate":"2025-08-20",
  "disposalMode":"非公开协议转让","exemption":false,
  "resolutionNo":"","listingPrice":650,"submitter":"申报人A"}')
printf %s  "$R1" | python3 -c "import json,sys; d=json.load(sys.stdin); print('带病申报: %s → RED（命中%s条）' % (d['declaration']['declNo'], len(json.loads(d['opinion']['hits']))))"

curl -s -X POST $BASE/api/precheck -H 'Content-Type: application/json' -d '{
  "assetName":"南山科技园厂房（演示）","assetCategory":"房产",
  "bookValue":120,"appraisedValue":150,"assessmentDate":"2026-10-01",
  "disposalMode":"进场挂牌转让","exemption":false,
  "resolutionNo":"党决〔2026〕18号","listingPrice":155,"submitter":"申报人B"}' | python3 -c "import json,sys; d=json.load(sys.stdin); print('合规申报: %s → PASS' % d['declaration']['declNo'])"

curl -s -X POST $BASE/api/precheck -H 'Content-Type: application/json' -d '{
  "assetName":"宝安某地块（演示）","assetCategory":"土地",
  "bookValue":300,"appraisedValue":310,"assessmentDate":"2026-09-01",
  "disposalMode":"进场挂牌转让","exemption":false,
  "resolutionNo":"党决〔2026〕9号","listingPrice":310,"dealPrice":260,"submitter":"申报人C"}' | python3 -c "import json,sys; d=json.load(sys.stdin); print('R5异常申报: %s → YELLOW' % d['declaration']['declNo'])"

# 3. R1风险事件走AI归因→派单→闭环（完整链路）
RID=$(curl -s "$BASE/api/risk-events?status=new" | python3 -c "import json,sys; rs=json.load(sys.stdin); print([r['id'] for r in rs if r['ruleCode']=='R1'][0])")
AID=$(curl -s -X POST $BASE/api/agent/analyze/$RID | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
TK=$(curl -s -X POST "$BASE/api/agent/analyze/$AID/approve?approved=true" | python3 -c "import json,sys; print(json.load(sys.stdin)['ticketId'])")
for a in assign execute resolve verify close; do
  curl -s -X POST "$BASE/api/tickets/$TK/transition" \
    --data-urlencode "action=$a" --data-urlencode "operator=合规管理员" \
    --data-urlencode "comment=演示流转" > /dev/null
done
echo "R1完整闭环: AI归因→人工审批→工单TK-$TK六步流转→风险事件closed"

echo "== 哈希链校验 =="
curl -s $BASE/api/audit/chain/verify
echo
echo "== 驾驶舱统计 =="
curl -s $BASE/api/stats/overview
echo
echo "演示数据就绪。前端: http://localhost:5173"

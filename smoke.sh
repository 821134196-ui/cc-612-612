#!/usr/bin/env bash
# 对运行中的服务做端到端冒烟验证（真实 HTTP + 真实 SQLite 文件）
set -u
BASE=http://localhost:8080
OWNER=101
MONTH=2026-10
PASS=0; FAIL=0

j() { :; }
post() { curl -s -X POST -H 'Content-Type: application/json' -d "$2" "$BASE$1"; }
get() { curl -s "$BASE$1"; }
check() { # desc expected actual
  if [ "$2" == "$3" ]; then echo "  ✅ $1 = $3"; PASS=$((PASS+1));
  else echo "  ❌ $1: 期望 [$2] 实际 [$3]"; FAIL=$((FAIL+1)); fi
}

echo "== 1) 发放 4 个袋号 =="
post /api/bags/issue "{\"ownerCode\":\"$OWNER\",\"count\":4}" > /dev/null
BAGS=$(get "/api/bags?ownerCode=$OWNER")
echo "$BAGS" | python3 -c "import sys,json; [print('   ',b['bagCode'],b['status']) for b in json.load(sys.stdin)]"

echo "== 2) 退回 B101-0002（未使用） =="
post /api/bags/return '{"bagCodes":["B101-0002"]}' > /dev/null

echo "== 3) 未扫码前本月账单应为 0 元，退袋不收费 =="
BILL=$(get "/api/bills/monthly?ownerCode=$OWNER&month=$MONTH")
check "发放4+退1后账单金额(分)" 0 "$(echo "$BILL" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['amountFen'])")"
check "退回数量" 1 "$(echo "$BILL" | python3 -c "import sys,json;print(json.load(sys.stdin)['returnedCount'])")"

echo "== 4) 扫码 B101-0001 =="
S1=$(post /api/bags/scan '{"bagCode":"B101-0001","collector":"王清运"}')
check "首次扫码结果" ACCEPTED "$(echo "$S1" | python3 -c "import sys,json;print(json.load(sys.stdin)['result'])")"
check "首次扫码是否计费" True "$(echo "$S1" | python3 -c "import sys,json;print(json.load(sys.stdin)['charged'])")"

echo "== 5) 重复扫码同一袋号 =="
S2=$(post /api/bags/scan '{"bagCode":"B101-0001","collector":"王清运"}')
check "重复扫码结果" DUPLICATE "$(echo "$S2" | python3 -c "import sys,json;print(json.load(sys.stdin)['result'])")"
check "重复扫码是否再次计费" False "$(echo "$S2" | python3 -c "import sys,json;print(json.load(sys.stdin)['charged'])")"
check "重复扫码沿用袋号" B101-0001 "$(echo "$S2" | python3 -c "import sys,json;print(json.load(sys.stdin)['bag']['bagCode'])")"

echo "== 6) 已退回袋子被扫码 -> REJECTED =="
S3=$(post /api/bags/scan '{"bagCode":"B101-0002","collector":"王清运"}')
check "退袋扫码结果" REJECTED "$(echo "$S3" | python3 -c "import sys,json;print(json.load(sys.stdin)['result'])")"

echo "== 7) 本月账单只收 1 袋 = 8.00 元 =="
BILL=$(get "/api/bills/monthly?ownerCode=$OWNER&month=$MONTH")
check "账单金额(分)" 800 "$(echo "$BILL" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['amountFen'])")"
check "收费明细条数" 1 "$(echo "$BILL" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['chargedItems']))")"
BILL_ID=$(echo "$BILL" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['id'])")

echo "== 8) 约定遗失 B101-0004 归属 2026-11（跨月，沿用袋号） =="
post /api/bags/lost '{"bagCode":"B101-0004","month":"2026-11","reason":"协商遗失"}' > /dev/null
check "10月账单不含11月遗失袋" 800 "$(get "/api/bills/monthly?ownerCode=$OWNER&month=2026-10" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['amountFen'])")"
NOV=$(get "/api/bills/monthly?ownerCode=$OWNER&month=2026-11")
check "11月账单按遗失袋号计费(分)" 800 "$(echo "$NOV" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['amountFen'])")"
check "11月账单沿用原袋号" B101-0004 "$(echo "$NOV" | python3 -c "import sys,json;print(json.load(sys.stdin)['chargedItems'][0]['bagCode'])")"

echo "== 9) 结算 10 月账单（模拟支付） =="
SET=$(post "/api/bills/$BILL_ID/settle" '{"paidVia":"本地模拟支付"}')
check "结算后状态" SETTLED "$(echo "$SET" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['status'])")"
REF1=$(echo "$SET" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['paymentRef'])")
SET2=$(post "/api/bills/$BILL_ID/settle" '{"paidVia":"本地模拟支付"}')
check "重复支付流水号不变(不重复扣款)" "$REF1" "$(echo "$SET2" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['paymentRef'])")"

echo "== 10) 已结算月份差错：B101-0003 漏收 -> 补差单 +800 =="
ADJ=$(post /api/bills/adjust '{"ownerCode":"101","month":"2026-10","lines":[{"bagCode":"B101-0003","correctionType":"MISSED_COLLECTION","reason":"漏扫码"}]}')
check "补差单类型" ADJUSTMENT "$(echo "$ADJ" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['billType'])")"
check "补差金额(分)" 800 "$(echo "$ADJ" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['amountFen'])")"
check "补差指向原账单" "$BILL_ID" "$(echo "$ADJ" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['originalBillId'])")"

echo "== 11) 重复提交同一补差 -> 不重复收费 =="
ADJ2=$(post /api/bills/adjust '{"ownerCode":"101","month":"2026-10","lines":[{"bagCode":"B101-0003","correctionType":"MISSED_COLLECTION","reason":"再次提交"}]}')
check "重复补差金额仍为(分)" 800 "$(echo "$ADJ2" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['amountFen'])")"
check "补差明细仍为1条" 1 "$(echo "$ADJ2" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['adjustmentLines']))")"

echo "== 12) 错收退费 B101-0001 -> 补差单轧差为 0 =="
ADJ3=$(post /api/bills/adjust '{"ownerCode":"101","month":"2026-10","lines":[{"bagCode":"B101-0001","correctionType":"WRONG_COLLECTION","reason":"业主核对未清运"}]}')
check "补收+退费轧差(分)" 0 "$(echo "$ADJ3" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['amountFen'])")"

echo "== 13) 原账单保持不变 =="
ORIG=$(get "/api/bills/$BILL_ID")
check "原账单金额不变(分)" 800 "$(echo "$ORIG" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['amountFen'])")"
check "原账单状态不变" SETTLED "$(echo "$ORIG" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['status'])")"
check "原账单流水不变" "$REF1" "$(echo "$ORIG" | python3 -c "import sys,json;print(json.load(sys.stdin)['bill']['paymentRef'])")"
check "原账单明细快照仍2条? " 1 "$(echo "$ORIG" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['chargedItems']))")"

echo "== 14) 前端页面与静态资源 =="
check "首页 HTTP 状态" 200 "$(curl -s -o /dev/null -w '%{http_code}' $BASE/)"
check "扫码记录接口 ACCEPTED 次数" 1 "$(get '/api/scans/recent?limit=50' | python3 -c "import sys,json;print(sum(1 for x in json.load(sys.stdin) if x['bagCode']=='B101-0001' and x['result']=='ACCEPTED'))")"
check "扫码记录 DUPLICATE 回执数" 1 "$(get '/api/scans/recent?limit=50' | python3 -c "import sys,json;print(sum(1 for x in json.load(sys.stdin) if x['bagCode']=='B101-0001' and x['result']=='DUPLICATE'))")"

echo
echo "结果：通过 $PASS，失败 $FAIL"
[ "$FAIL" -eq 0 ]

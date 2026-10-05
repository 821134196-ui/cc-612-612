# 住宅装修建筑垃圾袋领用与清运计费系统

物业按袋号发放装修垃圾袋，**只为实际清运或双方约定遗失的袋子计费**——不是领了多少袋就收多少清运费。

- 后端：Java 17 + Quarkus + Hibernate ORM (Panache) + SQLite，支付接口本地模拟
- 前端：Svelte 4 + Vite，移动端单页（底部四个标签），现场扫码后立即看到回执
- 一条命令启动：`./run.sh`

## 计费规则（核心）

| 袋子状态 | 含义 | 是否计费 |
| --- | --- | --- |
| `ISSUED` | 已发放、在业主手中 | ❌ 不计费 |
| `RETURNED` | 未使用已退回 | ❌ **永不产生清运费**，再扫码直接 REJECTED |
| `COLLECTED` | 清运员扫码确认收走 | ✅ 计一次，计入**实际清运月** |
| `LOST` | 双方约定遗失 | ✅ 视同清运计一次 |

1. **一个袋号一条记录、终身沿用**：袋子破损只追加备注，跨月清运仍用原袋号，不另建记录 → 物理上不可能重复收费。
2. **扫码幂等**：同一袋号第一次扫码 `ACCEPTED`（计费），之后全部 `DUPLICATE`（不计费）；每次尝试都留回执日志。
3. **账单并排核对**：同页并列展示领用、退袋、清运/遗失三类记录与收费来源明细。
4. **已结算月份差错 → 补差单**：原账单（含明细快照、支付流水）冻结不变；补差单只记差额（漏收 `MISSED_COLLECTION` 为 +，错收 `WRONG_COLLECTION` 为 −）。同一原账单、同一袋号、同一更正类型只能提交一次，重复提交幂等跳过。
5. **模拟支付**：`MockGateway` 始终成功，正额返回 `PAY-`、退费返回 `REF-` 流水；重复结算返回原流水，不二次扣款。

## 一条命令启动

```bash
./run.sh
```

脚本会自动：安装/使用本地 JDK17+Maven（无系统安装时用 `~/tools`）→ `npm install` + 构建 Svelte → `mvn package` → 启动。

打开 <http://localhost:8080>（手机同局域网访问 `http://<电脑IP>:8080`）。SQLite 数据库文件在 `data/bag-billing.db`，首次启动自动建表并写入 3 个演示业主。

前端开发热更新（可选）：`cd ui && npm run dev`（5173 端口，已配置 `/api` 代理到 8080）。

## 现场扫码

手机页面「扫码清运」标签：输入框支持**蓝牙/USB 扫码枪**（扫完自动回车提交），也可手动输入袋号。提交后立即显示：

- 绿色 `✓ 清运成功，已计费一次`
- 黄色 `⟳ 重复扫码，不重复收费`（含首次清运时间、清运员）
- 红色 `✕ 拒绝清运`（已退回/不存在）

下方实时显示最近扫码回执列表。

## 主要 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/bags/issue` | 物业登记发袋 `{ownerCode, count}` 或 `{ownerCode, bagCodes:[...]}` |
| POST | `/api/bags/return` | 未使用退袋 `{bagCodes:[...]}` |
| POST | `/api/bags/scan` | 清运扫码 `{bagCode, collector}` → ACCEPTED/DUPLICATE/REJECTED |
| POST | `/api/bags/lost` | 约定遗失 `{bagCode, month, reason}` |
| POST | `/api/bags/note` | 破损等备注，沿用原袋号 |
| GET | `/api/bills/monthly?ownerCode=&month=` | 月账单（OPEN 实时重算；含并排核对数据） |
| POST | `/api/bills/{id}/settle` | 本地模拟支付结算 |
| POST | `/api/bills/adjust` | 已结算月份补差 `{ownerCode, month, lines:[{bagCode, correctionType, reason}]}` |
| GET | `/api/scans/recent` | 扫码回执日志 |

单价在 `src/main/resources/application.properties` 的 `app.pricing.per-bag` 配置（默认 ¥8.00/袋，金额内部以"分"存储）。

## 验证

**自动化测试（4 条关键规则，Quarkus + REST-assured + 真实 SQLite）：**

```bash
mvn test
```

`src/test/java/com/renovation/bag/BillingRulesTest.java`：

1. `returnedBagsAreNeverCharged` — 发 3 袋：清运 1、退 1、留 1，账单只有 ¥8.00；退掉的袋子再扫码被 REJECTED。
2. `crossMonthCollectionKeepsOriginalBagCode` — 9 月发放（9 月账单 ¥0），10 月扫码清运，沿用原袋号计入 10 月。
3. `duplicateScanChargesOnlyOnce` — 连扫 3 次：1×ACCEPTED + 2×DUPLICATE，仅 1 条收费明细、¥8.00。
4. `settledMonthAdjustmentKeepsOriginalBillAndNeverDoubleCharges` — 结算后补差收/退，原账单金额、状态、流水、明细快照全程不变；重复补差与重复支付都幂等。

**真实服务冒烟（需先 `./run.sh`）：**

```bash
./smoke.sh
```

对运行中的系统走完整 HTTP 流程，28 项断言全部通过。

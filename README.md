# 住宅装修建筑垃圾袋领用与清运计费系统

物业按袋号发放装修垃圾袋，**业主只为实际清运（或约定遗失）的袋子付费**——领用多少袋不等于收多少清运费。

## 一条命令启动

```bash
./start.sh
```

脚本依次完成：构建 Svelte 前端 → 打包 Quarkus 后端 → 启动服务于 <http://localhost:8080>。
数据保存在 `backend/wastebags.db`（SQLite，无需安装数据库）。支付接口为本地模拟，不调用外部网关。

> 需要 JDK 17+、Maven 3.9+、Node 18+。若系统未安装 Java/Maven，脚本会自动使用 `~/tools/` 下的便携版。

## 业务规则

| 动作 | 是否计费 | 说明 |
|---|---|---|
| 领用（发袋） | 否 | 只登记袋号归属 |
| 退袋 | 否 | 未使用的袋子退回物业 |
| 破损登记 | 否 | 沿用原袋号，不另建记录 |
| 扫码清运 | 是 | 每袋一次清运费；**重复扫码只登记一次** |
| 报失 | 是 | 按约定标准赔付 |
| 跨月清运 | 是 | 沿用原袋号，费用落在**清运当月**账期 |
| 已结算月份差错 | 补差单 | 原账单冻结不变，差额走补差单 |

费用标准在 `backend/src/main/resources/application.properties` 配置（`app.fees.clearance` / `app.fees.lost`）。

## 手机扫码页

首页即扫码页（`#/scan`）：大输入框 + 摄像头扫码（localhost/https 下可用），提交后立即显示
「清运成功 / 重复扫码 / 已拒绝」及费用归属账期。

## 主要接口

```
POST /api/bags/issue            发袋 {ownerId, count}
POST /api/bags/{bagNo}/return   退袋（免费）
POST /api/bags/{bagNo}/lost     报失（约定赔付）
POST /api/bags/{bagNo}/damage   破损登记（沿用原袋号）
POST /api/scan                  扫码清运（幂等）{bagNo, operator}
GET  /api/bills/statement       业主对账 ?ownerId&month（领用/退袋/清运并列）
POST /api/bills/generate        出账 {month, ownerId?}（幂等）
POST /api/bills/{id}/settle     结算（冻结账单）
POST /api/bills/{id}/adjustments 已结算差错 -> 补差单
POST /api/payments              模拟支付 {billId | adjustmentId, amount}
```

各写接口接受可选 `at`（ISO 本地时间）用于补录历史时间，便于跨月场景与测试。

## 测试

```bash
cd backend && mvn test
```

`BillingRulesTest` 覆盖：退袋不产生清运费、重复扫码只计一次、跨月沿用原袋号且费用落在清运当月、
破损袋沿用原袋号、已结算月份差错走补差单且原账单不变、重复出账/重复支付不产生重复收费、
遗失按约定赔付且报失后不能再清运。

## 技术栈

Java 21 · Quarkus 3（REST + Hibernate ORM Panache）· SQLite（Quarkiverse 驱动）· Svelte 5 + Vite

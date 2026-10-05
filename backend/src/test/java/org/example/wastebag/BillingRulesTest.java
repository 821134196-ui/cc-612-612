package org.example.wastebag;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 计费规则验收：
 *  1. 领用、退袋不产生任何费用；
 *  2. 重复扫码只登记一次有效清运；
 *  3. 跨月清运沿用原袋号，费用落在清运当月，不重复收费；
 *  4. 已结算月份的差错走补差单，原账单不变、不重复收费。
 */
@QuarkusTest
class BillingRulesTest {

    // ---------- 工具 ----------

    long newOwner() {
        return given().contentType(ContentType.JSON)
                .body(Map.of("name", "测试业主", "unit", "T-" + UUID.randomUUID(), "phone", "13000000000"))
                .when().post("/api/owners")
                .then().statusCode(200)
                .extract().jsonPath().getLong("id");
    }

    List<String> issue(long ownerId, int count, String at) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("ownerId", ownerId, "count", count, "at", at))
                .when().post("/api/bags/issue")
                .then().statusCode(200)
                .extract().jsonPath().getList("bagNo", String.class);
    }

    Map<String, Object> scan(String bagNo, String at) {
        return given().contentType(ContentType.JSON)
                .body(at == null ? Map.of("bagNo", bagNo) : Map.of("bagNo", bagNo, "at", at))
                .when().post("/api/scan")
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
    }

    Map<String, Object> generate(String month, long ownerId) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("month", month, "ownerId", ownerId))
                .when().post("/api/bills/generate")
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
    }

    Map<String, Object> billOf(long ownerId, String month) {
        List<Map<String, Object>> bills = given()
                .queryParam("ownerId", ownerId).queryParam("month", month)
                .when().get("/api/bills")
                .then().statusCode(200)
                .extract().jsonPath().getList("");
        return bills.isEmpty() ? null : bills.get(0);
    }

    Map<String, Object> statement(long ownerId, String month) {
        return given().queryParam("ownerId", ownerId).queryParam("month", month)
                .when().get("/api/bills/statement")
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
    }

    // ---------- 1. 领用与退袋不产生清运费 ----------

    @Test
    void issueAndReturnProduceNoFee() {
        long owner = newOwner();
        List<String> bags = issue(owner, 3, "2026-08-05T10:00:00");
        assertEquals(3, bags.size());

        // 退回 1 袋
        given().contentType(ContentType.JSON).body(Map.of("at", "2026-08-06T09:00:00"))
                .when().post("/api/bags/" + bags.get(0) + "/return")
                .then().statusCode(200).body("status", is("RETURNED"));

        // 出账：没有任何费用事件 -> 不生成账单
        Map<String, Object> gen = generate("2026-08", owner);
        assertEquals(0, ((List<?>) gen.get("bills")).size());
        assertNull(billOf(owner, "2026-08"));

        // 对账单：领用 3、退袋 1、费用 0
        Map<String, Object> st = statement(owner, "2026-08");
        Map<String, Object> totals = (Map<String, Object>) st.get("totals");
        assertEquals(3, totals.get("issuedCount"));
        assertEquals(1, totals.get("returnedCount"));
        assertEquals(0, totals.get("clearedCount"));
        assertEquals(0.0, ((Number) totals.get("total")).doubleValue());
    }

    // ---------- 2. 重复扫码只计一次 ----------

    @Test
    void duplicateScanRegistersOnlyOneClearance() {
        long owner = newOwner();
        String bagNo = issue(owner, 1, "2026-08-05T10:00:00").get(0);

        Map<String, Object> first = scan(bagNo, "2026-08-07T08:00:00");
        assertEquals("CLEARED", first.get("result"));
        assertEquals(5.0, ((Number) first.get("fee")).doubleValue());

        // 重复扫码：返回 DUPLICATE，不产生新记录
        Map<String, Object> dup = scan(bagNo, "2026-08-07T08:05:00");
        assertEquals("DUPLICATE", dup.get("result"));

        Map<String, Object> gen = generate("2026-08", owner);
        assertEquals(1, ((List<?>) gen.get("bills")).size());
        Map<String, Object> bill = billOf(owner, "2026-08");
        assertEquals(10.0 - 5.0, 5.0); // 占位防误改
        assertEquals(5.0, ((Number) bill.get("total")).doubleValue());
        List<Map<String, Object>> items = (List<Map<String, Object>>) bill.get("items");
        assertEquals(1, items.size());
        assertEquals("CLEARANCE_FEE", items.get(0).get("type"));
        assertEquals(bagNo, items.get(0).get("bagNo"));
    }

    // ---------- 3. 跨月清运沿用原袋号，不重复收费 ----------

    @Test
    void crossMonthClearanceKeepsOriginalBagNo() {
        long owner = newOwner();
        String bagNo = issue(owner, 1, "2026-08-28T10:00:00").get(0);

        // 8 月出账（该袋未清运，无费用）
        generate("2026-08", owner);
        assertNull(billOf(owner, "2026-08"));

        // 9 月才清运：仍扫原袋号
        Map<String, Object> r = scan(bagNo, "2026-09-02T07:30:00");
        assertEquals("CLEARED", r.get("result"));
        assertEquals("2026-09", r.get("month"));

        // 9 月出账：费用落在 9 月、袋号不变；8 月依旧无账单
        generate("2026-09", owner);
        assertNull(billOf(owner, "2026-08"));
        Map<String, Object> bill = billOf(owner, "2026-09");
        assertEquals(5.0, ((Number) bill.get("total")).doubleValue());
        List<Map<String, Object>> items = (List<Map<String, Object>>) bill.get("items");
        assertEquals(bagNo, items.get(0).get("bagNo"));

        // 全库该袋号只有一条袋记录、一次清运
        List<Map<String, Object>> bags = given().queryParam("ownerId", owner)
                .when().get("/api/bags").then().extract().jsonPath().getList("");
        assertEquals(1, bags.stream().filter(b -> b.get("bagNo").equals(bagNo)).count());
        assertEquals("CLEARED", bags.get(0).get("status"));
    }

    // ---------- 3b. 破损袋沿用原袋号清运 ----------

    @Test
    void damagedBagKeepsSameBagNoAndChargesOnce() {
        long owner = newOwner();
        String bagNo = issue(owner, 1, "2026-08-05T10:00:00").get(0);

        given().contentType(ContentType.JSON).body(Map.of("note", "袋口撕裂", "at", "2026-08-06T10:00:00"))
                .when().post("/api/bags/" + bagNo + "/damage")
                .then().statusCode(200).body("damaged", is(true));

        // 破损袋仍用原袋号清运
        Map<String, Object> r = scan(bagNo, "2026-08-08T09:00:00");
        assertEquals("CLEARED", r.get("result"));

        generate("2026-08", owner);
        Map<String, Object> bill = billOf(owner, "2026-08");
        assertEquals(5.0, ((Number) bill.get("total")).doubleValue());
        List<Map<String, Object>> items = (List<Map<String, Object>>) bill.get("items");
        assertEquals(1, items.size());
        assertEquals(bagNo, items.get(0).get("bagNo"));
    }

    // ---------- 4. 已结算月份的差错走补差单，不重复收费 ----------

    @Test
    void settledMonthCorrectionUsesAdjustmentAndNeverDoubleCharges() {
        long owner = newOwner();
        List<String> bags = issue(owner, 3, "2026-08-05T10:00:00");
        scan(bags.get(0), "2026-08-07T08:00:00");
        scan(bags.get(1), "2026-08-08T08:00:00");

        // 8 月出账并结算：2 袋清运 = 10 元
        generate("2026-08", owner);
        Map<String, Object> bill = billOf(owner, "2026-08");
        long billId = ((Number) bill.get("id")).longValue();
        assertEquals(10.0, ((Number) bill.get("total")).doubleValue());
        given().when().post("/api/bills/" + billId + "/settle")
                .then().statusCode(200).body("status", is("SETTLED"));

        // 差错一：第 3 袋其实 8 月已清运但漏扫，补录（时间仍在 8 月）
        Map<String, Object> late = scan(bags.get(2), "2026-08-20T08:00:00");
        assertEquals("CLEARED", late.get("result"));
        assertTrue(((String) late.get("routedTo")).startsWith("ADJUSTMENT:"));

        // 原账单保持 10 元不变，补差单 5 元
        Map<String, Object> billAfter = billOf(owner, "2026-08");
        assertEquals(10.0, ((Number) billAfter.get("total")).doubleValue());
        List<Map<String, Object>> adjs = (List<Map<String, Object>>) billAfter.get("adjustments");
        assertEquals(1, adjs.size());
        assertEquals(5.0, ((Number) adjs.get(0).get("total")).doubleValue());

        // 差错二：物业手工补差（误收退回 5 元）-> 又一张补差单，原账单仍不变
        long adjId = ((Number) adjs.get(0).get("id")).longValue();
        given().contentType(ContentType.JSON)
                .body(Map.of("reason", "误收退回", "items", List.of(
                        Map.of("type", "ADJUST_CREDIT", "amount", 5, "description", "多收清运费退回"))))
                .when().post("/api/bills/" + billId + "/adjustments")
                .then().statusCode(200).body("total", is(-5));

        // 重新出账必须幂等：所有事件已入账，不再产生新账单/补差单
        Map<String, Object> regen = generate("2026-08", owner);
        assertEquals(0, ((List<?>) regen.get("bills")).size());
        assertEquals(0, ((List<?>) regen.get("adjustments")).size());
        Map<String, Object> billFinal = billOf(owner, "2026-08");
        assertEquals(10.0, ((Number) billFinal.get("total")).doubleValue());
        assertEquals(2, ((List<?>) billFinal.get("adjustments")).size());

        // 支付：账单 10 元、补差单 5 元各自支付一次，重复支付被拒绝
        given().contentType(ContentType.JSON).body(Map.of("billId", billId, "amount", 10))
                .when().post("/api/payments").then().statusCode(200).body("status", is("SUCCESS"));
        given().contentType(ContentType.JSON).body(Map.of("billId", billId, "amount", 10))
                .when().post("/api/payments").then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("adjustmentId", adjId, "amount", 5))
                .when().post("/api/payments").then().statusCode(200);
        // 合计实收 = 10 + 5 - 5 = 10，与两次有效清运 + 一次退回一致，无重复收费
    }

    // ---------- 5. 遗失按约定赔付，且报失后不能再清运 ----------

    @Test
    void lostBagChargedByAgreementOnly() {
        long owner = newOwner();
        String bagNo = issue(owner, 1, "2026-08-05T10:00:00").get(0);

        given().contentType(ContentType.JSON).body(Map.of("at", "2026-08-09T10:00:00"))
                .when().post("/api/bags/" + bagNo + "/lost")
                .then().statusCode(200).body("status", is("LOST"));

        // 报失后再扫码：拒绝，不产生清运费
        Map<String, Object> r = scan(bagNo, "2026-08-10T08:00:00");
        assertEquals("REJECTED", r.get("result"));

        generate("2026-08", owner);
        Map<String, Object> bill = billOf(owner, "2026-08");
        assertEquals(15.0, ((Number) bill.get("total")).doubleValue());
        List<Map<String, Object>> items = (List<Map<String, Object>>) bill.get("items");
        assertEquals(1, items.size());
        assertEquals("LOST_FEE", items.get(0).get("type"));
    }

    // ---------- 6. 支付金额校验 ----------

    @Test
    void paymentRequiresExactAmount() {
        long owner = newOwner();
        String bagNo = issue(owner, 1, "2026-08-05T10:00:00").get(0);
        scan(bagNo, "2026-08-07T08:00:00");
        generate("2026-08", owner);
        long billId = ((Number) billOf(owner, "2026-08").get("id")).longValue();

        // 未结算不能支付
        given().contentType(ContentType.JSON).body(Map.of("billId", billId, "amount", 5))
                .when().post("/api/payments").then().statusCode(400);

        given().when().post("/api/bills/" + billId + "/settle").then().statusCode(200);

        // 金额不符被拒绝
        given().contentType(ContentType.JSON).body(Map.of("billId", billId, "amount", 4.99))
                .when().post("/api/payments").then().statusCode(400);

        given().contentType(ContentType.JSON).body(Map.of("billId", billId, "amount", 5))
                .when().post("/api/payments").then().statusCode(200)
                .body("txnId", startsWith("MOCK-"));
    }
}

package com.renovation.bag;

import com.renovation.bag.support.AppClock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 关键计费规则端到端验证（REST + SQLite + Hibernate）：
 * 1) 退袋不产生清运费
 * 2) 跨月清运沿用原袋号，计入实际清运月
 * 3) 重复扫码只登记一次有效清运，不重复收费
 * 4) 已结算月份差错生成补差单，原账单不变，重复提交不重复收费
 */
@QuarkusTest
class BillingRulesTest {

    @Inject
    AppClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private String createOwner(String code) {
        given().contentType(ContentType.JSON)
                .body(Map.of("code", code, "name", "测试业主" + code, "room", code + "室"))
                .post("/api/owners")
                .then().statusCode(anyOf(is(200), is(201)));
        return code;
    }

    private List<String> issue(String owner, int n, String codePrefix) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("ownerCode", owner, "count", n, "prefix", codePrefix))
                .post("/api/bags/issue")
                .then().statusCode(200)
                .extract().jsonPath().getList("bagCode", String.class);
    }

    private Response scan(String code) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("bagCode", code, "collector", "测试清运员"))
                .post("/api/bags/scan")
                .then().statusCode(200)
                .extract().response();
    }

    private Map<String, Object> monthly(String owner, String month) {
        return given()
                .queryParam("ownerCode", owner)
                .queryParam("month", month)
                .get("/api/bills/monthly")
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
    }

    private long fen(Map<String, Object> billDetail) {
        return ((Number) ((Map<?, ?>) billDetail.get("bill")).get("amountFen")).longValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnedBagsAreNeverCharged() {
        String owner = createOwner("T101");
        List<String> codes = issue(owner, 3, "RT-");
        // 1 袋清运、1 袋退回、1 袋留在家中未使用
        assertEquals("ACCEPTED", scan(codes.get(0)).jsonPath().getString("result"));
        given().contentType(ContentType.JSON)
                .body(Map.of("bagCodes", List.of(codes.get(1))))
                .post("/api/bags/return")
                .then().statusCode(200);

        Map<String, Object> bill = monthly(owner, clock.currentMonth());
        // 只有 1 袋计清运费 8.00；退回与在户的袋子不收费
        assertEquals(800L, fen(bill), "退袋和未使用袋不应产生清运费");
        List<Map<String, Object>> charged = (List<Map<String, Object>>) bill.get("chargedItems");
        assertEquals(1, charged.size());
        assertEquals(codes.get(0), charged.get(0).get("bagCode"));
        assertEquals(3L, ((Number) bill.get("issuedCount")).longValue());
        assertEquals(1L, ((Number) bill.get("returnedCount")).longValue());

        // 退掉的袋子再尝试扫码清运 -> REJECTED，不收费
        Response rejected = scan(codes.get(1));
        assertEquals("REJECTED", rejected.jsonPath().getString("result"));
        assertFalse(rejected.jsonPath().getBoolean("charged"));
        Map<String, Object> bill2 = monthly(owner, clock.currentMonth());
        assertEquals(800L, fen(bill2), "已退回袋子被扫码也不能产生费用");
    }

    @Test
    @SuppressWarnings("unchecked")
    void crossMonthCollectionKeepsOriginalBagCode() {
        String owner = createOwner("T102");
        clock.setFixed(Instant.parse("2026-09-28T10:00:00Z"));
        List<String> codes = issue(owner, 1, "CM-");
        String bagCode = codes.get(0);

        // 9 月只发放未清运 -> 9 月账单 0 元
        Map<String, Object> sep = monthly(owner, "2026-09");
        assertEquals(0L, fen(sep), "仅发放不应在发放月计费");

        // 跨到 10 月才清运：沿用原袋号，计入 10 月
        clock.setFixed(Instant.parse("2026-10-02T09:00:00Z"));
        Response r = scan(bagCode);
        assertEquals("ACCEPTED", r.jsonPath().getString("result"));
        assertEquals(bagCode, r.jsonPath().getString("bag.bagCode"),
                "跨月清运必须沿用原袋号，不得另建记录");
        assertEquals("2026-10", r.jsonPath().getString("bag.collectedMonth"));

        Map<String, Object> sepAgain = monthly(owner, "2026-09");
        Map<String, Object> oct = monthly(owner, "2026-10");
        assertEquals(0L, fen(sepAgain), "9 月账单仍为 0，不能向发放月收费");
        assertEquals(800L, fen(oct), "清运费应计入实际清运月 10 月");
        List<Map<String, Object>> charged = (List<Map<String, Object>>) oct.get("chargedItems");
        assertEquals(1, charged.size());
        assertEquals(bagCode, charged.get(0).get("bagCode"),
                "10 月账单上的计费袋号必须是原袋号");
        assertEquals("2026-10", charged.get(0).get("eventMonth"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateScanChargesOnlyOnce() {
        String owner = createOwner("T103");
        List<String> codes = issue(owner, 1, "DP-");
        String bagCode = codes.get(0);

        Response first = scan(bagCode);
        Response second = scan(bagCode);
        Response third = scan(bagCode);
        assertEquals("ACCEPTED", first.jsonPath().getString("result"));
        assertTrue(first.jsonPath().getBoolean("charged"));
        assertEquals("DUPLICATE", second.jsonPath().getString("result"),
                "重复扫码必须返回 DUPLICATE 回执");
        assertFalse(second.jsonPath().getBoolean("charged"));
        assertEquals("DUPLICATE", third.jsonPath().getString("result"));
        // 重复扫码仍沿用同一个袋号记录
        assertEquals(bagCode, second.jsonPath().getString("bag.bagCode"));

        Map<String, Object> bill = monthly(owner, clock.currentMonth());
        assertEquals(800L, fen(bill), "重复扫码不能重复收费");
        List<Map<String, Object>> charged = (List<Map<String, Object>>) bill.get("chargedItems");
        assertEquals(1, charged.size(), "同一袋号只能有一条收费明细");

        given().get("/api/scans/recent?limit=50")
                .then()
                .body("findAll { it.bagCode == '" + bagCode + "' && it.result == 'ACCEPTED' }.size()", is(1))
                .body("findAll { it.bagCode == '" + bagCode + "' && it.result == 'DUPLICATE' }.size()", is(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void settledMonthAdjustmentKeepsOriginalBillAndNeverDoubleCharges() {
        String owner = createOwner("T104");
        clock.setFixed(Instant.parse("2026-09-30T15:00:00Z"));
        List<String> codes = issue(owner, 3, "AD-");
        scan(codes.get(0));
        scan(codes.get(1));
        // codes.get(2) 仍在户：模拟当月实际清运但漏扫码

        Map<String, Object> sep = monthly(owner, "2026-09");
        long originalId = ((Number) ((Map<?, ?>) sep.get("bill")).get("id")).longValue();
        assertEquals(1600L, fen(sep));

        // 结算 9 月账单（模拟支付）
        Map<String, Object> settled = given().contentType(ContentType.JSON)
                .body(Map.of("paidVia", "本地模拟支付"))
                .post("/api/bills/" + originalId + "/settle")
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
        String paymentRef = (String) ((Map<?, ?>) settled.get("bill")).get("paymentRef");
        assertNotNull(paymentRef);
        assertEquals(1600L, fen(settled));

        // 未结算月份不能补差（10 月 OPEN）-> 400
        given().contentType(ContentType.JSON)
                .body(Map.of("ownerCode", owner, "month", "2026-10",
                        "lines", List.of(Map.of("bagCode", codes.get(2),
                                "correctionType", "MISSED_COLLECTION", "reason", "x"))))
                .post("/api/bills/adjust")
                .then().statusCode(400);

        // 漏收补收：生成补差单 +800
        Map<String, Object> adj = given().contentType(ContentType.JSON)
                .body(Map.of("ownerCode", owner, "month", "2026-09",
                        "lines", List.of(Map.of("bagCode", codes.get(2),
                                "correctionType", "MISSED_COLLECTION",
                                "reason", "月底清运漏扫码，监控确认已收走"))))
                .post("/api/bills/adjust")
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
        long adjId = ((Number) ((Map<?, ?>) adj.get("bill")).get("id")).longValue();
        assertEquals("ADJUSTMENT", ((Map<?, ?>) adj.get("bill")).get("billType"));
        assertEquals(800L, fen(adj));
        assertEquals(originalId,
                ((Number) ((Map<?, ?>) adj.get("bill")).get("originalBillId")).longValue());

        // 原账单必须原样不变：仍为 1600、已结算、2 条明细、原流水号
        Map<String, Object> originalView = given()
                .get("/api/bills/" + originalId)
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
        assertEquals(1600L, fen(originalView), "补差不能改变原账单金额");
        assertEquals("SETTLED", ((Map<?, ?>) originalView.get("bill")).get("status"));
        assertEquals(paymentRef, ((Map<?, ?>) originalView.get("bill")).get("paymentRef"));
        assertEquals(2, ((List<?>) originalView.get("chargedItems")).size(),
                "原账单明细快照不可变");

        // 重复提交同一补差 -> 幂等，补差单仍是 +800
        Map<String, Object> adjAgain = given().contentType(ContentType.JSON)
                .body(Map.of("ownerCode", owner, "month", "2026-09",
                        "lines", List.of(Map.of("bagCode", codes.get(2),
                                "correctionType", "MISSED_COLLECTION", "reason", "重复提交"))))
                .post("/api/bills/adjust")
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
        assertEquals(800L, fen(adjAgain), "重复补差不能重复收费");
        assertEquals(1, ((List<?>) adjAgain.get("adjustmentLines")).size());

        // 错收退费：0001 实际未清运却被收费 -> -800，补差单合计变 0，原账单仍不变
        Map<String, Object> adj3 = given().contentType(ContentType.JSON)
                .body(Map.of("ownerCode", owner, "month", "2026-09",
                        "lines", List.of(Map.of("bagCode", codes.get(0),
                                "correctionType", "WRONG_COLLECTION", "reason", "业主核对未清运"))))
                .post("/api/bills/adjust")
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
        assertEquals(0L, fen(adj3), "补收与退费在补差单内轧差");
        assertEquals(2, ((List<?>) adj3.get("adjustmentLines")).size());

        // 再退一次同一袋 -> 幂等拒绝重复退费，合计保持 0
        Map<String, Object> adj4 = given().contentType(ContentType.JSON)
                .body(Map.of("ownerCode", owner, "month", "2026-09",
                        "lines", List.of(Map.of("bagCode", codes.get(0),
                                "correctionType", "WRONG_COLLECTION", "reason", "再次提交"))))
                .post("/api/bills/adjust")
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
        assertEquals(0L, fen(adj4), "同一错收不能重复退费");
        assertEquals(2, ((List<?>) adj4.get("adjustmentLines")).size());

        // 原账单从头到尾没变过
        Map<String, Object> originalFinal = given().get("/api/bills/" + originalId)
                .then().statusCode(200).extract().jsonPath().getMap("");
        assertEquals(1600L, fen(originalFinal));
        assertEquals(paymentRef, ((Map<?, ?>) originalFinal.get("bill")).get("paymentRef"));

        // 重复结算原账单也不应产生第二笔扣款（流水号不变）
        Map<String, Object> resettle = given().contentType(ContentType.JSON)
                .body(Map.of("paidVia", "本地模拟支付"))
                .post("/api/bills/" + originalId + "/settle")
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
        assertEquals(paymentRef, ((Map<?, ?>) resettle.get("bill")).get("paymentRef"),
                "重复支付不能二次扣款");
    }
}

package org.example.wastebag.resource;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.example.wastebag.service.PaymentService;

import java.math.BigDecimal;

/** 本地模拟支付接口 */
@Path("/api/payments")
public class PaymentResource {

    private final PaymentService paymentService;

    public PaymentResource(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public record PayIn(Long billId, Long adjustmentId, BigDecimal amount) {}

    @POST
    public PaymentService.PayResult pay(PayIn in) {
        if (in == null || (in.billId() == null && in.adjustmentId() == null)) {
            throw new WebApplicationException("须指定 billId 或 adjustmentId", Response.Status.BAD_REQUEST);
        }
        if (in.billId() != null) {
            return paymentService.payBill(in.billId(), in.amount());
        }
        return paymentService.payAdjustment(in.adjustmentId(), in.amount());
    }
}

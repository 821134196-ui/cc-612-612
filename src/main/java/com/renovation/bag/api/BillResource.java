package com.renovation.bag.api;

import com.renovation.bag.api.dto.*;
import com.renovation.bag.service.BillingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/bills")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BillResource {

    @Inject BillingService billing;

    /** 业主账单列表（正式单 + 补差单） */
    @GET
    public List<BillDto> list(@QueryParam("ownerCode") String ownerCode) {
        return billing.listBills(ownerCode);
    }

    /** 取某月账单并实时核对（OPEN 会按最新清运/遗失重算） */
    @GET
    @Path("/monthly")
    public BillDetailDto monthly(@QueryParam("ownerCode") String ownerCode,
                                 @QueryParam("month") String month) {
        return billing.monthlyBill(ownerCode, month);
    }

    @GET
    @Path("/{id}")
    public BillDetailDto detail(@PathParam("id") Long id) {
        return billing.detailById(id);
    }

    /** 已结算月份差错更正 -> 补差单，原账单不变 */
    @POST
    @Path("/adjust")
    public BillDetailDto adjust(AdjustmentRequest req) {
        return billing.adjust(req.ownerCode, req.month, req.lines);
    }

    /** 本地模拟支付结算 */
    @POST
    @Path("/{id}/settle")
    public BillDetailDto settle(@PathParam("id") Long id, SettleRequest req) {
        return billing.settle(id, req == null ? null : req.paidVia);
    }
}

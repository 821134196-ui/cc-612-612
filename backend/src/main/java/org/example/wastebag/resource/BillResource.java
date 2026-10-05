package org.example.wastebag.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.example.wastebag.model.Adjustment;
import org.example.wastebag.model.Bill;
import org.example.wastebag.model.BillItem;
import org.example.wastebag.model.BillStatus;
import org.example.wastebag.model.ItemType;
import org.example.wastebag.service.BillingService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Path("/api/bills")
public class BillResource {

    private final BillingService billingService;

    public BillResource(BillingService billingService) {
        this.billingService = billingService;
    }

    public record GenerateIn(String month, Long ownerId) {}
    public record AdjustmentIn(String reason, List<BillingService.ManualItem> items) {}

    public record ItemView(long id, ItemType type, String bagNo, BigDecimal amount, String description) {
        static ItemView of(BillItem i) {
            return new ItemView(i.id, i.type, i.bagNo, i.amount, i.description);
        }
    }

    public record AdjustmentView(long id, String reason, String status,
                                 BigDecimal total, List<ItemView> items) {}

    public record BillView(long id, long ownerId, String ownerName, String unit,
                           String month, BillStatus status, BigDecimal total,
                           List<ItemView> items, List<AdjustmentView> adjustments) {}

    /** 出账：把账期内未入账的清运/报失事件转成费用明细；幂等，可重复调用 */
    @POST
    @Path("/generate")
    public BillingService.GenerateResult generate(GenerateIn in) {
        return billingService.generate(in.month(), in.ownerId());
    }

    @GET
    public List<BillView> list(@QueryParam("ownerId") Long ownerId, @QueryParam("month") String month) {
        List<Bill> bills;
        if (ownerId != null && month != null) {
            Bill b = Bill.findByOwnerAndMonth(ownerId, month);
            bills = b == null ? List.of() : List.of(b);
        } else if (ownerId != null) {
            bills = Bill.list("owner.id = ?1 order by month, id", ownerId);
        } else if (month != null) {
            bills = Bill.list("month = ?1 order by id", month);
        } else {
            bills = Bill.list("order by month, id");
        }
        return bills.stream().map(this::view).toList();
    }

    @GET
    @Path("/{id}")
    public BillView get(@PathParam("id") long id) {
        return view(billingService.requireBill(id));
    }

    /** 结算：账单冻结，之后的差错只能走补差单 */
    @POST
    @Path("/{id}/settle")
    public BillView settle(@PathParam("id") long id) {
        return view(billingService.settle(id));
    }

    /** 已结算账单的差错更正：生成补差单，原账单不变 */
    @POST
    @Path("/{id}/adjustments")
    public AdjustmentView adjust(@PathParam("id") long id, AdjustmentIn in) {
        Adjustment adj = billingService.createAdjustment(id, in.reason(), in.items());
        return adjustmentView(adj);
    }

    BillView view(Bill b) {
        List<ItemView> items = BillItem.ofBill(b.id).stream().map(ItemView::of).toList();
        List<AdjustmentView> adjs = billingService.adjustmentsOf(b.id).stream()
                .map(this::adjustmentView).toList();
        return new BillView(b.id, b.owner.id, b.owner.name, b.owner.unit, b.month, b.status,
                billingService.totalOfBill(b.id), items, adjs);
    }

    AdjustmentView adjustmentView(Adjustment a) {
        List<ItemView> items = BillItem.ofAdjustment(a.id).stream().map(ItemView::of).toList();
        return new AdjustmentView(a.id, a.reason, a.status.name(),
                billingService.totalOfAdjustment(a.id), items);
    }

    /** 业主对账：领用/退袋/清运/报失并列展示 */
    @GET
    @Path("/statement")
    public Map<String, Object> statement(@QueryParam("ownerId") long ownerId,
                                         @QueryParam("month") String month) {
        return billingService.statement(ownerId, month);
    }
}

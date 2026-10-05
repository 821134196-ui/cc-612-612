package com.renovation.bag.api.dto;

import com.renovation.bag.domain.Bill;

public class BillDto {
    public Long id;
    public String billNo;
    public String ownerCode;
    public String ownerName;
    public String room;
    public String month;
    public String billType;
    public String status;
    public long amountFen;
    public Long originalBillId;
    public String originalBillNo;
    public String reason;
    public Long createdAt;
    public Long settledAt;
    public String paymentRef;

    public static BillDto of(Bill b) {
        BillDto d = new BillDto();
        d.id = b.getId();
        d.billNo = b.getBillNo();
        d.ownerCode = b.getOwner().getCode();
        d.ownerName = b.getOwner().getName();
        d.room = b.getOwner().getRoom();
        d.month = b.getMonth();
        d.billType = b.getBillType().name();
        d.status = b.getStatus().name();
        d.amountFen = b.getAmountFen();
        if (b.getOriginalBill() != null) {
            d.originalBillId = b.getOriginalBill().getId();
            d.originalBillNo = b.getOriginalBill().getBillNo();
        }
        d.reason = b.getReason();
        d.createdAt = b.getCreatedAt();
        d.settledAt = b.getSettledAt();
        d.paymentRef = b.getPaymentRef();
        return d;
    }
}

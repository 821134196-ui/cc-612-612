package com.renovation.bag.repo;

import com.renovation.bag.domain.Bill;
import com.renovation.bag.domain.BillItem;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class BillItemRepository implements PanacheRepository<BillItem> {

    public List<BillItem> findByBill(Long billId) {
        return list("bill.id", billId);
    }

    public boolean existsByBagCode(String bagCode) {
        return count("bagCode", bagCode) > 0;
    }

    public List<BillItem> findByBillAndBag(Bill bill, String bagCode) {
        return list("bill = ?1 and bagCode = ?2", bill, bagCode);
    }
}

package com.renovation.bag.repo;

import com.renovation.bag.domain.AdjustmentLine;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AdjustmentLineRepository implements PanacheRepository<AdjustmentLine> {

    public boolean exists(Long originalBillId, String bagCode, String correctionType) {
        return count("originalBill.id = ?1 and bagCode = ?2 and correctionType = ?3",
                originalBillId, bagCode, correctionType) > 0;
    }
}

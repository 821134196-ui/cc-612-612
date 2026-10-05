package com.renovation.bag.repo;

import com.renovation.bag.domain.Bill;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class BillRepository implements PanacheRepository<Bill> {

    public Bill findRegular(Long ownerId, String month) {
        return find("owner.id = ?1 and month = ?2 and billType = ?3",
                ownerId, month, Bill.Type.REGULAR).firstResult();
    }

    public List<Bill> findByOwner(Long ownerId) {
        return list("owner.id order by month desc, billType, id", ownerId);
    }
}

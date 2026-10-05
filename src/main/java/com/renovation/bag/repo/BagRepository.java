package com.renovation.bag.repo;

import com.renovation.bag.domain.BagRecord;
import com.renovation.bag.domain.BagStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class BagRepository implements PanacheRepository<BagRecord> {

    public BagRecord findByCode(String bagCode) {
        return find("bagCode", bagCode).firstResult();
    }

    public long countByOwnerAndStatus(Long ownerId, BagStatus status) {
        return count("owner.id = ?1 and status = ?2", ownerId, status);
    }

    public List<BagRecord> findByOwner(Long ownerId) {
        return list("owner.id", ownerId);
    }
}

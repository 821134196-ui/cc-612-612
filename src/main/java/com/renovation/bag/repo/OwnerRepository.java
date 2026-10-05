package com.renovation.bag.repo;

import com.renovation.bag.domain.Owner;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OwnerRepository implements PanacheRepository<Owner> {
    public Owner findByCode(String code) {
        return find("code", code).firstResult();
    }
}

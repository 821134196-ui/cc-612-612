package com.renovation.bag.support;

import com.renovation.bag.domain.Owner;
import com.renovation.bag.repo.OwnerRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** 首次启动写入演示业主 */
@ApplicationScoped
public class DataSeeder {

    @Inject OwnerRepository owners;

    @Transactional
    void seed(@Observes StartupEvent ev) {
        if (owners.count() == 0) {
            owners.persist(new Owner("101", "张先生", "3栋101室"));
            owners.persist(new Owner("102", "李女士", "3栋102室"));
            owners.persist(new Owner("201", "王先生", "5栋201室"));
        }
    }
}

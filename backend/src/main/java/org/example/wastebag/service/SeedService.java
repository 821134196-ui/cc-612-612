package org.example.wastebag.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.example.wastebag.model.Owner;

/** 首次启动写入演示业主，便于开箱试用 */
@ApplicationScoped
public class SeedService {

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (Owner.count() == 0) {
            seed("张三", "1-1-101", "13800000001");
            seed("李四", "1-1-102", "13800000002");
            seed("王五", "2-3-501", "13800000003");
        }
    }

    private void seed(String name, String unit, String phone) {
        Owner o = new Owner();
        o.name = name;
        o.unit = unit;
        o.phone = phone;
        o.persist();
    }
}

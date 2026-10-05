package com.renovation.bag.repo;

import com.renovation.bag.domain.ScanLog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ScanLogRepository implements PanacheRepository<ScanLog> {
}

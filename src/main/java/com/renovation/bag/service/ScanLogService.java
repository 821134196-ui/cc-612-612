package com.renovation.bag.service;

import com.renovation.bag.domain.ScanLog;
import com.renovation.bag.repo.ScanLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ScanLogService {

    @Inject ScanLogRepository repo;

    @Transactional(Transactional.TxType.MANDATORY)
    public void log(String bagCode, String collector, Instant at, String result, String detail) {
        repo.persist(new ScanLog(bagCode, collector, at.toEpochMilli(), result, detail));
    }

    public List<ScanLog> recent(int limit) {
        return repo.find("order by scannedAt desc").page(0, limit).list();
    }

    public long countByBagAndResult(String bagCode, String result) {
        return repo.count("bagCode = ?1 and result = ?2", bagCode, result);
    }
}

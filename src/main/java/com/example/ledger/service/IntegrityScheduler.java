package com.example.ledger.service;

import com.example.ledger.dto.IntegrityReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically reconciles the ledger and shouts if anything is off. */
@Component
public class IntegrityScheduler {

    private static final Logger log = LoggerFactory.getLogger(IntegrityScheduler.class);

    private final IntegrityService integrityService;

    public IntegrityScheduler(IntegrityService integrityService) {
        this.integrityService = integrityService;
    }

    @Scheduled(initialDelayString = "${ledger.integrity-check.interval}",
            fixedDelayString = "${ledger.integrity-check.interval}")
    public void verify() {
        IntegrityReport report = integrityService.check();
        if (report.consistent()) {
            log.info("Ledger integrity OK ({} accounts checked)", report.accountsChecked());
        } else {
            log.error("LEDGER INTEGRITY VIOLATION: totalBalance={}, discrepancies={}, unbalancedTransactions={}",
                    report.totalBalance(), report.discrepancies(), report.unbalancedTransactions());
        }
    }
}

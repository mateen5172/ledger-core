package com.example.ledger.controller;

import com.example.ledger.dto.IntegrityReport;
import com.example.ledger.service.IntegrityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
@Tag(name = "Ledger")
public class LedgerController {

    private final IntegrityService integrityService;

    public LedgerController(IntegrityService integrityService) {
        this.integrityService = integrityService;
    }

    /** Reconciles cached balances against the ledger and verifies that all books balance. */
    @GetMapping("/integrity")
    public IntegrityReport integrity() {
        return integrityService.check();
    }
}

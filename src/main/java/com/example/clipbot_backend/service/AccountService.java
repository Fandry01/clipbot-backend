package com.example.clipbot_backend.service;

import com.example.clipbot_backend.config.PlansProperties;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.repository.AccountRepository;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accountRepo;
    private final PlansProperties plansProperties;
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    public AccountService(AccountRepository accountRepo, PlansProperties plansProperties) {
        this.accountRepo = accountRepo;
        this.plansProperties = plansProperties;
    }
    public Account ensureByExternalSubject(String externalSubject, @Nullable String displayName) {
        var normalized = externalSubject.trim();
        return accountRepo.findByExternalSubject(normalized)
                .orElseGet(() -> {
                    log.info("Creating new Account for externalSubject={}", normalized);
                    Account acc = new Account(
                            normalized,
                            displayName != null ? displayName : "User"
                    );
                    if (plansProperties.getTrialDays() > 0) {
                        acc.setTrialEndsAt(Instant.now().plus(plansProperties.getTrialDays(), ChronoUnit.DAYS));
                    }
                    return accountRepo.save(acc);
                });
    }
    @Transactional(readOnly = true)
    public Account getByExternalSubjectOrThrow(String externalSubject) {
        return accountRepo.findByExternalSubject(externalSubject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OWNER_NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public Account getByIdOrThrow(UUID id) {
        return accountRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OWNER_NOT_FOUND"));
    }
}

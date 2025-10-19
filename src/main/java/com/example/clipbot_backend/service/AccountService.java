package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.repository.AccountRepository;
import jakarta.annotation.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accountRepo;

    public AccountService(AccountRepository accountRepo) {
        this.accountRepo = accountRepo;
    }
    public Account ensureByExternalSubject(String externalSubject, @Nullable String displayName) {
        return accountRepo.findByExternalSubject(externalSubject)
                .orElseGet(() -> accountRepo.save(new Account(
                        externalSubject,
                        displayName != null ? displayName : "User"
                )));
    }

    public Account getByExternalSubjectOrThrow(String externalSubject) {
        return accountRepo.findByExternalSubject(externalSubject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OWNER_NOT_FOUND"));
    }

    public Account getByIdOrThrow(UUID id) {
        return accountRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OWNER_NOT_FOUND"));
    }
}

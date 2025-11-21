package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.PlanLimits;
import com.example.clipbot_backend.service.AccountService;
import com.example.clipbot_backend.service.PlanConfigService;
import com.example.clipbot_backend.service.UsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes account entitlement and usage information for UI consumption.
 */
@RestController
@RequestMapping("/v1/account")
public class AccountUsageController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountUsageController.class);
    private final AccountService accountService;
    private final PlanConfigService planConfigService;
    private final UsageService usageService;

    public AccountUsageController(AccountService accountService, PlanConfigService planConfigService, UsageService usageService) {
        this.accountService = accountService;
        this.planConfigService = planConfigService;
        this.usageService = usageService;
    }

    /**
     * Returns entitlement information for the requested account.
     *
     * @param ownerExternalSubject account identifier
     * @return entitlement DTO
     */
    @GetMapping("/entitlements")
    public EntitlementsResponse entitlements(@RequestParam String ownerExternalSubject) {
        Account account = ensureOwned(ownerExternalSubject);
        PlanLimits limits = planConfigService.getLimits(account.getPlanTier());
        LOGGER.info("AccountUsageController entitlements owner={} plan={}", account.getId(), account.getPlanTier());
        return new EntitlementsResponse(account.getPlanTier().name(), account.getTrialEndsAt(), limits.getMaxRendersDay(), limits.getMaxRendersMonth(), limits.isAllow1080p(), limits.isAllow4k(), limits.isWatermark());
    }

    /**
     * Returns the current usage snapshot for the requested account.
     *
     * @param ownerExternalSubject account identifier
     * @return usage DTO
     */
    @GetMapping("/usage")
    public UsageResponse usage(@RequestParam String ownerExternalSubject) {
        Account account = ensureOwned(ownerExternalSubject);
        UsageService.UsageSnapshot snapshot = usageService.getUsage(account);
        LOGGER.info("AccountUsageController usage owner={} day={} month={}", account.getId(), snapshot.rendersToday(), snapshot.rendersMonth());
        return new UsageResponse(snapshot.rendersToday(), snapshot.rendersMonth(), snapshot.dateKey().toString());
    }

    private Account ensureOwned(String ownerExternalSubject) {
        return accountService.getByExternalSubjectOrThrow(ownerExternalSubject);
    }

    /**
     * DTO for entitlement payload.
     *
     * @param planTier plan tier name
     * @param trialEndsAt trial end instant
     * @param maxRendersDay maximum renders per day
     * @param maxRendersMonth maximum renders per month
     * @param allow1080p whether 1080p is allowed
     * @param allow4k whether 4k is allowed
     * @param watermark whether watermarking is enabled
     */
    public record EntitlementsResponse(String planTier, java.time.Instant trialEndsAt, int maxRendersDay, int maxRendersMonth, boolean allow1080p, boolean allow4k, boolean watermark) { }

    /**
     * DTO for usage payload.
     *
     * @param rendersToday renders performed today
     * @param rendersMonth renders performed in the current month
     * @param dateKey UTC date string
     */
    public record UsageResponse(int rendersToday, int rendersMonth, String dateKey) { }
}

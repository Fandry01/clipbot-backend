package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.RenderSpec;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.PlanLimits;
import com.example.clipbot_backend.model.PlanTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * Validates render eligibility and produces render policies based on plan configuration and usage.
 */
@Service
public class EntitlementService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntitlementService.class);
    private final PlanConfigService planConfigService;
    private final UsageService usageService;

    public EntitlementService(PlanConfigService planConfigService, UsageService usageService) {
        this.planConfigService = planConfigService;
        this.usageService = usageService;
    }

    /**
     * Determines whether the provided account can render the requested spec.
     *
     * @param account account owner
     * @param requestedSpec requested render spec (nullable)
     * @return render policy indicating allow/deny, enforced profile and watermark state
     */
    public RenderPolicy checkCanRender(Account account, RenderSpec requestedSpec) {
        Objects.requireNonNull(account, "account");
        PlanTier tier = account.getPlanTier() == null ? PlanTier.TRIAL : account.getPlanTier();
        if (tier == PlanTier.TRIAL && account.getTrialEndsAt() != null && account.getTrialEndsAt().isBefore(Instant.now())) {
            LOGGER.info("EntitlementService deny account={} plan={} reason=TRIAL_EXPIRED", account.getId(), tier);
            return new RenderPolicy(false, "TRIAL_EXPIRED", true, "youtube-720p");
        }
        PlanLimits limits = planConfigService.getLimits(tier);
        UsageService.UsageSnapshot usage = usageService.getUsage(account);
        if (usage.rendersToday() >= limits.getMaxRendersDay()) {
            LOGGER.info("EntitlementService deny account={} plan={} reason=QUOTA_EXCEEDED today={}", account.getId(), tier, usage.rendersToday());
            LOGGER.debug("EntitlementService limits day={} month={}", limits.getMaxRendersDay(), limits.getMaxRendersMonth());
            return new RenderPolicy(false, "QUOTA_EXCEEDED", limits.isWatermark(), null);
        }
        if (usage.rendersMonth() >= limits.getMaxRendersMonth()) {
            LOGGER.info("EntitlementService deny account={} plan={} reason=QUOTA_EXCEEDED month={}", account.getId(), tier, usage.rendersMonth());
            LOGGER.debug("EntitlementService limits day={} month={}", limits.getMaxRendersDay(), limits.getMaxRendersMonth());
            return new RenderPolicy(false, "QUOTA_EXCEEDED", limits.isWatermark(), null);
        }
        if (requestedSpec != null && requestedSpec.height() != null) {
            int height = requestedSpec.height();
            if (height > 1080 && !limits.isAllow4k()) {
                LOGGER.info("EntitlementService deny account={} plan={} reason=UPGRADE_REQUIRED requestedHeight={}", account.getId(), tier, height);
                return new RenderPolicy(false, "UPGRADE_REQUIRED", limits.isWatermark(), null);
            }
            if (height > 720 && !limits.isAllow1080p()) {
                LOGGER.info("EntitlementService deny account={} plan={} reason=UPGRADE_REQUIRED requestedHeight={}", account.getId(), tier, height);
                return new RenderPolicy(false, "UPGRADE_REQUIRED", limits.isWatermark(), null);
            }
        }
        boolean watermark = tier == PlanTier.TRIAL || limits.isWatermark();
        String forcedProfile = tier == PlanTier.TRIAL ? "youtube-720p" : null;
        LOGGER.info("EntitlementService allow account={} plan={} watermark={} forcedProfile={}", account.getId(), tier, watermark, forcedProfile);
        LOGGER.debug("EntitlementService usage today={} month={} limitsDay={} limitsMonth={}", usage.rendersToday(), usage.rendersMonth(), limits.getMaxRendersDay(), limits.getMaxRendersMonth());
        return new RenderPolicy(true, null, watermark, forcedProfile);
    }

    /**
     * Burns one render unit for the given account.
     *
     * @param account account owner
     */
    public void burnOneRender(Account account) {
        usageService.incrementRenders(account);
    }

    /**
     * Immutable render policy DTO.
     *
     * @param allow whether rendering is permitted
     * @param reason optional denial code
     * @param watermark whether watermarking must be applied
     * @param forcedProfile profile override when not null
     */
    public record RenderPolicy(boolean allow, String reason, boolean watermark, String forcedProfile) {
    }
}

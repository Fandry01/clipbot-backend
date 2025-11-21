package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.RenderSpec;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.PlanLimits;
import com.example.clipbot_backend.model.PlanTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * Validates render eligibility and produces render policies based on plan configuration and usage.
 */
@Service
public class EntitlementService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntitlementService.class);

    private final PlanConfigService plans;
    private final UsageService usage;

    public record Decision(boolean allow, String reason,
                           String forcedProfile, boolean watermark) {

    }

    public EntitlementService(PlanConfigService plans, UsageService usage) {
        this.plans = plans;
        this.usage = usage;
    }

    public Decision checkCanRender(Account acc, @Nullable String requestedProfile) {
        if (acc.isAdmin()) {
            // Admin: alles mag, geen watermark, respecteer aangevraagd profiel (fallback 1080p)
            String prof = (requestedProfile == null || requestedProfile.isBlank())
                    ? "youtube-1080p" : requestedProfile;
            return new Decision(true, "ADMIN_OK", prof, false);
        }

        var limits = plans.getLimits(acc.getPlanTier());
        var snap = usage.getUsage(acc);

        // Trial verlopen?
        if (acc.getPlanTier() == PlanTier.TRIAL && acc.getTrialEndsAt() != null
                && acc.getTrialEndsAt().isBefore(Instant.now())) {
            return new Decision(false, "TRIAL_EXPIRED", null, true);
        }
        // Quota
        if (snap.rendersToday() >= limits.getMaxRendersDay()) {
            return new Decision(false, "QUOTA_DAY_EXCEEDED", null, limits.isWatermark());
        }
        if (snap.rendersMonth() >= limits.getMaxRendersMonth()) {
            return new Decision(false, "QUOTA_MONTH_EXCEEDED", null, limits.isWatermark());
        }

        // Profiel afdwingen (max 720 op TRIAL als 1080 niet toegestaan)
        String forced = requestedProfile;
        if (!limits.isAllow1080p()) {
            forced = "youtube-720p";
        }
        return new Decision(true, "OK", forced, limits.isWatermark());
    }

    /** Call direct na enqueue, niet pas na render-complete. */
    public void burnOneRender(Account acc) {
        if (acc.isAdmin()) return; // admin telt niet mee
        usage.incrementRenders(acc);
    }
}
package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.PlanLimits;
import com.example.clipbot_backend.model.PlanTier;
import com.example.clipbot_backend.repository.PlanLimitsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * Resolves plan configuration from the {@code plan_limits} lookup table with sensible fallbacks.
 */
@Service
public class PlanConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanConfigService.class);
    private final PlanLimitsRepository planLimitsRepository;
    private final Map<PlanTier, PlanLimits> defaultLimits = new EnumMap<>(PlanTier.class);

    public PlanConfigService(PlanLimitsRepository planLimitsRepository) {
        this.planLimitsRepository = planLimitsRepository;
        defaultLimits.put(PlanTier.TRIAL, buildDefaults("TRIAL", 50, 60, false, false, true));
        defaultLimits.put(PlanTier.STARTER, buildDefaults("STARTER", 40, 400, true, false, false));
        defaultLimits.put(PlanTier.PRO, buildDefaults("PRO", 120, 1200, true, false, false));
    }

    /**
     * Loads the configured limits for a given tier, logging when a fallback is used.
     *
     * @param tier account tier
     * @return resolved limits (never {@code null}).
     */
    public PlanLimits getLimits(PlanTier tier) {
        return planLimitsRepository.findById(tier.name())
                .orElseGet(() -> {
                    LOGGER.info("PlanConfigService fallback plan={} reason=missing_lookup", tier);
                    return defaultLimits.getOrDefault(tier, defaultLimits.get(PlanTier.TRIAL));
                });
    }

    private static PlanLimits buildDefaults(String name, int maxDay, int maxMonth, boolean allow1080p, boolean allow4k, boolean watermark) {
        PlanLimits limits = new PlanLimits();
        try {
            java.lang.reflect.Field planField = PlanLimits.class.getDeclaredField("plan");
            planField.setAccessible(true);
            planField.set(limits, name);
            setInt(limits, "maxRendersDay", maxDay);
            setInt(limits, "maxRendersMonth", maxMonth);
            setBool(limits, "allow1080p", allow1080p);
            setBool(limits, "allow4k", allow4k);
            setBool(limits, "watermark", watermark);
        } catch (Exception ignore) {
            // reflectively populate simple record-like entity
        }
        return limits;
    }

    private static void setInt(PlanLimits target, String field, int value) throws Exception {
        var f = PlanLimits.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void setBool(PlanLimits target, String field, boolean value) throws Exception {
        var f = PlanLimits.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}

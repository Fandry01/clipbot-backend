package com.example.clipbot_backend.service;

import com.example.clipbot_backend.config.BrandProperties;
import com.example.clipbot_backend.dto.RenderSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Resolves the effective render profile and watermark settings based on entitlements.
 */
@Component
public class RenderProfileResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderProfileResolver.class);
    private final BrandProperties brandProperties;

    public RenderProfileResolver(BrandProperties brandProperties) {
        this.brandProperties = brandProperties;
    }

    /**
     * Applies the provided render policy to the requested spec, forcing watermark and profile when applicable.
     *
     * @param requested initial request spec (nullable)
     * @param policy render policy describing constraints
     * @return resolved spec with enforced watermark and profile
     */
    public RenderSpec resolve(RenderSpec requested, EntitlementService.RenderPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        RenderSpec base = requested != null ? requested : RenderSpec.DEFAULT;
        String profile = policy.forcedProfile() != null ? policy.forcedProfile() : base.profile();
        Boolean watermarkEnabled = policy.watermark();
        String watermarkPath = watermarkEnabled ? brandProperties.getWatermarkPath() : null;
        RenderSpec resolved = new RenderSpec(
                base.width(),
                base.height(),
                base.fps(),
                base.crf(),
                base.preset(),
                profile,
                watermarkEnabled,
                watermarkPath
        );
        LOGGER.debug("RenderProfileResolver resolved profile={} watermark={} path={}", profile, watermarkEnabled, watermarkPath);
        return resolved;
    }
}

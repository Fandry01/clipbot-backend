package com.example.clipbot_backend.service;

import com.example.clipbot_backend.config.BrandProperties;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.PlanLimits;
import com.example.clipbot_backend.model.PlanTier;
import com.example.clipbot_backend.model.UsageCounters;
import com.example.clipbot_backend.repository.PlanLimitsRepository;
import com.example.clipbot_backend.repository.UsageCountersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * Basic entitlement and render policy tests to verify gating rules.
 */
public class EntitlementServiceTest {
    private UsageCountersRepository usageRepo;
    private PlanLimitsRepository planRepo;
    private UsageService usageService;
    private PlanConfigService planConfigService;
    private EntitlementService entitlementService;

    @BeforeEach
    void setup() {
        usageRepo = Mockito.mock(UsageCountersRepository.class);
        planRepo = Mockito.mock(PlanLimitsRepository.class);
        usageService = new UsageService(usageRepo);
        planConfigService = new PlanConfigService(planRepo);
        entitlementService = new EntitlementService(planConfigService, usageService);
    }

    @Test
    void trialWithExpiredDateIsDenied() {
        Account account = new Account("ext", "User");
        account.setPlanTier(PlanTier.TRIAL);
        account.setTrialEndsAt(Instant.now().minusSeconds(3600));
        Mockito.when(usageRepo.findByAccountAndDateKey(any(), any())).thenReturn(Optional.empty());
        Mockito.when(usageRepo.sumRendersMonth(any(), any())).thenReturn(0);

        EntitlementService.RenderPolicy policy = entitlementService.checkCanRender(account, null);
        assertThat(policy.allow()).isFalse();
        assertThat(policy.reason()).isEqualTo("TRIAL_EXPIRED");
    }

    @Test
    void dailyQuotaExceededIsDenied() throws Exception {
        Account account = new Account("ext", "User");
        account.setPlanTier(PlanTier.TRIAL);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UsageCounters counters = new UsageCounters(account, today, today.withDayOfMonth(1));
        counters.setRendersToday(10);
        Mockito.when(usageRepo.findByAccountAndDateKey(any(), any())).thenReturn(Optional.of(counters));
        Mockito.when(usageRepo.sumRendersMonth(any(), any())).thenReturn(10);

        PlanLimits limits = createPlanLimits("TRIAL",10,60,true);
        Mockito.when(planRepo.findById("TRIAL")).thenReturn(Optional.of(limits));

        EntitlementService.RenderPolicy policy = entitlementService.checkCanRender(account, null);
        assertThat(policy.allow()).isFalse();
        assertThat(policy.reason()).isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    void trialForcesProfileAndWatermark() throws Exception {
        Account account = new Account("ext", "User");
        account.setPlanTier(PlanTier.TRIAL);
        account.setTrialEndsAt(Instant.now().plusSeconds(3600));
        Mockito.when(usageRepo.findByAccountAndDateKey(any(), any())).thenReturn(Optional.empty());
        Mockito.when(usageRepo.sumRendersMonth(any(), any())).thenReturn(0);
        PlanLimits limits = createPlanLimits("TRIAL",10,60,true);
        Mockito.when(planRepo.findById("TRIAL")).thenReturn(Optional.of(limits));

        var policy = entitlementService.checkCanRender(account, null);
        BrandProperties brandProperties = new BrandProperties();
        RenderProfileResolver resolver = new RenderProfileResolver(brandProperties);
        var resolved = resolver.resolve(null, policy);
        assertThat(policy.allow()).isTrue();
        assertThat(resolved.profile()).isEqualTo("youtube-720p");
        assertThat(resolved.watermarkEnabled()).isTrue();
        assertThat(resolved.watermarkPath()).isEqualTo(brandProperties.getWatermarkPath());
    }

    private PlanLimits createPlanLimits(String name, int maxDay, int maxMonth, boolean watermark) throws Exception {
        PlanLimits limits = new PlanLimits();
        var planF = PlanLimits.class.getDeclaredField("plan");
        planF.setAccessible(true);
        planF.set(limits, name);
        var dayF = PlanLimits.class.getDeclaredField("maxRendersDay");
        dayF.setAccessible(true);
        dayF.set(limits, maxDay);
        var monthF = PlanLimits.class.getDeclaredField("maxRendersMonth");
        monthF.setAccessible(true);
        monthF.set(limits, maxMonth);
        var wmark = PlanLimits.class.getDeclaredField("watermark");
        wmark.setAccessible(true);
        wmark.set(limits, watermark);
        var allow1080p = PlanLimits.class.getDeclaredField("allow1080p");
        allow1080p.setAccessible(true);
        allow1080p.set(limits, true);
        var allow4k = PlanLimits.class.getDeclaredField("allow4k");
        allow4k.setAccessible(true);
        allow4k.set(limits, false);
        return limits;
    }
}

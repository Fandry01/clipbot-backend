package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.UsageCounters;
import com.example.clipbot_backend.repository.UsageCountersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * Unit tests for {@link UsageService} to ensure counters increment correctly.
 */
class UsageServiceTest {
    private UsageCountersRepository usageRepo;
    private UsageService usageService;

    @BeforeEach
    void setup() {
        usageRepo = Mockito.mock(UsageCountersRepository.class);
        usageService = new UsageService(usageRepo);
    }

    @Test
    void incrementCreatesNewCountersAndUpdatesDayAndMonth() {
        Account account = new Account("ext", "User");
        Mockito.when(usageRepo.findByAccountAndDateKey(any(), any())).thenReturn(Optional.empty());
        Mockito.when(usageRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        usageService.incrementRenders(account);

        ArgumentCaptor<UsageCounters> captor = ArgumentCaptor.forClass(UsageCounters.class);
        Mockito.verify(usageRepo).save(captor.capture());
        UsageCounters saved = captor.getValue();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(saved.getDateKey()).isEqualTo(today);
        assertThat(saved.getMonthKey()).isEqualTo(today.withDayOfMonth(1));
        assertThat(saved.getRendersToday()).isEqualTo(1);
        assertThat(saved.getRendersMonth()).isEqualTo(1);
    }

    @Test
    void incrementUpdatesExistingCounters() {
        Account account = new Account("ext", "User");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UsageCounters existing = new UsageCounters(account, today, today.withDayOfMonth(1));
        existing.setRendersToday(2);
        existing.setRendersMonth(5);
        Mockito.when(usageRepo.findByAccountAndDateKey(any(), any())).thenReturn(Optional.of(existing));
        Mockito.when(usageRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        usageService.incrementRenders(account);

        ArgumentCaptor<UsageCounters> captor = ArgumentCaptor.forClass(UsageCounters.class);
        Mockito.verify(usageRepo).save(captor.capture());
        UsageCounters saved = captor.getValue();
        assertThat(saved.getRendersToday()).isEqualTo(3);
        assertThat(saved.getRendersMonth()).isEqualTo(6);
    }
}

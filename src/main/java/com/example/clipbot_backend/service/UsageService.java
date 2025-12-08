package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.UsageCounters;
import com.example.clipbot_backend.repository.UsageCountersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Tracks render usage for accounts.
 */
@Service
public class UsageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UsageService.class);
    private final UsageCountersRepository usageCountersRepository;

    public UsageService(UsageCountersRepository usageCountersRepository) {
        this.usageCountersRepository = usageCountersRepository;
    }

    /**
     * Returns the usage snapshot for the given account.
     *
     * @param account account owner
     * @return immutable snapshot
     */
    @Transactional(readOnly = true)
    public UsageSnapshot getUsage(Account account) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate monthKey = today.withDayOfMonth(1);
        UsageCounters todayCounters = usageCountersRepository.findByAccountAndDateKey(account, today).orElse(null);
        int rendersToday = todayCounters != null ? todayCounters.getRendersToday() : 0;
        int rendersMonth = usageCountersRepository.sumRendersMonth(account, monthKey);
        LOGGER.debug("UsageService usage account={} today={} month={}", account.getId(), rendersToday, rendersMonth);
        return new UsageSnapshot(rendersToday, rendersMonth, today);
    }

    /**
     * Increments the render counters atomically within a transaction.
     *
     * @param account account owner
     */
    @Transactional
    public void incrementRenders(Account account) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate monthKey = today.withDayOfMonth(1);
        UsageCounters counters = usageCountersRepository.findByAccountAndDateKey(account, today)
                .orElseGet(() -> new UsageCounters(account, today, monthKey));
        counters.setRendersToday(counters.getRendersToday() + 1);
        counters.setRendersMonth(counters.getRendersMonth() + 1);
        usageCountersRepository.save(counters);
        LOGGER.info("UsageService increment account={} rendersToday={} rendersMonth={}", account.getId(), counters.getRendersToday(), counters.getRendersMonth());
    }

    /**
     * Snapshot DTO for current usage.
     *
     * @param rendersToday renders performed today
     * @param rendersMonth renders performed in the current month
     * @param dateKey UTC date key
     */
    public record UsageSnapshot(int rendersToday, int rendersMonth, LocalDate dateKey) {
    }
}

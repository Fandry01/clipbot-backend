package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.UsageCounters;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for usage counter records.
 */
public interface UsageCountersRepository extends JpaRepository<UsageCounters, UUID> {
    Optional<UsageCounters> findByAccountAndDateKey(Account account, LocalDate dateKey);

    @Query("select coalesce(sum(u.rendersMonth), 0) " +
                  "from UsageCounters u " +
                  "where u.account = :account and u.monthKey = :monthKey")
    int sumRendersMonth(@Param("account") Account account, @Param("monthKey") LocalDate monthKey);
    @Query("""
    select u.monthKey as month, coalesce(sum(u.rendersToday), 0) as total
    from UsageCounters u
    where u.account = :account
    group by u.monthKey
    order by u.monthKey desc
    """)
    List<Object[]> monthlyRollup(Account account);
}

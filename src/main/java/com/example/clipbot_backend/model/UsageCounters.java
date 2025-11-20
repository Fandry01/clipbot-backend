package com.example.clipbot_backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Usage counters for daily and monthly render tracking per account.
 */
@Entity
@Table(name = "usage_counters", uniqueConstraints = @UniqueConstraint(name = "ux_usage_counters", columnNames = {"account_id", "date_key"}))
public class UsageCounters {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", foreignKey = @ForeignKey(name = "fk_usage_account"))
    private Account account;

    @Column(name = "date_key", nullable = false)
    private LocalDate dateKey;

    @Column(name = "month_key", nullable = false)
    private LocalDate monthKey;

    @Column(name = "renders_today", nullable = false)
    private int rendersToday;

    @Column(name = "renders_month", nullable = false)
    private int rendersMonth;

    public UsageCounters() {
    }

    public UsageCounters(Account account, LocalDate dateKey, LocalDate monthKey) {
        this.account = account;
        this.dateKey = dateKey;
        this.monthKey = monthKey;
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public LocalDate getDateKey() {
        return dateKey;
    }

    public void setDateKey(LocalDate dateKey) {
        this.dateKey = dateKey;
    }

    public LocalDate getMonthKey() {
        return monthKey;
    }

    public void setMonthKey(LocalDate monthKey) {
        this.monthKey = monthKey;
    }

    public int getRendersToday() {
        return rendersToday;
    }

    public void setRendersToday(int rendersToday) {
        this.rendersToday = rendersToday;
    }

    public int getRendersMonth() {
        return rendersMonth;
    }

    public void setRendersMonth(int rendersMonth) {
        this.rendersMonth = rendersMonth;
    }
}

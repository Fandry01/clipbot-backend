package com.example.clipbot_backend.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

/**
 * Flyway strategy that repairs checksum mismatches (e.g., when migration SQL changed after apply)
 * before running migrations. This keeps environments aligned even if V29 was previously applied
 * with an older checksum.
 */
@Configuration
public class FlywayRepairMigrationStrategy {

    /**
     * Repairs the Flyway schema history to the current SQL files and then executes migrations.
     *
     * @return strategy that invokes {@link Flyway#repair()} prior to {@link Flyway#migrate()}.
     */
    @Bean
    public FlywayMigrationStrategy repairThenMigrateStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}

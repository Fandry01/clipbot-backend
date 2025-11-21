package com.example.clipbot_backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables application-specific configuration properties.
 */
@Configuration
@EnableConfigurationProperties({BrandProperties.class, PlansProperties.class})
public class AppPropertiesConfig {
}

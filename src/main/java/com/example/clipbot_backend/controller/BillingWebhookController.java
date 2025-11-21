package com.example.clipbot_backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Stripe webhook stub that logs incoming events for future billing integration.
 */
@RestController
@RequestMapping("/v1/billing/stripe")
public class BillingWebhookController {
    private static final Logger LOGGER = LoggerFactory.getLogger(BillingWebhookController.class);

    /**
     * Logs the received Stripe event. Actual plan switching will be implemented later.
     *
     * @param payload raw webhook payload
     * @return 200 OK response
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> payload) {
        String type = payload.getOrDefault("type", "unknown").toString();
        LOGGER.info("BillingWebhookController received event type={}", type);
        // TODO: map customer.subscription.created|updated|trial_will_end|deleted to plan changes
        // TODO: update account.planTier and trialEndsAt based on subscription status
        return ResponseEntity.ok().build();
    }
}

package com.example.clipbot_backend.util;

import com.example.clipbot_backend.dto.TemplateResponse;
import com.example.clipbot_backend.model.Template;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class TemplateMapper {
    private final ObjectMapper om = new ObjectMapper();

    public TemplateResponse toResponse(Template t) {
        Map<String, Object> cfg;
        try {
            cfg = (t.getJsonConfig() == null || t.getJsonConfig().isBlank())
                    ? Map.of()
                    : om.readValue(t.getJsonConfig(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            cfg = Map.of(); // fallback
        }

        return new TemplateResponse(
                t.getId(),
                t.getOwner() != null ? t.getOwner().getId() : null, // <-- haal UUID uit Account
                t.getName(),
                cfg,
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }

    public String toJson(Map<String,Object> config) {
        try {
            return om.writeValueAsString(config == null ? Map.of() : config);
        } catch (Exception e) {
            throw new RuntimeException("CONFIG_SERIALIZE_FAILED", e);
        }
    }
}

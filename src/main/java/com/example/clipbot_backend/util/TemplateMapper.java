package com.example.clipbot_backend.util;

import com.example.clipbot_backend.dto.TemplateResponse;
import com.example.clipbot_backend.model.Template;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class TemplateMapper {
    private final ObjectMapper om = new ObjectMapper();

    public TemplateResponse toResponse(Template t){
        Map<String,Object> cfg = Map.of();
        try { cfg = om.readValue(t.getJsonConfig(), Map.class); } catch (Exception ignored) {}
        return new TemplateResponse(
                t.getId(), t.getOwnerId(), t.getName(), cfg, t.getCreatedAt(), t.getUpdatedAt()
        );
    }

    public String toJson(Map<String,Object> config){
        try { return om.writeValueAsString(config == null ? Map.of() : config); }
        catch (Exception e){ throw new RuntimeException("CONFIG_SERIALIZE_FAILED", e); }
    }
}

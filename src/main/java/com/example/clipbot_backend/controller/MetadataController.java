package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.MetadataResponse;
import com.example.clipbot_backend.service.metadata.MetadataResult;
import com.example.clipbot_backend.service.metadata.MetadataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/metadata")
    public MetadataResponse getMetadata(@RequestParam("url") String url) {
        MetadataResult result = metadataService.resolve(url);
        Integer durationSec = null;
        if (result.durationSec() != null) {
            durationSec = result.durationSec() > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : result.durationSec().intValue();
        }
        return new MetadataResponse(
                result.platform().id(),
                result.url(),
                result.title(),
                result.author(),
                durationSec,
                result.thumbnail()
        );
    }
}

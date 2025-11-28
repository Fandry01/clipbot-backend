package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.render.ExportClipRequest;
import com.example.clipbot_backend.dto.render.SubtitleStyle;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.service.AccountService;
import com.example.clipbot_backend.service.RenderService;
import com.example.clipbot_backend.service.ClipService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/v1/render")
public class RenderController {
    private final RenderService renderService;
    private final ClipService clipService;
    private final AccountService accountService;

    public RenderController(RenderService renderService, ClipService clipService, AccountService accountService) {
        this.renderService = renderService;
        this.clipService = clipService;
        this.accountService = accountService;
    }

    @PostMapping("/export")
    public ResponseEntity<String> exportWithStyle(
            @Valid @RequestBody ExportClipRequest req,
            @RequestParam(required = false) String ownerExternalSubject
    ) {
        Clip clip = clipService.get(req.clipId());
        ensureOwnedBy(clip, ownerExternalSubject);

        UUID jobId = renderService.enqueueExportWithStyle(
                req.clipId(),
                req.subtitleStyle() == null ? SubtitleStyle.defaults() : req.subtitleStyle(),
                req.profile()
        );
        return ResponseEntity.ok(jobId.toString());
    }

    private void ensureOwnedBy(Clip clip, String sub) {
        if (sub == null || sub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OWNER_REQUIRED");
        }
        if (isAdmin(sub)) return;
        var owner = clip.getMedia().getOwner().getExternalSubject();
        if (!Objects.equals(owner, sub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CLIP_NOT_OWNED");
        }
    }

    private boolean isAdmin(String sub) {
        try {
            return accountService.isAdmin(sub);
        } catch (Exception e) {
            return false;
        }
    }
}

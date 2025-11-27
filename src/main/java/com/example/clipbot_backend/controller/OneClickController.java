package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.orchestrate.OneClickRequest;
import com.example.clipbot_backend.dto.orchestrate.OneClickResponse;
import com.example.clipbot_backend.service.OneClickOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoint for the one-click orchestration flow.
 */
@RestController
@RequestMapping("/v1/orchestrate")
public class OneClickController {
    private final OneClickOrchestrator orchestrator;

    public OneClickController(OneClickOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Operation(summary = "Run full ingest + detect + recommend flow in one call")
    @ApiResponse(responseCode = "200", description = "Orchestration completed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request payload")
    @ApiResponse(responseCode = "409", description = "Idempotency conflict or in-progress orchestration")
    @PostMapping("/one-click")
    public ResponseEntity<OneClickResponse> oneClick(@Valid @RequestBody OneClickRequest request) {
        OneClickResponse response = orchestrator.orchestrate(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}

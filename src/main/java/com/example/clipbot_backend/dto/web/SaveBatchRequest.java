package com.example.clipbot_backend.dto.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SaveBatchRequest(@NotNull UUID mediaId, @Valid List<SegmentBatchItem> items) {
}

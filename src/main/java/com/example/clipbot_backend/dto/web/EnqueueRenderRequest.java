package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnqueueRenderRequest(@NotNull UUID clipId)
{}

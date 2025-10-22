package com.example.clipbot_backend.dto;

import java.util.List;

public record FwVerboseResponse(String text, List<Seg> segments) {
    public record Seg(Double start, Double end, String text, List<Word> words) {}
    public record Word(String word, Double start, Double end) {}
}

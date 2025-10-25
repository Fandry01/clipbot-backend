package com.example.clipbot_backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FwVerboseResponse(
        String task,               // "transcribe" (optioneel)
        String language,           // bv "en" (kan null zijn)
        Double duration,           // totale duur in seconden (kan null zijn)
        String text,               // volledige transcript
        java.util.List<RootWord> words,    // root-level woorden (soms aanwezig)
        java.util.List<Seg> segments       // segmenten met evt. words per segment
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RootWord(
            String word,
            Double start,
            Double end,
            Double probability    // extra veld; kan null zijn
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Seg(
            Double start,
            Double end,
            String text,
            java.util.List<SegWord> words
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SegWord(
            String word,
            Double start,
            Double end,
            Double probability     // extra veld; kan null zijn
    ) {}
}

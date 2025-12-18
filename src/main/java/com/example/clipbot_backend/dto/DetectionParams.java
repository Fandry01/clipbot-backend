package com.example.clipbot_backend.dto;

public record DetectionParams(
        //clip duur
                              long minDurationMs,
                              long maxDurationMs,
                              int maxCandidates,
                              // stilte (ffmpeg)
                              double silenceNoiseDb,
                              double silenceMinDurSec,
                              long snapThresholdMs,
                              // Tekst-heuristiek
                              double targetLenSec,
                              double lenSigmaSec,
                              // scene change
                              double sceneThreshold,
                              long   snapSceneMs,
                              double sceneAlignBonus,
                              boolean speakerTurnsEnabled

) {
    // Compacte ctor voor defaults + guard rails
    public DetectionParams {
        // Defaults als inkomende waarde 0/negatief is (of niet gezet bij JSON)
        minDurationMs   = (minDurationMs   > 0) ? minDurationMs   : 10_000;   // 10s
        maxDurationMs   = (maxDurationMs   > 0) ? maxDurationMs   : 60_000;   // 60s
        maxCandidates   = (maxCandidates   > 0) ? maxCandidates   : 8;

        silenceNoiseDb  = (silenceNoiseDb != 0.0) ? silenceNoiseDb : -35.0;
        silenceMinDurSec= (silenceMinDurSec> 0) ? silenceMinDurSec : 0.5;
        snapThresholdMs = (snapThresholdMs > 0) ? snapThresholdMs  : 400;

        targetLenSec    = (targetLenSec    > 0) ? targetLenSec     : 30.0;
        lenSigmaSec     = (lenSigmaSec     > 0) ? lenSigmaSec      : 10.0;

        sceneThreshold  = (sceneThreshold  > 0) ? sceneThreshold   : 0.4;
        snapSceneMs     = (snapSceneMs     > 0) ? snapSceneMs      : 400;
        sceneAlignBonus = (sceneAlignBonus > 0) ? sceneAlignBonus  : 0.12;

        speakerTurnsEnabled = speakerTurnsEnabled;

        // Basisvalidatie
        if (minDurationMs >= maxDurationMs)
            throw new IllegalArgumentException("minDurationMs must be < maxDurationMs");
    }
    // Handige named factory voor defaults (optioneel)
    public static DetectionParams defaults() { return new DetectionParams(0,0,0,0,0,0,0,0,0,0,0,false); }

    // "withers" voor fluency (optioneel)
    public DetectionParams withMaxCandidates(int n) { return new DetectionParams(
            minDurationMs, maxDurationMs, n,
            silenceNoiseDb, silenceMinDurSec, snapThresholdMs,
            targetLenSec, lenSigmaSec,
            sceneThreshold, snapSceneMs, sceneAlignBonus,
            speakerTurnsEnabled
    );}

    public DetectionParams withSpeakerTurnsEnabled(boolean enabled) {
        return new DetectionParams(
                minDurationMs, maxDurationMs, maxCandidates,
                silenceNoiseDb, silenceMinDurSec, snapThresholdMs,
                targetLenSec, lenSigmaSec,
                sceneThreshold, snapSceneMs, sceneAlignBonus,
                enabled
        );
    }
}

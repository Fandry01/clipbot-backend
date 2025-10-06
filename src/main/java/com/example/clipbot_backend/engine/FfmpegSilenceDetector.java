package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.SilenceEvent;
import com.example.clipbot_backend.service.Interfaces.SilenceDetector;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class FfmpegSilenceDetector  implements SilenceDetector {
    private static final Pattern START = Pattern.compile("silence_start: ([0-9.]+)");
    private static final Pattern END   = Pattern.compile("silence_end: ([0-9.]+)");

    @Override
    public List<SilenceEvent> detect(Path mediaPath, double noiseDb, double minSilenceSec) {
        List<Double> starts = new ArrayList<>(), ends = new ArrayList<>();
        List<String> cmd = List.of(
                "ffmpeg","-hide_banner","-nostats",
                "-i", mediaPath.toString(),
                "-af","silencedetect=noise="+noiseDb+"dB:d="+minSilenceSec,
                "-f","null","-"
        );
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    var m1 = START.matcher(line); if (m1.find()) starts.add(Double.parseDouble(m1.group(1)));
                    var m2 = END.matcher(line);   if (m2.find()) ends.add(Double.parseDouble(m2.group(1)));
                }
            }
            p.waitFor();
        } catch (Exception e) {
            throw new RuntimeException("FFmpeg silencedetect failed", e);
        }

        int n = Math.min(starts.size(), ends.size());
        List<SilenceEvent> out = new ArrayList<>(n);
        for (int i=0;i<n;i++) {
            long s = Math.round(starts.get(i)*1000);
            long e = Math.round(ends.get(i)*1000);
            if (e>s) out.add(new SilenceEvent(s,e));
        }
        return out;
    }
}

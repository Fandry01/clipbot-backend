package com.example.clipbot_backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public class SmartThumbnailer {
    private static final Logger LOG = LoggerFactory.getLogger(SmartThumbnailer.class);

    private final String ffmpegBin;
    private final Path workDir;

    public SmartThumbnailer(String ffmpegBin, Path workDir) {
        this.ffmpegBin = ffmpegBin;
        this.workDir = workDir;
    }

    /** Retourneert pad van uiteindelijke JPEG */
    public Path generate(Path sourceVideo, double startSec, double endSec, int targetW, int targetH) throws Exception {
        double dur = Math.max(0.5, endSec - startSec);
        // 1) Kandidaten extraheren (max ~12) tussen start+0.3s en end-0.3s
        double safeStart = startSec + 0.3;
        double safeDur   = Math.max(0.4, dur - 0.6);

        Path tmpDir = java.nio.file.Files.createTempDirectory(workDir, "thumbs-");
        // 2 fps cap, maar clamp niet > 12 frames
        int fps = (int)Math.ceil(Math.min(12.0, Math.max(2.0, safeDur * 2.0 / 12.0) * 2.0));
        Path pattern = tmpDir.resolve("cand-%03d.jpg");

        List<String> cmd = List.of(
                ffmpegBin, "-y",
                "-ss", String.format(java.util.Locale.ROOT, "%.3f", safeStart),
                "-t",  String.format(java.util.Locale.ROOT, "%.3f", safeDur),
                "-i",  sourceVideo.toAbsolutePath().toString(),
                "-vf", "fps=" + fps + ",scale=" + targetW + ":" + targetH + ":force_original_aspect_ratio=decrease,pad=" + targetW + ":" + targetH + ":(ow-iw)/2:(oh-ih)/2",
                "-q:v", "3",
                pattern.toAbsolutePath().toString()
        );
        new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor();

        // 2) Beste kiezen via sharpness+exposure score
        java.util.List<Path> candidates = java.nio.file.Files.list(tmpDir)
                .filter(p -> p.getFileName().toString().startsWith("cand-") && p.getFileName().toString().endsWith(".jpg"))
                .sorted()
                .toList();
        if (candidates.isEmpty()) throw new IllegalStateException("no candidates extracted");

        Path best = null; double bestScore = Double.NEGATIVE_INFINITY;
        for (Path c : candidates) {
            Score s = scoreImage(c, 0.80); // score bovenste 80% (minder last van subs onderin)
            double score = s.sharpVar * 1.0 + s.contrast * 0.15 - s.tooDarkPenalty - s.tooBrightPenalty;
            if (score > bestScore) { bestScore = score; best = c; }
        }

        // 3) Final touch (kleine punch + unsharp)
        Path out = workDir.resolve("thumb-" + java.util.UUID.randomUUID() + ".jpg");
        List<String> post = List.of(
                ffmpegBin, "-y",
                "-i", best.toAbsolutePath().toString(),
                "-vf", "eq=contrast=1.05:saturation=1.12,unsharp=5:5:0.7:5:5:0.0",
                "-q:v", "2",
                out.toAbsolutePath().toString()
        );
        new ProcessBuilder(post).redirectErrorStream(true).start().waitFor();

        // cleanup
        try { java.nio.file.Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { java.nio.file.Files.deleteIfExists(p);} catch(Exception ignore){} }); } catch(Exception ignore){}

        return out;
    }

    /** Heel simpele scherpte/helderheid scoring */
    private static class Score { double sharpVar; double contrast; double tooDarkPenalty; double tooBrightPenalty; }
    private Score scoreImage(Path jpg, double topFrac) throws Exception {
        var img = javax.imageio.ImageIO.read(jpg.toFile());
        int w = img.getWidth(), h = img.getHeight();
        int hCrop = (int)(h * topFrac);
        // grijs + simpele Laplacian-variantie
        double sum=0, sum2=0, n=0, mean=0, var=0, contrast=0;
        int[] lap = {-1,-1,-1,-1,8,-1,-1,-1,-1};
        for (int y=1; y<hCrop-1; y++) {
            for (int x=1; x<w-1; x++) {
                double g00 = luminance(img.getRGB(x, y));
                double acc = 0;
                int k=0;
                for (int j=-1;j<=1;j++)
                    for (int i=-1;i<=1;i++)
                        acc += lap[k++] * luminance(img.getRGB(x+i, y+j));
                sum  += acc; sum2 += acc*acc; n++;
                contrast += Math.abs(g00 - 0.5);
            }
        }
        mean = sum / Math.max(1,n);
        var  = sum2/Math.max(1,n) - mean*mean;
        contrast = contrast / Math.max(1,n);

        // simpele belichtingspenalty
        double avgLum = averageLum(img, hCrop);
        double darkPenalty   = avgLum < 0.18 ? (0.18-avgLum)*2.0 : 0.0;
        double brightPenalty = avgLum > 0.85 ? (avgLum-0.85)*2.0 : 0.0;

        Score s = new Score();
        s.sharpVar = var;
        s.contrast = contrast;
        s.tooDarkPenalty = darkPenalty;
        s.tooBrightPenalty = brightPenalty;
        return s;
    }
    private static double luminance(int argb) {
        int r=(argb>>16)&0xFF, g=(argb>>8)&0xFF, b=(argb)&0xFF;
        return (0.2126*r + 0.7152*g + 0.0722*b)/255.0;
    }
    private static double averageLum(java.awt.image.BufferedImage img, int hCrop) {
        double s=0; int w=img.getWidth();
        for (int y=0;y<hCrop;y++) for(int x=0;x<w;x++) s += luminance(img.getRGB(x,y));
        return s/(w*(double)hCrop);
    }
}

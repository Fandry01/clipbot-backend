1. Overzicht

Product: Clipbot-backend – detecteert sterke fragmenten in audio/video, rendert korte clips met ondertitels en thumbnails.

Hoofdflows

Ingest
Bronnen:

UploadLocal (bestand upload → RAW opslag).

HTTP download (directe URL).

YouTube (via yt-dlp → MP4; evt. M4A extract).

Transcribe + Detect

Faster Whisper → transcript (woorden + segmenten).

Detection-engine → candidate segments (startMs, endMs, score, meta).

Segments opslaan/raadplegen

Batch save → DB.

List/top endpoints → UI.

Render

FFmpeg: video/audiosnede + burned-in subtitles + smart thumbnail.

Assets (MP4, THUMBNAIL, SUB_SRT/VTT) aan owner/media/clip koppelen.

Belangrijkste componenten

StorageService (interface) met LocalStorageService implementatie.

UrlDownloader (yt-dlp/HTTP).

FasterWhisperTranscriptionEngine (via FasterWhisperClient).

DetectWorkflow, ClipWorkFlow, WorkerService.

FfmpegClipRenderEngine (+ SmartThumbnailer).

Repositories: MediaRepository, SegmentRepository, ClipRepository, AssetRepository.

2. Configuratie (Spring properties)
   Opslag (lokaal)
   storage.local.baseDir=./data
   storage.local.rawPrefix=raw
   storage.local.outPrefix=out

Externe tools
downloader.ytdlp.bin=yt-dlp
downloader.http.timeoutSeconds=120
downloader.http.userAgent=Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari
downloader.http.maxRedirects=5

ffmpeg.binary=ffmpeg
renderer.fontsDir=./resources/fonts   # bv. bevat Inter-SemiBold.ttf

Timeouts (FW client e.d.)

Client timeouts mogen ruim zijn voor 10–15 min video’s.

Voor echt lange files komt later chunking (niet nu).

3. Data & modellen (korte leidraad)
   AssetKind (DB constraint!)
   public enum AssetKind {
   MEDIA_RAW, MP4, WEBM, THUMBNAIL, SUB_SRT, SUB_VTT
   }


Let op: eerdere CLIP_MP4/CLIP_WEBM zijn gemigreerd; houd je aan bovenstaande values. Inserts falen anders op asset_kind_check.

Segment

startMs (>=0), endMs (>startMs), score (BigDecimal), meta (json-like map).

Repository:

Page<Segment> findByMediaOrderByStartMsAsc(Media media, Pageable pageable)

List<Segment> findTopByMediaOrderByScoreDesc(Media media, Pageable limitPage)

int deleteByMedia(Media media)

4. Storage

Interface

Path resolveRaw(String objectKey)

Path resolveOut(String objectKey)

void uploadToOut(Path local, String objectKey)

Local implementatie

RAW → ${baseDir}/${rawPrefix}/...

OUT → ${baseDir}/${outPrefix}/...

Object keys (gebruik consequent):

YouTube: ext/yt/{videoId}/source.mp4 of source.m4a

Clips: clips/clip-<uuid>.mp4

Thumbs: clips/thumbs/clip-<uuid>.jpg

Subtitles: subtitles/{mediaId}/{captionId}.srt|vtt

5. Ingest
   5.1 UploadLocal (multipart)

Schrijf bestand naar RAW via StorageService.

Maak/refresh een Media record.

Retourneer mediaId + objectKey.

5.2 HTTP download

UrlDownloader.downloadWithHttp(...)

Volgt redirects (max downloader.http.maxRedirects).

Schrijft naar *.part en atomic move naar target.

5.3 YouTube (yt-dlp)

MP4 pad: ext/yt/{id}/source.mp4

M4A extract (optioneel): met ffmpeg uit MP4 naar source.m4a

Bij detect kiest pipeline audio-only (M4A) voor transcript; render gebruikt MP4 voor video waar beschikbaar.

Regels

Geen bestanden overschrijven als target bestaat (idempotent).

Heldere errors (exit codes vermelden).

6. Transcribe & Detect
   FasterWhisperTranscriptionEngine

Input: StorageService.resolveRaw(objectKey)

Output: Result(text, words[], lang, "fw", meta)

Woordenlijst: (startMs, endMs, token) geschoond en gesorteerd.

DetectWorkflow

TX-grenzen helder: IO buiten TX, DB in korte TX’s.

Logging:

FW START media={} key={} size={}B

FW DONE media={} in={}ms segments={}

TRANSCRIPT SAVED media={} id={} words={}

DETECT words={} media={}

Endpoints

POST /v1/media/{mediaId}/detect?ownerExternalSubject=...

Resultaat: segments worden opgeslagen via SegmentService.saveBatch.

7. Segments API

POST /v1/segments/batch
Body: { mediaId, items: [{ startMs, endMs, score?, meta? }] }
Validatie: startMs >= 0, endMs > startMs.

GET /v1/segments/media/{mediaId}?page=0&size=20&ownerExternalSubject=...
Sort: startMs ASC (server-side).

GET /v1/segments/media/{mediaId}/top?limit=5&ownerExternalSubject=...
Sort: score DESC (nulls last).

DELETE /v1/segments/media/{mediaId}?ownerExternalSubject=...

Auth/ownership

Iedere endpoint valideert ownerExternalSubject vs media.owner.

8. Render (FFmpeg)
   8.1 Belangrijkste regels

Plaats -ss vóór de -i (snelle seek op de input).

Gebruik even width/height (anders encoder warnings).

Subtitles pas ná scale/pad, zodat fontsize klopt voor output-resolutie.

Audio-only → maak zwarte canvas als video-input, map audio apart:

-f lavfi -i color=black:size=WxH:rate=FPS
-ss START -i input.m4a
-map 0:v:0 -map 1:a:0?
-shortest

8.2 Subtitles styling

Font: Inter Semi Bold (in renderer.fontsDir).

Filter: subtitles='...srt':force_style='...':fontsdir='...fontsDir'

Dynamische schaal:

// voorbeeld van policy (tune via meta.subtitleScale)
// 16:9 → mul ± 0.0222; 9:16 → ± 0.0260
// Hard min/max: 14–36 px @1080p

8.3 Command-opbouw (samengevat)

Video-bron:

ffmpeg -y
-ss {startSec} -i {inputVideo}
-vf "scale=WxH:force_original_aspect_ratio=decrease,pad=WxH:(ow-iw)/2:(oh-ih)/2,subtitles='SRT':force_style='STYLE':fontsdir='DIR'"
-c:v libx264 -preset {preset} -crf {crf}
-c:a aac -b:a 128k
-t {durationSec}
-pix_fmt yuv420p -movflags +faststart
-force_key_frames expr:gte(t\,0)
{out.mp4}


Audio-only:

ffmpeg -y
-f lavfi -i color=color=black:size=WxH:rate=FPS
-ss {startSec} -i {inputAudio}
-vf "scale=...,pad=...,subtitles=..."
-map 0:v:0 -map 1:a:0?
-c:v libx264 -preset {preset} -crf {crf}
-c:a aac -b:a 128k
-shortest
-t {durationSec}
-pix_fmt yuv420p -movflags +faststart
-force_key_frames expr:gte(t\,0)
{out.mp4}

8.4 Smart thumbnails

SmartThumbnailer.generate(src, startSec, endSec, W, H)

Scoort frames (gezicht/helderheid/onscherpte e.d.), mijdt ondertitelzone.

Fallback: single frame uit midden.

Thumbnail uit gerenderde clip voor audio-only; uit bron bij video.

8.5 Persist assets (TX-veilig)

In REQUIRES_NEW:

Asset aanmaken met juiste AssetKind, size en objectKey.

Relaties zetten met getReferenceById om lazy loads te vermijden.

clip.setStatus(READY).

9. Transacties & Lazy-init

Patroon

A. markRendering(clipId) – korte TX om status te zetten.

B. loadIoData(clipId) – findByIdWithMedia met join fetch media + media.owner. Geef terug als IO DTO:

record IoData(UUID clipId, UUID mediaId, String objectKey, long startMs, long endMs) {}


C. Render – buiten TX (ffmpeg/IO).

D. persistSuccess(io, res, subs) – REQUIRES_NEW TX.

E. persistFailure(...) – REQUIRES_NEW TX.

Don’ts

Geen lazy toegang tot media.getOwner() buiten TX.

Geen lange IO binnen open TX.

10. Logging (strikt)

Formaat: key=value.
Voorbeelden

log.info("FW START media={} key={} size={}B", mediaId, objectKey, sizeB);
log.info("FW DONE media={} in={}ms segments={}", mediaId, tookMs, segCount);

log.info("CLIP START jobId={} media={} clip={} rangeMs={}..{}", jobId, mediaId, clipId, startMs, endMs);
log.info("CLIP DONE jobId={} media={} clip={} in={}ms mp4={}B thumb={}B",
jobId, mediaId, clipId, tookMs, mp4Size, thumbSize);

log.warn("thumbnail fallback used media={} reason={}", mediaId, reason);
log.error("CLIP FAILED jobId={} media={} clip={} err={}", jobId, mediaId, clipId, e.toString());


Geen: secrets, absolute privépaden, megabytes aan payloads.

11. Fouten & validatie

Segments: INVALID_SEGMENT_BOUNDS startMs=.. endMs=..

Storage: "RAW missing: {objectKey}", "OUT upload failed: key=..."

Downloader: "yt-dlp exit=.. output missing", "HTTP ... status=.. body=.."

FFmpeg: "ffmpeg failed exit=.." + stdout/stderr samenvatting (geen ruis op INFO).

DB: vang DataIntegrityViolationException en geef context (kind, objectKey).

12. REST endpoints (samenvatting)

Detect: POST /v1/media/{mediaId}/detect?ownerExternalSubject=...

Segments

POST /v1/segments/batch

GET /v1/segments/media/{mediaId}?page=&size=&ownerExternalSubject=...

GET /v1/segments/media/{mediaId}/top?limit=&ownerExternalSubject=...

DELETE /v1/segments/media/{mediaId}?ownerExternalSubject=...

Files (read):

OUT assets worden geserveerd als:
/v1/files/out/{objectKey}
bijv.
/v1/files/out/clips/clip-<uuid>.mp4
/v1/files/out/clips/thumbs/clip-<uuid>.jpg

UploadLocal: multipart upload endpoint (schrijft naar RAW via StorageService, creëert Media).
NB: volg bestaand patroon/naming uit de codebase; uniform objectKey’s.

13. Code style: namen, logs, uitleg (harde regels)
    13.1 Naamgeving

Functies = werkwoorden, velden/types = zelfstandige naamwoorden.

Gebruik domeintermen (mediaId, objectKey, clipId, ownerExternalSubject, startMs, endMs).

Geen vage afkortingen.

Goed → Fout

extractAudioToM4a(mp4Path, m4aPath) → doM4a(x,y)

buildSubtitleFilter(srtPath, width, height, meta) → mkSub()

13.2 Logging

START/DONE/WARN/ERROR + ids + duur.

Key=value, geen secrets.

13.3 Uitleg (Javadoc)

Elke public functie: wat/params/return/side-effects/throws/example.

Korte rationale in PR-body.

13.4 Magic numbers

Maak constants:

private static final int DEFAULT_FPS = 30;
private static final int FONT_MIN_PX = 14;
private static final int FONT_MAX_PX = 36;

13.5 Exceptions

Altijd context in de message: id’s, objectKey, grenzen, exit code.

14. Ontwerpkeuzes die je moet volgen

Tx-grenzen strikt gescheiden van IO.

Fetch-join voor alles wat je buiten TX nodig hebt; stop het in een DTO.

FFmpeg filters: scale/pad → subtitles (in die volgorde).

Audio-only: canvas route + expliciete -map + -shortest.

Fonts: pakket Inter in renderer.fontsDir; geen systeemfonts aannemen.

Thumbnails: SmartThumbnailer eerst, anders fallback.

15. Testchecklist (per feature/PR)

UploadLocal → RAW correct, Media aangemaakt.

HTTP & YouTube ingest werken en idempotent (bestaat=skip).

Detect: transcript + segments persist; segments/media/{id} levert data.

Render video-bron: ondertitels schaal/positie oké; MP4 speelt af.

Render audio-only: zwart canvas + subs; MP4 speelt af.

Assets: MP4/THUMBNAIL/SUB_* met juiste owner/media/clip ref; geen constraint errors.

Logs bevatten START/DONE met tijden; geen secrets.

Fouten geven bruikbare context (geen “failed” zonder details).

16. Troubleshooting (bekende valkuilen)

asset_kind_check faalt
→ verkeerde AssetKind. Gebruik exact: MEDIA_RAW, MP4, WEBM, THUMBNAIL, SUB_SRT, SUB_VTT.

LazyInitializationException
→ buiten TX aan lazy field. Oplossing: findByIdWithMedia(.. join fetch ..) en IO DTO gebruiken.

FFmpeg “input option to output file”
→ opties op juiste plaats zetten. -ss vóór -i. -force_key_frames mag aan einde.

Subtitles te groot
→ volg subtitleStyleForHeight(...). Eventueel meta.subtitleScale gebruiken.

YouTube SABR/HLS issues
→ we gebruiken HLS format 96 fallback; laat yt-dlp bepalen. Updaten naar nieuwste yt-dlp indien nodig.

17. Voorbeeld: publieke methode Javadoc
    /**
* Rendert een clip met optionele burned-in subtitles en genereert een thumbnail.
*
* @param inputFile  Bestaand bronbestand (video of audio) op schijf.
* @param startMs    Start (ms) t.o.v. bron, >=0.
* @param endMs      Einde (ms) t.o.v. bron, > startMs.
* @param options    Renderopties (profile, meta, subtitles).
* @return RenderResult met object keys en sizes (mp4 + thumbnail).
* @throws IllegalArgumentException bij ongeldige range of ontbrekende bron.
* @throws IOException / InterruptedException bij ffmpeg/IO issues.
*
* Side-effects:
* - Start extern proces (ffmpeg).
* - Schrijft tijdelijke bestanden in workDir.
* - Uploadt output via StorageService (OUT).
*
* Example:
*   RenderOptions opts = RenderOptions.withDefaults(Map.of("profile","youtube-1080p"), subs);
*   render(src, 15_540, 44_997, opts);
    */

18. Wat je niet doet

Geen lange running TX’s over IO/processen.

Geen cryptische namen of “mystery booleans”.

Geen stille swallow van exceptions.

Geen afhankelijkheid van systeemfonts; altijd renderer.fontsDir.

Geen endpoints zonder ownership-check.
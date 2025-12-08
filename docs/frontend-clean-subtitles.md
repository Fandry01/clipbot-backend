# Frontend prompt: play clean MP4 + overlay VTT (no double subtitles)

Use these steps to avoid double subtitles and to surface the clean render in the UI:

1. When requesting a clip asset, pass `kind=CLIP_MP4_CLEAN` to `/v1/assets/latest/clip/{clipId}`. Fallback to the regular MP4 only if the clean asset is missing.
2. Build the player source from that clean URL (no burned-in captions), and keep the VTT URL as the external text track.
3. Hide the VTT overlay if you intentionally choose the burned-in MP4, because that already contains subtitles.
4. In any clip detail DTO, prefer `subtitlesMode === "clean"` to decide whether to show the VTT track; `"burned"` means the video already has subs baked in.
5. If you cache clip data, invalidate after an export so the frontend can re-fetch and pick up the clean asset.

Quick prompt to hand to the frontend:

> Use `/v1/assets/latest/clip/{clipId}?kind=CLIP_MP4_CLEAN` for playback. Only overlay the VTT track on that clean URL. If the API marks `subtitlesMode` as `clean`, show the VTT; if it is `burned`, do not overlay because the video already has captions.

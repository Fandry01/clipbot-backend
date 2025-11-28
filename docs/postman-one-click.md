# One-click orchestrator: Postman test flow

Deze gids laat zien hoe je de idempotente one-click orchestrator met Postman kunt testen, inclusief de request-fingerprint check die een 409 geeft wanneer dezelfde idempotency key met een andere payload wordt hergebruikt.

## Basisinstellingen
- **Endpoint**: `POST http://localhost:8080/v1/orchestrate/one-click`
- **Headers**: `Content-Type: application/json`
- **Verplichte velden**: `ownerExternalSubject`, exact één van `url` of `mediaId`, en `idempotencyKey`.

## Scenario 1: Eerste succesvolle call
1. Maak een nieuwe **POST** request met body:
   ```json
   {
     "ownerExternalSubject": "demo-user-1",
     "url": "https://www.youtube.com/watch?v=dNgFOGzuOqE",
     "title": "My YT import",
     "opts": {
       "lang": "auto",
       "provider": "fasterwhisper",
       "sceneThreshold": 0.3,
       "topN": 6,
       "enqueueRender": true
     },
     "idempotencyKey": "11111111-1111-4111-8111-111111111111"
   }
   ```
2. Verwacht een **200** met o.a. `projectId`, `mediaId`, `detectJob`, `recommendations`, `thumbnailSource`, en eventueel `renderJobs`.

## Scenario 2: Replay met dezelfde payload
1. Stuur exact dezelfde request opnieuw met **dezelfde** `idempotencyKey` (`1111...`).
2. Verwacht wederom een **200** met **identieke** response-body; er wordt geen nieuwe detect- of render-job gestart (replay uit de idempotency store).

## Scenario 3: Bescherming tegen payload-wijziging
1. Pas de body aan (bijvoorbeeld verander `url` of `opts.topN`) maar laat `idempotencyKey` ongewijzigd (`1111...`).
2. Verwacht een **409** response. De orchestrator vergelijkt de opgeslagen request-fingerprint met de nieuwe payload en wijst de key af als de inhoud verschilt.

## Scenario 4: Nieuwe workload met nieuwe key
1. Gebruik dezelfde gewijzigde payload als in scenario 3, maar geef een **nieuwe** `idempotencyKey` (bijv. `22222222-2222-4222-8222-222222222222`).
2. Verwacht een **200** en een nieuwe orchestratie (nieuwe `mediaId`/`projectId` of reuse waar van toepassing).

## Optioneel: voortgang controleren
- Gebruik `GET http://localhost:8080/v1/jobs/{detectJob.jobId}` om de detect-jobstatus te volgen.
- Herhaal totdat `status` op **SUCCEEDED** staat; daarna zullen aanbevelingen/renders (indien geënqueue'd) worden verwerkt.

## Tips
- Houd `ownerExternalSubject` consistent per testrun; idempotency wordt op owner + key afgedwongen.
- Voor uploads gebruik je `mediaId` in plaats van `url`; de fingerprint-check werkt hetzelfde: gewijzigde payload met dezelfde key geeft 409.
- Genereer de `idempotencyKey` in de client (bijv. `crypto.randomUUID()` in de frontend) en hergebruik die alleen voor retries met dezelfde payload; een nieuwe bron of upload hoort een nieuwe key te krijgen.

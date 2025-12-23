package com.example.clipbot_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.clipbot_backend.config.WorkerExecutorProperties;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.Job;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.ProjectMediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.service.Interfaces.SubtitleService;
import com.example.clipbot_backend.service.thumbnail.ThumbnailService;
import com.example.clipbot_backend.util.JobType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkerServiceConcurrencyTest {

    @Mock private JobService jobService;
    @Mock private TranscriptService transcriptService;
    @Mock private MediaRepository mediaRepository;
    @Mock private TranscriptRepository transcriptRepository;
    @Mock private SegmentRepository segmentRepository;
    @Mock private ClipRepository clipRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ProjectMediaRepository projectMediaRepository;
    @Mock private UrlDownloader urlDownloader;
    @Mock private FasterWhisperClient fastWhisperClient;
    @Mock private AudioWindowService audioWindowService;
    @Mock private DetectWorkflow detectWorkflow;
    @Mock private ClipWorkFlow clipWorkFlow;
    @Mock private ClipService clipService;
    @Mock private ThumbnailService thumbnailService;
    @Mock private IngestCleanupService ingestCleanupService;
    @Mock private DetectionEngine detectionEngine;
    @Mock private ClipRenderEngine clipRenderEngine;
    @Mock private StorageService storageService;
    @Mock private SubtitleService subtitleService;
    @Mock private RenderService renderService;
    @Mock private TranscriptionEngine gptEngine;
    @Mock private TranscriptionEngine fasterEngine;

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void clipJobsRespectConcurrencyLimit() throws Exception {
        WorkerExecutorProperties props = new WorkerExecutorProperties();
        props.getClip().setMaxConcurrency(2);
        props.setExecutorThreads(3);
        props.setPollBatchSize(3);

        executor = Executors.newFixedThreadPool(props.getExecutorThreads());
        WorkerService workerService = new WorkerService(
                jobService,
                transcriptService,
                mediaRepository,
                transcriptRepository,
                segmentRepository,
                clipRepository,
                assetRepository,
                projectMediaRepository,
                urlDownloader,
                fastWhisperClient,
                audioWindowService,
                detectWorkflow,
                clipWorkFlow,
                clipService,
                thumbnailService,
                ingestCleanupService,
                detectionEngine,
                clipRenderEngine,
                storageService,
                subtitleService,
                renderService,
                gptEngine,
                fasterEngine,
                executor,
                props);

        Job job1 = clipJob();
        Job job2 = clipJob();
        Job job3 = clipJob();

        when(jobService.claimQueuedBatch(anyInt())).thenReturn(List.of(job1, job2, job3));

        CountDownLatch firstTwo = new CountDownLatch(2);
        CountDownLatch allowFinish = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(3);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();

        org.mockito.Mockito.doAnswer(inv -> {
            int current = concurrent.incrementAndGet();
            maxConcurrent.updateAndGet(v -> Math.max(v, current));
            firstTwo.countDown();
            allowFinish.await(2, TimeUnit.SECONDS);
            concurrent.decrementAndGet();
            allDone.countDown();
            return null;
        }).when(clipWorkFlow).run(any());

        workerService.poll();

        assertTrue(firstTwo.await(2, TimeUnit.SECONDS));
        allowFinish.countDown();
        assertTrue(allDone.await(2, TimeUnit.SECONDS));
        assertEquals(2, maxConcurrent.get());
        verify(clipWorkFlow, times(3)).run(any());
    }

    private Job clipJob() {
        Job job = new Job(JobType.CLIP);
        job.setId(UUID.randomUUID());
        job.setPayload(Map.of("clipId", UUID.randomUUID().toString()));
        return job;
    }
}

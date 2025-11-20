package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.RecommendationResult;
import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.selector.HeuristicGoodClipSelector;
import com.example.clipbot_backend.service.Interfaces.SubtitleService;
import com.example.clipbot_backend.service.impl.RecommendationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;


import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


class PseudoTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) throws TransactionException {
        // niets
    }

    @Override
    public void rollback(TransactionStatus status) throws TransactionException {
        // niets
    }
}
@ExtendWith(MockitoExtension.class)
class RecommendationServiceImplTest {

    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private ClipRepository clipRepository;
    @Mock
    private SegmentRepository segmentRepository;
    @Mock
    private TranscriptRepository transcriptRepository;
    @Mock
    private SubtitleService subtitleService;
    @Mock
    private JobService jobService;

    private RecommendationServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TransactionTemplate template = new TransactionTemplate(new PseudoTransactionManager());
        service = new RecommendationServiceImpl(mediaRepository, clipRepository, segmentRepository, transcriptRepository,
                subtitleService, jobService, new HeuristicGoodClipSelector(), objectMapper, template);
    }

    @Test
    void computeRecommendationsIsIdempotentAndAvoidsDoubleEnqueue() throws Exception {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        Account owner = new Account();
        owner.setExternalSubject("user");
        media.setOwner(owner);

        Segment segment = new Segment(media, 0L, 25_000L);
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(mediaRepository.getReferenceById(mediaId)).thenReturn(media);
        when(segmentRepository.findByMedia(eq(media), eq(Pageable.unpaged()))).thenReturn(new PageImpl<>(List.of(segment)));

        Transcript transcript = new Transcript();
        transcript.setMedia(media);
        transcript.setWords(objectMapper.readTree("{" +
                "\"items\":[{" +
                "\"text\":\"Hello\",\"startMs\":0,\"endMs\":1000,\"confidence\":0.9},{" +
                "\"text\":\"world!\",\"startMs\":1000,\"endMs\":2500,\"confidence\":0.8}]}"));
        when(transcriptRepository.findTopByMediaIdOrderByCreatedAtDesc(mediaId)).thenReturn(Optional.of(transcript));

        AtomicReference<Clip> storedClip = new AtomicReference<>();
        when(clipRepository.findByMediaIdAndStartMsAndEndMsAndProfileHash(eq(mediaId), anyLong(), anyLong(), anyString()))
                .then(invocation -> storedClip.get() == null ? Optional.empty() : Optional.of(storedClip.get()));
        when(clipRepository.save(any(Clip.class))).thenAnswer(invocation -> {
            Clip clip = invocation.getArgument(0);
            if (clip.getId() == null) {
                ReflectionTestUtils.setField(clip, "id", UUID.randomUUID());
            }
            storedClip.set(clip);
            return clip;
        });
        when(subtitleService.buildSubtitles(any(), anyLong(), anyLong()))
                .thenReturn(new SubtitleFiles("key.srt", 10L, "key.vtt", 11L));
        when(jobService.enqueueUnique(eq(mediaId), eq(com.example.clipbot_backend.util.JobType.CLIP), anyString(), anyMap()))
                .thenReturn(UUID.randomUUID());

        RecommendationResult first = service.computeRecommendations(mediaId, 1, Map.of("profile", "youtube-720p"), true);
        RecommendationResult second = service.computeRecommendations(mediaId, 1, Map.of("profile", "youtube-720p"), true);

        assertThat(first.clips()).hasSize(1);
        assertThat(second.clips()).hasSize(1);
        assertThat(second.clips().getFirst().id()).isEqualTo(first.clips().getFirst().id());

        verify(jobService, times(1)).enqueueUnique(eq(mediaId), eq(com.example.clipbot_backend.util.JobType.CLIP), anyString(), anyMap());
        verify(subtitleService, times(1)).buildSubtitles(any(), anyLong(), anyLong());
        verifyNoMoreInteractions(jobService);
    }
}

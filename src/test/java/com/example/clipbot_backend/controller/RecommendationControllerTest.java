package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.ClipSummary;
import com.example.clipbot_backend.dto.RecommendationResult;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.service.RecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RecommendationService recommendationService;
    @MockitoBean
    private MediaRepository mediaRepository;

    @Test
    void computeEndpointTriggersService() throws Exception {
        UUID mediaId = UUID.randomUUID();
        Media media = buildMedia("user-1");
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        RecommendationResult result = new RecommendationResult(mediaId, 1,
                List.of(new ClipSummary(UUID.randomUUID(), 0, 20_000, BigDecimal.valueOf(0.9), "QUEUED", "abc")));
        when(recommendationService.computeRecommendations(eq(mediaId), eq(3), any(), eq(true))).thenReturn(result);

        String body = objectMapper.writeValueAsString(Map.of(
                "topN", 3,
                "profile", Map.of("profile", "youtube-720p"),
                "enqueueRender", true
        ));

        mockMvc.perform(post("/v1/media/{mediaId}/recommendations/compute", mediaId)
                        .param("ownerExternalSubject", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void listEndpointReturnsPagedClips() throws Exception {
        UUID mediaId = UUID.randomUUID();
        Media media = buildMedia("owner-subj");
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        ClipSummary summary = new ClipSummary(UUID.randomUUID(), 1000, 8000, BigDecimal.valueOf(0.5), "READY", "hash");
        when(recommendationService.listRecommendations(eq(mediaId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/v1/media/{mediaId}/recommendations", mediaId)
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "score,desc")
                        .param("ownerExternalSubject", "owner-subj"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("READY"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(recommendationService).listRecommendations(eq(mediaId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("score")).isNotNull();
    }

    @Test
    void explainEndpointReturnsBreakdown() throws Exception {
        UUID mediaId = UUID.randomUUID();
        Media media = buildMedia("owner");
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(recommendationService.explain(mediaId, 1000L, 5000L)).thenReturn(Map.of("1000-5000", "details"));

        mockMvc.perform(get("/v1/media/{mediaId}/recommendations/explain", mediaId)
                        .param("startMs", "1000")
                        .param("endMs", "5000")
                        .param("ownerExternalSubject", "owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['1000-5000']").value("details"));
    }

    private static Media buildMedia(String ownerSubject) {
        Media media = new Media();
        Account owner = new Account();
        owner.setExternalSubject(ownerSubject);
        media.setOwner(owner);
        return media;
    }
}

package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.service.AccountService;
import com.example.clipbot_backend.service.MediaService;
import com.example.clipbot_backend.service.metadata.MetadataService;
import com.example.clipbot_backend.util.MediaPlatform;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MediaController.class)
@AutoConfigureMockMvc(addFilters = false)
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private MetadataService metadataService;

    @MockitoBean
    private AccountService accountService;

    @Test
    void createFromUrlAcceptsOwnerExternalSubject() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        String externalSubject = "demo-user-1";
        String url = "https://example.com/audio.mp4";

        Account account = mock(Account.class);
        when(account.getId()).thenReturn(ownerId);
        when(accountService.getByExternalSubjectOrThrow(externalSubject)).thenReturn(account);
        when(metadataService.normalizeUrl(url)).thenReturn(url);
        when(metadataService.detectPlatform(url)).thenReturn(MediaPlatform.OTHER);
        when(mediaService.createMediaFromUrl(eq(ownerId), eq(url), eq(MediaPlatform.OTHER), eq("url"), any(), any(), any()))
                .thenReturn(mediaId);

        Media media = new Media();
        media.setOwner(account);
        media.setObjectKey("ext/url/abc/source.m4a");
        when(mediaService.get(mediaId)).thenReturn(media);

        String body = objectMapper.writeValueAsString(Map.of(
                "ownerExternalSubject", externalSubject,
                "url", url
        ));

        var mvcResult = mockMvc.perform(post("/v1/media/from-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaId").value(mediaId.toString()))
                .andExpect(jsonPath("$.objectKey").value("ext/url/abc/source.m4a"))
                .andReturn();

        verify(mediaService).createMediaFromUrl(eq(ownerId), eq(url), eq(MediaPlatform.OTHER), eq("url"), any(), any(), any());
        assertThat(mvcResult.getResponse().getContentAsString()).contains("DOWNLOADING");
    }

    @Test
    void createFromUrlTreatsNonUuidOwnerIdAsExternalSubject() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        String externalSubject = "demo-user-1";
        String url = "https://example.com/audio.mp4";

        Account account = mock(Account.class);
        when(account.getId()).thenReturn(ownerId);
        when(accountService.getByExternalSubjectOrThrow(externalSubject)).thenReturn(account);
        when(metadataService.normalizeUrl(url)).thenReturn(url);
        when(metadataService.detectPlatform(url)).thenReturn(MediaPlatform.OTHER);
        when(mediaService.createMediaFromUrl(eq(ownerId), eq(url), eq(MediaPlatform.OTHER), eq("url"), any(), any(), any()))
                .thenReturn(mediaId);

        Media media = new Media();
        media.setOwner(account);
        media.setObjectKey("ext/url/abc/source.m4a");
        when(mediaService.get(mediaId)).thenReturn(media);

        String body = objectMapper.writeValueAsString(Map.of(
                "ownerId", externalSubject,
                "url", url
        ));

        mockMvc.perform(post("/v1/media/from-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaId").value(mediaId.toString()))
                .andExpect(jsonPath("$.objectKey").value("ext/url/abc/source.m4a"));

        verify(accountService).getByExternalSubjectOrThrow(externalSubject);
        verify(mediaService).createMediaFromUrl(eq(ownerId), eq(url), eq(MediaPlatform.OTHER), eq("url"), any(), any(), any());
    }

    @Test
    void createFromUrlRejectsConflictingOwnerInputs() throws Exception {
        String ownerIdRaw = "demo-user-1";
        String externalSubject = "other-user";
        String url = "https://example.com/audio.mp4";

        String body = objectMapper.writeValueAsString(Map.of(
                "ownerId", ownerIdRaw,
                "ownerExternalSubject", externalSubject,
                "url", url
        ));

        mockMvc.perform(post("/v1/media/from-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}

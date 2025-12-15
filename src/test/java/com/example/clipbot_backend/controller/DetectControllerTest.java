package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.service.AccountService;
import com.example.clipbot_backend.service.DetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DetectController.class)
@AutoConfigureMockMvc(addFilters = false)
class DetectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private DetectionService detectionService;

    @MockitoBean
    private MediaRepository mediaRepository;

    @Test
    void enqueueUsesOwnerFetchJoinToAuthorize() throws Exception {
        UUID mediaId = UUID.randomUUID();
        String ownerSubject = "demo-user-1";

        Account owner = new Account();
        owner.setExternalSubject(ownerSubject);

        Media media = new Media();
        media.setId(mediaId);
        media.setOwner(owner);

        when(accountService.isAdmin(ownerSubject)).thenReturn(false);
        when(mediaRepository.findByIdWithOwner(mediaId)).thenReturn(Optional.of(media));
        when(detectionService.enqueueDetect(eq(mediaId), any(), any(), any())).thenReturn(UUID.randomUUID());

        mockMvc.perform(post("/v1/media/" + mediaId + "/detect")
                        .param("ownerExternalSubject", ownerSubject)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mediaId").value(mediaId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(mediaRepository).findByIdWithOwner(mediaId);
    }

    @Test
    void enqueueRejectsWhenOwnerMismatch() throws Exception {
        UUID mediaId = UUID.randomUUID();
        String ownerSubject = "demo-user-1";

        Account owner = new Account();
        owner.setExternalSubject("other-user");

        Media media = new Media();
        media.setId(mediaId);
        media.setOwner(owner);

        when(accountService.isAdmin(ownerSubject)).thenReturn(false);
        when(mediaRepository.findByIdWithOwner(mediaId)).thenReturn(Optional.of(media));

        mockMvc.perform(post("/v1/media/" + mediaId + "/detect")
                        .param("ownerExternalSubject", ownerSubject)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(mediaRepository).findByIdWithOwner(mediaId);
    }
}

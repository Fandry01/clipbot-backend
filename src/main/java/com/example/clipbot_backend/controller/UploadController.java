package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.UploadCompleteResponse;
import com.example.clipbot_backend.dto.web.UploadInitRequest;
import com.example.clipbot_backend.dto.web.UploadInitResponse;
import com.example.clipbot_backend.service.UploadService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/uploads")
public class UploadController {
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("init")
    public UploadInitResponse init(@RequestBody UploadInitRequest request) {

        return uploadService.init(request);
    }

    @PostMapping(
            value = "/local",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public UploadCompleteResponse localUpload(
            @RequestParam("owner") String ownerExternalSubject,
            @RequestParam(value = "objectKey", required = false) String objectKey,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "source", required = false, defaultValue = "upload") String source,
            @RequestParam(value = "podcastOrInterview", required = false, defaultValue = "false") boolean podcastOrInterview) throws Exception {
        return uploadService.uploadLocal(ownerExternalSubject, objectKey, file, source, podcastOrInterview);
    }


}

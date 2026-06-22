package com.example.issuesystem.patchhistory.controller;

import com.example.issuesystem.common.ApiResponse;
import com.example.issuesystem.patchhistory.dto.PatchHistoryUploadFileResponse;
import com.example.issuesystem.patchhistory.dto.PatchHistoryUploadResult;
import com.example.issuesystem.patchhistory.service.PatchHistoryFileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/patch-histories", "/api/issue-cases"})
public class PatchHistoryFileUploadController {

    private final PatchHistoryFileUploadService fileUploadService;

    @PostMapping("/upload")
    public ApiResponse<PatchHistoryUploadResult> uploadExcelFile(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Excel upload requested. fileName={}, size={}", file.getOriginalFilename(), file.getSize());

            PatchHistoryUploadResult result = fileUploadService.processExcelFile(file);

            log.info(
                    "Excel upload completed. savedCount={}, excludedCount={}",
                    result.getSavedCount(),
                    result.getExcludedCount()
            );

            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("Excel upload failed.", e);
            throw e;
        }
    }

    @GetMapping("/upload-files")
    public ApiResponse<List<PatchHistoryUploadFileResponse>> getUploadFiles() {
        return ApiResponse.ok(fileUploadService.getRecentUploadFiles());
    }
}

package com.example.issuesystem.issue.controller;

import com.example.issuesystem.common.ApiResponse;
import com.example.issuesystem.issue.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/issue-cases")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/upload")
    public ApiResponse<Integer> uploadExcelFile(@RequestParam("file") MultipartFile file) {
        try {
            log.info("엑셀 업로드 요청: fileName={}, size={}", file.getOriginalFilename(), file.getSize());

            int savedCount = fileUploadService.processExcelFile(file);

            log.info("엑셀 업로드 완료: savedCount={}", savedCount);

            return ApiResponse.ok(savedCount);
        } catch (Exception e) {
            log.error("엑셀 업로드 처리 실패", e);

            throw e;
        }
    }
}
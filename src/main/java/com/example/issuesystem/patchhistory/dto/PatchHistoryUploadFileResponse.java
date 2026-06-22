package com.example.issuesystem.patchhistory.dto;

import com.example.issuesystem.patchhistory.domain.PatchHistoryUploadLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PatchHistoryUploadFileResponse {

    private Long id;
    private String fileName;
    private int savedCount;
    private int excludedCount;
    private LocalDateTime createdAt;

    public static PatchHistoryUploadFileResponse from(PatchHistoryUploadLog uploadLog) {
        return PatchHistoryUploadFileResponse.builder()
                .id(uploadLog.getId())
                .fileName(uploadLog.getFileName())
                .savedCount(uploadLog.getSavedCount())
                .excludedCount(uploadLog.getExcludedCount())
                .createdAt(uploadLog.getCreatedAt())
                .build();
    }
}

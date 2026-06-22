package com.example.issuesystem.patchhistory.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PatchHistoryUploadExcludedItem {

    private String sheetName;
    private int rowNumber;
    private String title;
    private String reason;
}

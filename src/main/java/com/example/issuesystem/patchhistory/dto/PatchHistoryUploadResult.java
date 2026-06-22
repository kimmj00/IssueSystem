package com.example.issuesystem.patchhistory.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PatchHistoryUploadResult {

    private int savedCount;
    private int excludedCount;
    private List<PatchHistoryUploadExcludedItem> excludedItems;
}

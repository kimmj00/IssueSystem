package com.example.issuesystem.patchhistory.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PatchHistoryFilterOptionsResponse {
    private List<String> categories;
    private List<String> deploymentVersions;
}

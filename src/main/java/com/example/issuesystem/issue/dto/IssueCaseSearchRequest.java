package com.example.issuesystem.issue.dto;

import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.issue.domain.IssueStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IssueCaseSearchRequest {
    private String keyword;
    private InfraType infraType;
    private IssueStatus status;
    private String customerName;
}
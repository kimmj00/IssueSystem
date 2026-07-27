package com.example.issuesystem.account.dto;

import com.example.issuesystem.account.domain.Account;
import com.example.issuesystem.account.domain.AccountRole;

import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String userId,
        String name,
        AccountRole role,
        LocalDateTime createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getUserId(), account.getName(), account.getRole(), account.getCreatedAt());
    }
}

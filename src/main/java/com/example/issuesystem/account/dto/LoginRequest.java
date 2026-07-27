package com.example.issuesystem.account.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "ID를 입력해주세요.") String userId,
        @NotBlank(message = "비밀번호를 입력해주세요.") String password
) {}

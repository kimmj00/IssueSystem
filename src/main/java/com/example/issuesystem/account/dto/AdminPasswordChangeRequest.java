package com.example.issuesystem.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminPasswordChangeRequest(
        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S{8,20}$",
                message = "비밀번호는 8~20자이며 영문 대·소문자, 숫자, 특수문자를 각각 포함해야 합니다."
        )
        String password
) {
}

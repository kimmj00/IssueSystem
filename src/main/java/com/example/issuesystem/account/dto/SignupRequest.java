package com.example.issuesystem.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "ID를 입력해주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z0-9]{4,20}$", message = "ID는 영문과 숫자를 포함하여 4~20자로 입력해주세요.")
        String userId,
        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S{8,20}$", message = "비밀번호는 8~20자이며 영문 대·소문자, 숫자, 특수문자를 각각 포함해야 합니다.")
        String password,
        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하로 입력해주세요.")
        String name
) {}

package com.realtimechat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 2, max = 30, message = "닉네임은 2~30자로 입력해 주세요.")
        String displayName
) {
}
package com.realtimechat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Size(min = 3, max = 30)
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "아이디는 영문, 숫자, 밑줄, 하이픈만 사용할 수 있습니다.")
        String username,
        @NotBlank
        @Size(min = 8, max = 72, message = "비밀번호는 8~72자로 입력해 주세요.")
        String password
) {
}
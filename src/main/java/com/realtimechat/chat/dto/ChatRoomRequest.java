package com.realtimechat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRoomRequest(
        @NotBlank(message = "채팅방 이름을 입력해 주세요.")
        @Size(max = 100, message = "채팅방 이름은 100자 이하여야 합니다.")
        String name
) {
}
package com.realtimechat.auth.dto;

import java.time.LocalDateTime;

public record AuthResponse(String username, String displayName, LocalDateTime createdAt) {
}
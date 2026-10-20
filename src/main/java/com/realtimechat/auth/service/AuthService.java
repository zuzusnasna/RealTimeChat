package com.realtimechat.auth.service;

import com.realtimechat.auth.dto.AuthResponse;
import com.realtimechat.auth.dto.SignupRequest;
import com.realtimechat.auth.entity.ChatUser;
import com.realtimechat.auth.repository.ChatUserRepository;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final ChatUserRepository repository;
    private final PasswordEncoder encoder;

    public AuthService(ChatUserRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String username = request.username().trim().toLowerCase();
        if (repository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        ChatUser user = repository.save(new ChatUser(
                username, encoder.encode(request.password()), LocalDateTime.now()));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse getCurrentUser(String username) {
        ChatUser user = repository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toResponse(user);
    }

    private AuthResponse toResponse(ChatUser user) {
        return new AuthResponse(user.getUsername(), user.getCreatedAt());
    }
}
package com.realtimechat.auth.service;

import com.realtimechat.auth.entity.ChatUser;
import com.realtimechat.auth.repository.ChatUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
public class ChatUserDetailsService implements UserDetailsService {
    private final ChatUserRepository repository;

    public ChatUserDetailsService(ChatUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        ChatUser user = repository.findByUsername(username)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles("USER")
                .build();
    }
}
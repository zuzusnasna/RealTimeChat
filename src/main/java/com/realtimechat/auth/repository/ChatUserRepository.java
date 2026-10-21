package com.realtimechat.auth.repository;

import com.realtimechat.auth.entity.ChatUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatUserRepository extends JpaRepository<ChatUser, Long> {
    Optional<ChatUser> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByDisplayNameIgnoreCaseAndUsernameNot(String displayName, String username);
}
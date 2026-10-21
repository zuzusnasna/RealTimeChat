package com.realtimechat.auth.repository;

import com.realtimechat.auth.entity.ChatUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatUserRepository extends JpaRepository<ChatUser, Long> {
    Optional<ChatUser> findByUsername(String username);
    List<ChatUser> findAllByUsernameIn(Collection<String> usernames);
    boolean existsByUsername(String username);
    boolean existsByDisplayNameIgnoreCaseAndUsernameNot(String displayName, String username);
}
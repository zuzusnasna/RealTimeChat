package com.realtimechat.chat.repository;

import com.realtimechat.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    void deleteByRoom_Id(Long roomId);
}
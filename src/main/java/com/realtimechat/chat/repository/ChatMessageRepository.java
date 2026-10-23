package com.realtimechat.chat.repository;

import com.realtimechat.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    @Query("""
            select count(m)
            from ChatMessage m
            where m.room.id = :roomId
              and m.sender <> :username
              and (:lastReadMessageId is null or m.id > :lastReadMessageId)
            """)
    long countUnreadMessages(
            @Param("roomId") Long roomId,
            @Param("username") String username,
            @Param("lastReadMessageId") Long lastReadMessageId
    );

    void deleteByRoom_Id(Long roomId);
}
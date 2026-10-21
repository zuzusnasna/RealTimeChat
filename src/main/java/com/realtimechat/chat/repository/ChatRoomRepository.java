package com.realtimechat.chat.repository;

import com.realtimechat.chat.entity.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from ChatRoom room where room.id = :roomId")
    Optional<ChatRoom> findByIdForUpdate(@Param("roomId") Long roomId);
}
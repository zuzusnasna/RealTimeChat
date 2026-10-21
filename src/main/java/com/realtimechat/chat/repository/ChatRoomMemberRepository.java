package com.realtimechat.chat.repository;

import com.realtimechat.chat.entity.ChatRoomMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByRoom_IdAndUsername(Long roomId, String username);

    List<ChatRoomMember> findByUsername(String username);

    List<ChatRoomMember> findByRoom_Id(Long roomId);

    Optional<ChatRoomMember> findByRoom_IdAndUsername(Long roomId, String username);

    Optional<ChatRoomMember> findFirstByRoom_IdOrderByJoinedAtAscIdAsc(Long roomId);

    @Query("""
            select count(member)
            from ChatRoomMember member
            where member.room.id = :roomId
              and member.username <> :sender
              and (member.lastReadMessageId is null or member.lastReadMessageId < :messageId)
            """)
    long countUnreadRecipients(
            @Param("roomId") Long roomId,
            @Param("sender") String sender,
            @Param("messageId") Long messageId
    );

    void deleteByRoom_Id(Long roomId);
}

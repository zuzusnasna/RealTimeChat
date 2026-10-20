package com.realtimechat.chat.repository;

import com.realtimechat.chat.entity.ChatRoomMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByRoom_IdAndUsername(Long roomId, String username);

    List<ChatRoomMember> findByUsername(String username);

    Optional<ChatRoomMember> findFirstByRoom_IdOrderByJoinedAtAscIdAsc(Long roomId);

    void deleteByRoom_Id(Long roomId);
}
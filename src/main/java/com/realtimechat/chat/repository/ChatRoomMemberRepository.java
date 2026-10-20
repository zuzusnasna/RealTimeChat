package com.realtimechat.chat.repository;

import com.realtimechat.chat.entity.ChatRoomMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByRoom_IdAndUsername(Long roomId, String username);

    List<ChatRoomMember> findByUsername(String username);
}

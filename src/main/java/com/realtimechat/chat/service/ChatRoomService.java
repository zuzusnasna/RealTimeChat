package com.realtimechat.chat.service;

import com.realtimechat.chat.dto.ChatRoomRequest;
import com.realtimechat.chat.dto.ChatRoomResponse;
import com.realtimechat.chat.entity.ChatRoom;
import com.realtimechat.chat.entity.ChatRoomMember;
import com.realtimechat.chat.repository.ChatMessageRepository;
import com.realtimechat.chat.repository.ChatRoomMemberRepository;
import com.realtimechat.chat.repository.ChatRoomRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;

    public ChatRoomService(
            ChatRoomRepository chatRoomRepository,
            ChatRoomMemberRepository memberRepository,
            ChatMessageRepository messageRepository
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public ChatRoomResponse createRoom(ChatRoomRequest request, String username) {
        ChatRoom room = chatRoomRepository.save(
                new ChatRoom(request.name().trim(), LocalDateTime.now(), username)
        );
        memberRepository.save(new ChatRoomMember(room, username, LocalDateTime.now()));
        return ChatRoomResponse.from(room, true, true);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getRooms(String username) {
        return chatRoomRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(room -> ChatRoomResponse.from(
                        room,
                        memberRepository.existsByRoom_IdAndUsername(room.getId(), username),
                        isOwner(room, username)
                ))
                .toList();
    }

    @Transactional
    public ChatRoomResponse joinRoom(Long roomId, String username) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."
                ));

        if (!memberRepository.existsByRoom_IdAndUsername(roomId, username)) {
            memberRepository.save(new ChatRoomMember(room, username, LocalDateTime.now()));
        }
        return ChatRoomResponse.from(
                room,
                true,
                isOwner(room, username)
        );
    }

    @Transactional
    public void deleteRoom(Long roomId, String username) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."
                ));

        if (!isOwner(room, username)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "채팅방을 만든 사용자만 삭제할 수 있습니다."
            );
        }

        // Remove dependent records first so existing foreign keys remain valid.
        messageRepository.deleteByRoom_Id(roomId);
        memberRepository.deleteByRoom_Id(roomId);
        chatRoomRepository.delete(room);
    }

    private boolean isOwner(ChatRoom room, String username) {
        if (room.getCreatedBy() != null && !room.getCreatedBy().isBlank()) {
            return room.getCreatedBy().equals(username);
        }

        // Existing rooms predate the createdBy column. Treat the first member as creator.
        return memberRepository.findFirstByRoom_IdOrderByJoinedAtAscIdAsc(room.getId())
                .map(ChatRoomMember::getUsername)
                .filter(username::equals)
                .isPresent();
    }
}
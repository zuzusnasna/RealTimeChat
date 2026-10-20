package com.realtimechat.chat.service;

import com.realtimechat.chat.dto.ChatRoomRequest;
import com.realtimechat.chat.dto.ChatRoomResponse;
import com.realtimechat.chat.entity.ChatRoom;
import com.realtimechat.chat.repository.ChatRoomRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;

    public ChatRoomService(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
    }

    @Transactional
    public ChatRoomResponse createRoom(ChatRoomRequest request) {
        ChatRoom room = new ChatRoom(request.name().trim(), LocalDateTime.now());
        return ChatRoomResponse.from(chatRoomRepository.save(room));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getRooms() {
        return chatRoomRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(ChatRoomResponse::from)
                .toList();
    }
}
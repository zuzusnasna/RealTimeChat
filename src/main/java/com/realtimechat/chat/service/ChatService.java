package com.realtimechat.chat.service;

import com.realtimechat.chat.dto.ChatMessage;
import com.realtimechat.chat.dto.ChatMessageResponse;
import com.realtimechat.chat.entity.ChatRoom;
import com.realtimechat.chat.repository.ChatMessageRepository;
import com.realtimechat.chat.repository.ChatRoomRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    private static final int HISTORY_LIMIT = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            ChatRoomRepository chatRoomRepository
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomRepository = chatRoomRepository;
    }

    @Transactional
    public ChatMessageResponse saveMessage(Long roomId, ChatMessage message) {
        String sender = message.getSender() == null ? "" : message.getSender().trim();
        String content = message.getContent() == null ? "" : message.getContent().trim();

        if (sender.isBlank() || sender.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이름은 1~50자로 입력해 주세요.");
        }
        if (content.isBlank() || content.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 1~1000자로 입력해 주세요.");
        }

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));

        com.realtimechat.chat.entity.ChatMessage saved = chatMessageRepository.save(
                new com.realtimechat.chat.entity.ChatMessage(
                        sender,
                        content,
                        LocalDateTime.now(),
                        room
                )
        );

        return ChatMessageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getHistory(Long roomId) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다.");
        }

        return chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(
                        roomId,
                        PageRequest.of(0, HISTORY_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .stream()
                .map(ChatMessageResponse::from)
                .sorted((a, b) -> a.createdAt().compareTo(b.createdAt()))
                .toList();
    }
}
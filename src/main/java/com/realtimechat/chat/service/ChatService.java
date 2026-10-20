package com.realtimechat.chat.service;

import com.realtimechat.chat.dto.ChatMessage;
import com.realtimechat.chat.repository.ChatMessageRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public ChatMessage saveMessage(ChatMessage message) {
        chatMessageRepository.save(
                new com.realtimechat.chat.entity.ChatMessage(
                        message.getSender(),
                        message.getContent(),
                        LocalDateTime.now()
                )
        );

        return message;
    }
}

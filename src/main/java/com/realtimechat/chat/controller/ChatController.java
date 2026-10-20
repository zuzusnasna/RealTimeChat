package com.realtimechat.chat.controller;

import com.realtimechat.chat.dto.ChatMessage;
import com.realtimechat.chat.dto.ChatMessageResponse;
import com.realtimechat.chat.service.ChatService;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/{roomId}")
    public void sendMessage(
            @DestinationVariable Long roomId,
            ChatMessage message,
            Principal principal
    ) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        // 발신자는 클라이언트 payload가 아니라 인증된 세션에서 가져옵니다.
        ChatMessageResponse savedMessage = chatService.saveMessage(roomId, principal.getName(), message);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, savedMessage);
    }
}
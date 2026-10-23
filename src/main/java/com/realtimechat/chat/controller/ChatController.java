package com.realtimechat.chat.controller;

import com.realtimechat.chat.dto.ChatMessage;
import com.realtimechat.chat.dto.ChatMessageResponse;
import com.realtimechat.chat.service.ChatMessageBroadcaster;
import com.realtimechat.chat.service.ChatService;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ChatMessageBroadcaster messageBroadcaster;

    public ChatController(ChatService chatService, ChatMessageBroadcaster messageBroadcaster) {
        this.chatService = chatService;
        this.messageBroadcaster = messageBroadcaster;
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

        // DB 저장이 성공한 메시지만 로컬 또는 Redis를 통해 전달합니다.
        long saveStart = System.nanoTime();
        ChatMessageResponse savedMessage = chatService.saveMessage(roomId, principal.getName(), message);
        long saveMessageTotalMs = (System.nanoTime() - saveStart) / 1_000_000;

        long broadcastStart = System.nanoTime();
        messageBroadcaster.broadcast(savedMessage);
        long broadcastMs = (System.nanoTime() - broadcastStart) / 1_000_000;

        log.info("[CHAT-PERF] roomId={} sender={} saveMessageCallMs={} broadcastCallMs={}",
                roomId, principal.getName(), saveMessageTotalMs, broadcastMs);
    }
}

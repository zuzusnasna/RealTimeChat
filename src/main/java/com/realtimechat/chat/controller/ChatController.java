package com.realtimechat.chat.controller;

import com.realtimechat.chat.dto.ChatMessage;
import com.realtimechat.chat.service.ChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat")
    @SendTo("/topic/chat")
    public ChatMessage sendMessage(ChatMessage message) {
        return chatService.saveMessage(message);
    }
}

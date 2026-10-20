package com.realtimechat.chat.controller;

import com.realtimechat.chat.dto.ChatRoomRequest;
import com.realtimechat.chat.dto.ChatRoomResponse;
import com.realtimechat.chat.dto.ChatMessageResponse;
import com.realtimechat.chat.service.ChatRoomService;
import com.realtimechat.chat.service.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatService chatService;

    public ChatRoomController(ChatRoomService chatRoomService, ChatService chatService) {
        this.chatRoomService = chatRoomService;
        this.chatService = chatService;
    }

    @GetMapping
    public List<ChatRoomResponse> getRooms() {
        return chatRoomService.getRooms();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatRoomResponse createRoom(@Valid @RequestBody ChatRoomRequest request) {
        return chatRoomService.createRoom(request);
    }

    @GetMapping("/{roomId}/messages")
    public List<ChatMessageResponse> getMessages(@PathVariable Long roomId) {
        return chatService.getHistory(roomId);
    }
}
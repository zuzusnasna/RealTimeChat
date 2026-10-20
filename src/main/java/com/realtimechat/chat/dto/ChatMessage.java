package com.realtimechat.chat.dto;

import lombok.Data;

@Data
public class ChatMessage {

    private String sender;
    private String content;

    public ChatMessage() {
    }

    public ChatMessage(String sender, String content) {
        this.sender = sender;
        this.content = content;
    }

}

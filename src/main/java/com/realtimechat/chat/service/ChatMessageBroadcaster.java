package com.realtimechat.chat.service;

import com.realtimechat.chat.dto.ChatMessageResponse;

/**
 * Delivers a persisted chat message to clients connected to this application
 * or to all application instances through Redis Pub/Sub.
 */
public interface ChatMessageBroadcaster {

    void broadcast(ChatMessageResponse message);
}

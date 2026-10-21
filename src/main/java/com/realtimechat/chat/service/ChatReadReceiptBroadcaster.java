package com.realtimechat.chat.service;

import com.realtimechat.chat.service.ChatService.ReadReceiptEvent;

public interface ChatReadReceiptBroadcaster {
    void broadcast(ReadReceiptEvent event);
}

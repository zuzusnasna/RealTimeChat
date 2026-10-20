package com.realtimechat.chat.config;

import com.realtimechat.chat.dto.ChatMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "chat.redis.enabled", havingValue = "true")
public class RedisChatMessageListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMessageListener.class);

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public RedisChatMessageListener(
            ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatMessageResponse chatMessage = objectMapper.readValue(
                    message.getBody(),
                    ChatMessageResponse.class
            );
            if (chatMessage.roomId() == null) {
                return;
            }
            messagingTemplate.convertAndSend("/topic/rooms/" + chatMessage.roomId(), chatMessage);
        } catch (Exception exception) {
            // Ignore malformed events without stopping message delivery for other events.
            log.warn("Redis 채팅 이벤트 처리에 실패했습니다.", exception);
        }
    }
}

package com.realtimechat.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimechat.chat.dto.ChatMessageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chat.redis.enabled", havingValue = "true")
public class RedisChatMessageListener implements MessageListener {

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
            // A malformed event must not terminate the Redis listener container.
            // Logging is intentionally kept concise to avoid dumping message content.
            org.slf4j.LoggerFactory.getLogger(RedisChatMessageListener.class)
                    .warn("Redis 채팅 이벤트 처리에 실패했습니다.", exception);
        }
    }
}

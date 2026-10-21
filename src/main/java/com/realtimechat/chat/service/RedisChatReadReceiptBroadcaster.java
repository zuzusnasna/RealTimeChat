package com.realtimechat.chat.service;

import com.realtimechat.chat.service.ChatService.ReadReceiptEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "chat.redis.enabled", havingValue = "true")
public class RedisChatReadReceiptBroadcaster implements ChatReadReceiptBroadcaster {

    public static final String CHANNEL = "realtime-chat:read-receipts";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatReadReceiptBroadcaster(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void broadcast(ReadReceiptEvent event) {
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            throw new IllegalStateException("읽음 상태를 Redis 이벤트로 변환하지 못했습니다.", exception);
        }
    }
}

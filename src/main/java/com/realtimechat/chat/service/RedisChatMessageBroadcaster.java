package com.realtimechat.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimechat.chat.dto.ChatMessageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chat.redis.enabled", havingValue = "true")
public class RedisChatMessageBroadcaster implements ChatMessageBroadcaster {

    static final String CHANNEL = "realtime-chat:messages";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatMessageBroadcaster(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void broadcast(ChatMessageResponse message) {
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("채팅 메시지를 Redis 이벤트로 변환하지 못했습니다.", exception);
        }
    }
}

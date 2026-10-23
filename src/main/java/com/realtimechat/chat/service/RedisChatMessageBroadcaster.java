package com.realtimechat.chat.service;

import com.realtimechat.chat.dto.ChatMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "chat.redis.enabled", havingValue = "true")
public class RedisChatMessageBroadcaster implements ChatMessageBroadcaster {

    public static final String CHANNEL = "realtime-chat:messages";
    private static final Logger log = LoggerFactory.getLogger(RedisChatMessageBroadcaster.class);

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
            long start = System.nanoTime();
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(message));
            long publishMs = (System.nanoTime() - start) / 1_000_000;
            log.info("[CHAT-PERF] roomId={} messageId={} redisPublishMs={}",
                    message.roomId(), message.id(), publishMs);
        } catch (Exception exception) {
            throw new IllegalStateException("채팅 메시지를 Redis 이벤트로 변환하지 못했습니다.", exception);
        }
    }
}

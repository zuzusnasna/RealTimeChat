package com.realtimechat.chat.config;

import com.realtimechat.chat.service.RedisChatMessageBroadcaster;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
@ConditionalOnProperty(name = "chat.redis.enabled", havingValue = "true")
public class RedisChatPubSubConfig {

    @Bean
    ChannelTopic chatMessageTopic() {
        return new ChannelTopic(RedisChatMessageBroadcaster.CHANNEL);
    }

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter chatMessageListenerAdapter,
            ChannelTopic chatMessageTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatMessageListenerAdapter, chatMessageTopic);
        return container;
    }

    @Bean
    MessageListenerAdapter chatMessageListenerAdapter(RedisChatMessageListener listener) {
        return new MessageListenerAdapter(listener, "onMessage");
    }
}

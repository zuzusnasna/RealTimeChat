package com.realtimechat.chat.config;

import com.realtimechat.chat.service.RedisChatMessageBroadcaster;
import com.realtimechat.chat.service.RedisChatReadReceiptBroadcaster;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(name = "chat.redis.enabled", havingValue = "true")
public class RedisChatPubSubConfig {

    @Bean
    ChannelTopic chatMessageTopic() {
        return new ChannelTopic(RedisChatMessageBroadcaster.CHANNEL);
    }

    @Bean
    ChannelTopic chatReadReceiptTopic() {
        return new ChannelTopic(RedisChatReadReceiptBroadcaster.CHANNEL);
    }

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisChatMessageListener messageListener,
            RedisChatReadReceiptListener readReceiptListener,
            ChannelTopic chatMessageTopic,
            ChannelTopic chatReadReceiptTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageListener, chatMessageTopic);
        container.addMessageListener(readReceiptListener, chatReadReceiptTopic);
        return container;
    }
}

package com.realtimechat.chat.config;

import com.realtimechat.chat.service.RedisChatMessageBroadcaster;
import com.realtimechat.chat.service.RedisChatReadReceiptBroadcaster;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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

    @Bean(name = "chatRedisTaskExecutor")
    ThreadPoolTaskExecutor chatRedisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("redis-chat-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "readReceiptRedisTaskExecutor")
    ThreadPoolTaskExecutor readReceiptRedisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("redis-read-receipt-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "redisChatMessageListenerContainer")
    RedisMessageListenerContainer redisChatMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisChatMessageListener messageListener,
            ChannelTopic chatMessageTopic,
            ThreadPoolTaskExecutor chatRedisTaskExecutor
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(chatRedisTaskExecutor);
        container.addMessageListener(messageListener, chatMessageTopic);
        return container;
    }

    @Bean(name = "redisChatReadReceiptListenerContainer")
    RedisMessageListenerContainer redisChatReadReceiptListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisChatReadReceiptListener readReceiptListener,
            ChannelTopic chatReadReceiptTopic,
            ThreadPoolTaskExecutor readReceiptRedisTaskExecutor
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(readReceiptRedisTaskExecutor);
        container.addMessageListener(readReceiptListener, chatReadReceiptTopic);
        return container;
    }
}

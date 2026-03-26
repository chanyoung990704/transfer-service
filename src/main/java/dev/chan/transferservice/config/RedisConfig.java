package dev.chan.transferservice.config;

import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.*;

@Configuration
public class RedisConfig {

    public static final String TRANSFER_STATUS_TOPIC = "transfer-status-topic";

    /**
     * SortedSet 및 일반 객체 처리를 위한 RedisTemplate 설정
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    /**
     * Redis Pub/Sub 메시지 리스너 컨테이너
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory,
            MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(listenerAdapter, new ChannelTopic(TRANSFER_STATUS_TOPIC));
        return container;
    }

    /**
     * 메시지 구독 시 처리할 실제 리스너 클래스와 메서드 매핑
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(RedisSubscriber subscriber) {
        // RedisSubscriber 클래스의 onMessage 메서드를 호출하도록 설정
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    // RedisSubscriber는 별도 컴포넌트로 생성 예정
}

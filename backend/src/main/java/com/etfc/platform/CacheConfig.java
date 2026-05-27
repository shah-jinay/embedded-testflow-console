package com.etfc.platform;

import com.etfc.dashboard.DashboardSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class CacheConfig {

  @Bean
  @SuppressWarnings({"unchecked", "rawtypes"})
  public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Raw-type cast: at runtime the serializer correctly uses DashboardSummaryResponse.class
    // for both serialize and deserialize; generics are erased and cause no issue.
    RedisSerializer<Object> valueSerializer =
        (RedisSerializer<Object>)
            (RedisSerializer) new Jackson2JsonRedisSerializer<>(mapper, DashboardSummaryResponse.class);

    RedisCacheConfiguration config =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(15))
            .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer))
            .disableCachingNullValues();

    return RedisCacheManager.builder(factory)
        .cacheDefaults(config)
        .withCacheConfiguration("dashboard-summary", config)
        .build();
  }
}

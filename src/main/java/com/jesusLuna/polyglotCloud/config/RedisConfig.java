package com.jesusLuna.polyglotCloud.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Usar StringRedisSerializer para las claves
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Usar JSON para los valores
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))  // TTL por defecto
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())
                )
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }

    @Bean
    public ProxyManager<String> proxyManager() {
            // Construir URL de Redis usando las propiedades
            String redisUrl = buildRedisUrl();
            log.info("Connecting to Redis for Bucket4j: {}", sanitizeUrlForLogging(redisUrl));
            
            RedisClient redisClient = RedisClient.create(redisUrl);
            
            // Usar codec específico: String para keys, byte[] para values
            StatefulRedisConnection<String, byte[]> redisConnection = redisClient
                    .connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

            return LettuceBasedProxyManager.builderFor(redisConnection)
                    .build();
        }

    /**
     * Construye la URL de conexión a Redis basada en las propiedades
     */
    private String buildRedisUrl() {
        StringBuilder urlBuilder = new StringBuilder("redis://");
        
        /// Solo agregar password si no está vacío o null
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            urlBuilder.append(":").append(redisPassword).append("@");
        }
        
        // Agregar host y puerto
        urlBuilder.append(redisHost).append(":").append(redisPort);
        
        return urlBuilder.toString();
    }

    /**
     * Sanitiza la URL para logging (oculta password)
     */
    private String sanitizeUrlForLogging(String url) {
        return url.replaceAll(":[^:@]*@", ":***@");
    }
}
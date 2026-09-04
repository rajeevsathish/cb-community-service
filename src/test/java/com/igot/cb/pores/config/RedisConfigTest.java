package com.igot.cb.pores.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

class RedisConfigTest {

  private RedisConfig redisConfig;

  @BeforeEach
  void setUp() {
    redisConfig = new RedisConfig();
    ReflectionTestUtils.setField(redisConfig, "redisHost", "localhost");
    ReflectionTestUtils.setField(redisConfig, "redisPort", 6379);
    ReflectionTestUtils.setField(redisConfig, "redisDataHost", "localhost");
    ReflectionTestUtils.setField(redisConfig, "redisDataPort", 6380);
  }

  @Test
  void redisConnectionFactory_isConfiguredWithHostAndPort() {
    RedisConnectionFactory factory = redisConfig.redisConnectionFactory();

    assertNotNull(factory);
    LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;
    assertEquals("localhost", lettuceFactory.getHostName());
    assertEquals(6379, lettuceFactory.getPort());
  }

  @Test
  void redisDataConnectionFactory_isConfiguredWithSeparateHostAndPort() {
    RedisConnectionFactory factory = redisConfig.redisDataConnectionFactory();

    LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;
    assertEquals("localhost", lettuceFactory.getHostName());
    assertEquals(6380, lettuceFactory.getPort());
  }

  @Test
  void redisTemplate_usesStringSerializersAndGivenConnectionFactory() {
    RedisConnectionFactory factory = redisConfig.redisConnectionFactory();

    RedisTemplate<String, String> template = redisConfig.redisTemplate(factory);

    assertSame(factory, template.getConnectionFactory());
    assertTrue(template.getKeySerializer() instanceof StringRedisSerializer);
    assertTrue(template.getValueSerializer() instanceof StringRedisSerializer);
    assertTrue(template.getHashKeySerializer() instanceof StringRedisSerializer);
    assertTrue(template.getHashValueSerializer() instanceof StringRedisSerializer);
  }

  @Test
  void redisDataTemplate_usesStringSerializersAndGivenConnectionFactory() {
    RedisConnectionFactory factory = redisConfig.redisDataConnectionFactory();

    RedisTemplate<String, String> template = redisConfig.redisDataTemplate(factory);

    assertSame(factory, template.getConnectionFactory());
    assertTrue(template.getKeySerializer() instanceof StringRedisSerializer);
    assertTrue(template.getValueSerializer() instanceof StringRedisSerializer);
  }

  @Test
  void searchResultRedisTemplate_usesGivenConnectionFactory() {
    RedisConnectionFactory factory = redisConfig.redisConnectionFactory();

    var template = redisConfig.searchResultRedisTemplate(factory);

    assertSame(factory, template.getConnectionFactory());
    assertTrue(template.getKeySerializer() instanceof StringRedisSerializer);
  }

  @Test
  void redisObjectTemplate_usesJsonValueSerializer() {
    RedisConnectionFactory factory = redisConfig.redisConnectionFactory();

    RedisTemplate<String, Object> template = redisConfig.redisObjectTemplate(factory);

    assertSame(factory, template.getConnectionFactory());
    assertTrue(template.getKeySerializer() instanceof StringRedisSerializer);
    assertTrue(template.getValueSerializer() instanceof GenericJackson2JsonRedisSerializer);
  }
}

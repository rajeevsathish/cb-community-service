package com.igot.cb.pores.cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

  @Mock
  private RedisTemplate<String, String> redisTemplate;

  @Mock
  private RedisTemplate<String, String> redisDataTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Mock
  private HashOperations<String, String, String> hashOperations;

  @Mock
  private CbServerProperties properties;

  private CacheService cacheService;

  @BeforeEach
  void setUp() {
    cacheService = new CacheService();
    ReflectionTestUtils.setField(cacheService, "redisTemplate", redisTemplate);
    ReflectionTestUtils.setField(cacheService, "redisDataTemplate", redisDataTemplate);
    ReflectionTestUtils.setField(cacheService, "objectMapper", new ObjectMapper());
    ReflectionTestUtils.setField(cacheService, "cacheTtl", 300L);
    ReflectionTestUtils.setField(cacheService, "properties", properties);
  }

  @Test
  void putCache_writesSerializedValue_withTtl() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    cacheService.putCache("comm1", java.util.Map.of("id", "comm1"));

    verify(valueOperations).set(eq(Constants.REDIS_KEY_PREFIX + "comm1"), anyString(), eq(300L), eq(TimeUnit.SECONDS));
  }

  @Test
  void putCache_doesNotThrow_whenRedisFails() {
    when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

    assertDoesNotThrow(() -> cacheService.putCache("comm1", java.util.Map.of("id", "comm1")));
  }

  @Test
  void getCache_returnsStoredValue() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(Constants.REDIS_KEY_PREFIX + "comm1")).thenReturn("{\"id\":\"comm1\"}");

    String result = cacheService.getCache("comm1");

    assertEquals("{\"id\":\"comm1\"}", result);
  }

  @Test
  void getCache_returnsNull_whenRedisFails() {
    when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

    String result = cacheService.getCache("comm1");

    assertNull(result);
  }

  @Test
  void deleteCache_logsSuccess_whenKeyDeleted() {
    when(redisTemplate.delete(Constants.REDIS_KEY_PREFIX + "comm1")).thenReturn(true);

    Long result = cacheService.deleteCache("comm1");

    assertNull(result);
  }

  @Test
  void deleteCache_logsWarning_whenKeyMissing() {
    when(redisTemplate.delete(Constants.REDIS_KEY_PREFIX + "comm1")).thenReturn(false);

    Long result = cacheService.deleteCache("comm1");

    assertNull(result);
  }

  @Test
  void deleteCache_doesNotThrow_whenRedisFails() {
    when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("redis down"));

    assertDoesNotThrow(() -> cacheService.deleteCache("comm1"));
  }

  @Test
  void addUsersToHash_putsEachUserAndSetsExpiry() {
    when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
    when(properties.getRedisCommunityUserDataTtlSeconds()).thenReturn(600L);

    cacheService.addUsersToHash("comm1-users", Set.of("u1", "u2"));

    verify(hashOperations).putIfAbsent("comm1-users", "u1", "u1");
    verify(hashOperations).putIfAbsent("comm1-users", "u2", "u2");
    verify(redisTemplate).expire("comm1-users", 600L, TimeUnit.SECONDS);
  }

  @Test
  void addUsersToHash_doesNotThrow_whenRedisFails() {
    when(redisTemplate.<String, String>opsForHash()).thenThrow(new RuntimeException("redis down"));

    assertDoesNotThrow(() -> cacheService.addUsersToHash("comm1-users", Set.of("u1")));
  }

  private Cursor<java.util.Map.Entry<String, String>> cursorOf(List<String> userIds) {
    Iterator<String> iterator = userIds.iterator();
    Cursor<java.util.Map.Entry<String, String>> cursor = mock(Cursor.class);
    when(cursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
    when(cursor.next()).thenAnswer(invocation -> {
      String id = iterator.next();
      return new AbstractMap.SimpleEntry<>(id, id);
    });
    return cursor;
  }

  @Test
  void getPaginatedUsersFromHash_returnsSortedPage() {
    when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
    when(properties.getRedisScanCountSize()).thenReturn(100);
    Cursor<java.util.Map.Entry<String, String>> cursor = cursorOf(List.of("u3", "u1", "u2", "u4"));
    when(hashOperations.scan(eq("comm1-users"), org.mockito.ArgumentMatchers.any())).thenReturn(cursor);

    List<String> page = cacheService.getPaginatedUsersFromHash("comm1-users", 0, 2);

    assertEquals(List.of("u1", "u2"), page);
  }

  @Test
  void getPaginatedUsersFromHash_returnsEmptyList_whenOffsetBeyondSize() {
    when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
    when(properties.getRedisScanCountSize()).thenReturn(100);
    Cursor<java.util.Map.Entry<String, String>> cursor = cursorOf(List.of("u1"));
    when(hashOperations.scan(eq("comm1-users"), org.mockito.ArgumentMatchers.any())).thenReturn(cursor);

    List<String> page = cacheService.getPaginatedUsersFromHash("comm1-users", 5, 2);

    assertTrue(page.isEmpty());
  }

  @Test
  void getPaginatedUsersFromHash_returnsEmptyList_whenRedisFails() {
    when(redisTemplate.<String, String>opsForHash()).thenThrow(new RuntimeException("redis down"));

    List<String> page = cacheService.getPaginatedUsersFromHash("comm1-users", 0, 2);

    assertTrue(page.isEmpty());
  }

  @Test
  void getListSize_returnsHashSize() {
    when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
    when(hashOperations.size("comm1-users")).thenReturn(4L);

    Long size = cacheService.getListSize("comm1-users");

    assertEquals(4L, size);
  }

  @Test
  void getListSize_returnsNull_whenRedisFails() {
    when(redisTemplate.<String, String>opsForHash()).thenThrow(new RuntimeException("redis down"));

    Long size = cacheService.getListSize("comm1-users");

    assertNull(size);
  }

  @Test
  void deleteUserFromHash_removesField() {
    when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
    when(hashOperations.delete("comm1-users", "u1")).thenReturn(1L);

    cacheService.deleteUserFromHash("comm1-users", "u1");

    verify(hashOperations).delete("comm1-users", "u1");
  }

  @Test
  void deleteUserFromHash_doesNotThrow_whenFieldMissing() {
    when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
    when(hashOperations.delete("comm1-users", "u1")).thenReturn(0L);

    assertDoesNotThrow(() -> cacheService.deleteUserFromHash("comm1-users", "u1"));
  }

  @Test
  void hget_returnsValuesForEachKey() {
    when(redisDataTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("k1")).thenReturn("v1");
    when(valueOperations.get("k2")).thenReturn("v2");

    List<Object> result = cacheService.hget(List.of("k1", "k2"));

    assertEquals(List.of("v1", "v2"), result);
  }

  @Test
  void hget_returnsEmptyList_whenRedisFails() {
    when(redisDataTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

    List<Object> result = cacheService.hget(List.of("k1"));

    assertTrue(result.isEmpty());
  }
}

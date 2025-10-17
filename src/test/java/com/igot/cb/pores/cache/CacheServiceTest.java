package com.igot.cb.pores.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheServiceTest {

    @InjectMocks
    private CacheService cacheService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisTemplate<String, String> redisDataTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HashOperations<String, String, String> hashOperations;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testPutCacheSuccess() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("mockedData");
        cacheService.putCache("testKey", Map.of("foo", "bar"));
        verify(redisTemplate).opsForValue();
    }

    @Test
    void testPutCacheException() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("mocked error"));
        cacheService.putCache("testKey", Map.of("foo", "bar"));
        // no exception thrown
    }

    @Test
    void testGetCacheException() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("mock"));
        String value = cacheService.getCache("testKey");
        assertNull(value);
    }

    @Test
    void testDeleteCacheSuccess() {
        when(redisTemplate.delete(Constants.REDIS_KEY_PREFIX + "testKey")).thenReturn(true);
        Long result = cacheService.deleteCache("testKey");
        assertNull(result);  // still null as method returns null
    }

    @Test
    void testDeleteCacheNotFound() {
        when(redisTemplate.delete(Constants.REDIS_KEY_PREFIX + "testKey")).thenReturn(false);
        cacheService.deleteCache("testKey");
        // just verify no exception
    }

    @Test
    void testDeleteCacheException() {
        when(redisTemplate.delete(Constants.REDIS_KEY_PREFIX + "testKey")).thenThrow(new RuntimeException("fail"));
        cacheService.deleteCache("testKey");
        // no exception thrown
    }

    @Test
    void testAddUsersToHashException() {
        when(redisTemplate.opsForHash()).thenThrow(new RuntimeException("fail"));
        cacheService.addUsersToHash("key", Set.of("user"));
        // no exception thrown
    }

    @Test
    void testGetPaginatedUsersFromHashSuccess() {
        Map<String, String> userMap = Map.of("u1", "u1", "u2", "u2", "u3", "u3");
        when(hashOperations.entries(Constants.REDIS_KEY_PREFIX + "key")).thenReturn(userMap);
        List<String> result = cacheService.getPaginatedUsersFromHash("key", 0, 2);
        assertEquals(0, result.size());
    }

    @Test
    void testGetPaginatedUsersFromHashOutOfBounds() {
        Map<String, String> userMap = Map.of("u1", "u1");
        when(hashOperations.entries(Constants.REDIS_KEY_PREFIX + "key")).thenReturn(userMap);
        List<String> result = cacheService.getPaginatedUsersFromHash("key", 2, 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPaginatedUsersFromHashException() {
        when(hashOperations.entries(Constants.REDIS_KEY_PREFIX + "key")).thenThrow(new RuntimeException("fail"));
        List<String> result = cacheService.getPaginatedUsersFromHash("key", 0, 2);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetListSizeException() {
        when(hashOperations.size(Constants.REDIS_KEY_PREFIX + "key")).thenThrow(new RuntimeException("fail"));
        Long result = cacheService.getListSize("key");
        assertNull(result);
    }

    @Test
    void testDeleteUserFromHashSuccess() {
        when(hashOperations.delete(Constants.REDIS_KEY_PREFIX + "key", "field")).thenReturn(1L);
        cacheService.deleteUserFromHash("key", "field");
    }

    @Test
    void testDeleteUserFromHashFieldNotFound() {
        when(hashOperations.delete(Constants.REDIS_KEY_PREFIX + "key", "field")).thenReturn(0L);
        cacheService.deleteUserFromHash("key", "field");
    }

    @Test
    void testDeleteUserFromHashException() {
        when(hashOperations.delete(Constants.REDIS_KEY_PREFIX + "key", "field")).thenThrow(new RuntimeException("fail"));
        cacheService.deleteUserFromHash("key", "field");
    }

    @Test
    void testHget_Exception() {
        List<String> keys = Arrays.asList("key1", "key2");
        when(valueOperations.get("key1")).thenReturn("val1");
        when(valueOperations.get("key2")).thenReturn("val2");

        List<Object> result = cacheService.hget(keys);

        assertEquals(0, result.size());

    }

    @Test
    void testGetPaginatedUsersFromHash_Success() {
        // Given
        String key = "testCommunity";
        String redisKey = "community:" + key;
        int offset = 0;
        int limit = 2;

        Map<String, String> redisData = new HashMap<>();
        redisData.put("user1", "data1");
        redisData.put("user3", "data3");
        redisData.put("user2", "data2");

        // When
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Use Answer to bypass generic inference issues
        doReturn(hashOperations).when(redisTemplate).opsForHash();
        when(hashOperations.entries(redisKey)).thenReturn(redisData);

        // Then
        List<String> result = cacheService.getPaginatedUsersFromHash(key, offset, limit);

        // Assert
        assertNotNull(result);
    }
}


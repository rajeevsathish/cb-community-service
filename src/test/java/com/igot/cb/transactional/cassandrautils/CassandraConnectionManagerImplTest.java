package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.PropertiesCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CassandraConnectionManagerImplTest {

    @Mock
    PropertiesCache propertiesCache;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetConsistencyLevel_valid() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("LOCAL_QUORUM");
            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, level);
        }
    }

    @Test
    void testGetConsistencyLevel_invalid() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("INVALID");
            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertNull(level);
        }
    }

    @Test
    void testGetConsistencyLevel_blank() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("");
            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertNull(level);
        }
    }

    @Test
    void testRegisterShutdownHook() {
        assertDoesNotThrow(CassandraConnectionManagerImpl::registerShutdownHook);
    }

    @Test
    void testConstructorThrowsException_whenHostIsBlank() {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = Mockito.mockStatic(PropertiesCache.class)) {
            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");
            CustomException exception = assertThrows(CustomException.class, CassandraConnectionManagerImpl::new);
            assertEquals("Cassandra host is not configured", exception.getMessage());
        }
    }

    @Test
    void testCreateCassandraConnectionWithKeySpaces_throwsException() throws Exception {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = mockStatic(PropertiesCache.class)) {
            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(anyString())).thenThrow(new RuntimeException("fail"));
            Method method = CassandraConnectionManagerImpl.class
                    .getDeclaredMethod("createCassandraConnectionWithKeySpaces", String.class);
            method.setAccessible(true);
            Exception thrown = assertThrows(Exception.class, () -> method.invoke(null, "test"));
            Throwable cause = thrown.getCause();
            assertInstanceOf(CustomException.class, cause);
            assertTrue(cause.getMessage().contains("fail"));
        }
    }


    @Test
    void testResourceCleanup_noSessions() {
        CassandraConnectionManagerImpl.ResourceCleanUp cleanup = new CassandraConnectionManagerImpl.ResourceCleanUp();
        assertDoesNotThrow(cleanup::run);
    }

    @Test
    void testGetSession_returnsExistingSession() {
        CqlSession mockSession = mock(CqlSession.class);
        when(mockSession.isClosed()).thenReturn(false);
        try {
            java.lang.reflect.Field f = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, CqlSession> map = (Map<String, CqlSession>) f.get(null);
            map.clear();
            map.put("key", mockSession);

            CassandraConnectionManagerImpl impl = mock(CassandraConnectionManagerImpl.class);
            when(impl.getSession("key")).thenCallRealMethod();

            assertEquals(mockSession, impl.getSession("key"));
        } catch (Exception e) {
            fail(e);
        }
    }


    @Test
    void testCreateCassandraConnectionWithKeySpaces_success() throws Exception {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = mockStatic(PropertiesCache.class);
             MockedStatic<CqlSession> sessionStatic = mockStatic(CqlSession.class)) {

            PropertiesCache mockCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockCache);
            when(mockCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("127.0.0.1");
            when(mockCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("1");
            when(mockCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
            when(mockCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("10");
            sessionStatic.when(CqlSession::builder)
                    .thenThrow(new RuntimeException("builder not available"));
            Method method = CassandraConnectionManagerImpl.class
                    .getDeclaredMethod("createCassandraConnectionWithKeySpaces", String.class);
            method.setAccessible(true);
            Exception thrown = assertThrows(Exception.class, () -> method.invoke(null, "testKey"));
            Throwable cause = thrown.getCause();
            assertInstanceOf(CustomException.class, cause);
            assertEquals("builder not available", cause.getMessage());
        }
    }


    private ConsistencyLevel invokeGetConsistencyLevel() {
        try {
            Method method = CassandraConnectionManagerImpl.class.getDeclaredMethod("getConsistencyLevel");
            method.setAccessible(true);
            return (ConsistencyLevel) method.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
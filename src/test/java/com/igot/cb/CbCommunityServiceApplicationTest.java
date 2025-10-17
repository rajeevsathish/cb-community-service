package com.igot.cb;

import com.igot.cb.pores.util.PropertiesCache;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CbCommunityServiceApplicationTest {

    @Test
    void testGetClientHttpRequestFactory_returnsFactory() throws Exception {
        // Arrange
        PropertiesCache mockCache = mock(PropertiesCache.class);

        try (MockedStatic<PropertiesCache> mockedStatic = mockStatic(PropertiesCache.class)) {
            mockedStatic.when(PropertiesCache::getInstance).thenReturn(mockCache);

            when(mockCache.getProperty("rest.client.connect.timeout")).thenReturn("1000");
            when(mockCache.getProperty("rest.client.read.timeout")).thenReturn("2000");
            when(mockCache.getProperty("rest.client.connection.request.timeout")).thenReturn("3000");
            when(mockCache.getProperty("rest.client.max.connections")).thenReturn("500");
            when(mockCache.getProperty("rest.client.max.connections.per.route")).thenReturn("200");

            CbCommunityServiceApplication app = new CbCommunityServiceApplication();

            // Use reflection to invoke private method
            var method = CbCommunityServiceApplication.class.getDeclaredMethod("getClientHttpRequestFactory");
            method.setAccessible(true);
            ClientHttpRequestFactory factory = (ClientHttpRequestFactory) method.invoke(app);

            // Assert
            assertNotNull(factory);
            assertTrue(factory instanceof HttpComponentsClientHttpRequestFactory);
        }
    }
}


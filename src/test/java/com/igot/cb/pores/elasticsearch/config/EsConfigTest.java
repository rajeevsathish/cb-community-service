package com.igot.cb.pores.elasticsearch.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class EsConfigTest {

    private EsConfig esConfig;

    @BeforeEach
    void setUp() {
        esConfig = new EsConfig();

        // Set values using reflection
        ReflectionTestUtils.setField(esConfig, "elasticsearchHost", "localhost");
        ReflectionTestUtils.setField(esConfig, "elasticsearchPort", 9200);
        ReflectionTestUtils.setField(esConfig, "elasticsearchUsername", "elastic");
        ReflectionTestUtils.setField(esConfig, "elasticsearchPassword", "changeme");

        ReflectionTestUtils.setField(esConfig, "sbESClientHost", "localhost,127.0.0.1");
        ReflectionTestUtils.setField(esConfig, "sbESClientPort", "9201,9202");
        ReflectionTestUtils.setField(esConfig, "sbESClientUsername", "sb_user");
        ReflectionTestUtils.setField(esConfig, "sbESClientPassword", "sb_pass");
    }

    @Test
    void testElasticsearchClientBean() {
        try (MockedStatic<RestClient> restClientMockedStatic = Mockito.mockStatic(RestClient.class)) {
            RestClientBuilder mockBuilder = mock(RestClientBuilder.class, RETURNS_SELF);
            RestClient mockRestClient = mock(RestClient.class);

            restClientMockedStatic.when(() -> RestClient.builder(any(HttpHost.class)))
                    .thenReturn(mockBuilder);

            when(mockBuilder.build()).thenReturn(mockRestClient);

            ElasticsearchClient client = esConfig.elasticsearchClient();
            assertNotNull(client);
        }
    }

    @Test
    void testSbEsClientBean() {
        try (MockedStatic<RestClient> restClientMockedStatic = Mockito.mockStatic(RestClient.class)) {
            RestClientBuilder mockBuilder = mock(RestClientBuilder.class, RETURNS_SELF);
            RestClient mockRestClient = mock(RestClient.class);

            restClientMockedStatic.when(() -> RestClient.builder(any(HttpHost[].class)))
                    .thenReturn(mockBuilder);

            when(mockBuilder.build()).thenReturn(mockRestClient);

            ElasticsearchClient client = esConfig.sbESClient();
            assertNotNull(client);
        }
    }
}


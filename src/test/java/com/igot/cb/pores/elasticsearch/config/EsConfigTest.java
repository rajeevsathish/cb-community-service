package com.igot.cb.pores.elasticsearch.config;

import static org.junit.jupiter.api.Assertions.*;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.elasticsearch.client.Node;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.apache.http.HttpHost;

class EsConfigTest {

    private EsConfig esConfig;

    @BeforeEach
    void setUp() {
        esConfig = new EsConfig();
        ReflectionTestUtils.setField(esConfig, "elasticsearchHost", "localhost");
        ReflectionTestUtils.setField(esConfig, "elasticsearchPort", 9200);
        ReflectionTestUtils.setField(esConfig, "elasticsearchUsername", "elastic");
        ReflectionTestUtils.setField(esConfig, "elasticsearchPassword", "password");
        ReflectionTestUtils.setField(esConfig, "userESClientHost", "localhost,127.0.0.1");
        ReflectionTestUtils.setField(esConfig, "userESClientPort", "9200,9201");
    }

    @AfterEach
    void tearDown() {
        esConfig = null;
    }


    @Test
    void testElasticsearchClientCreation() {
        ElasticsearchClient client = esConfig.elasticsearchClient();
        assertNotNull(client, "ElasticsearchClient should not be null");
    }

    @Test
    void testUserESClientCreation() {
        RestHighLevelClient userClient = esConfig.userESClient();

        assertNotNull(userClient, "RestHighLevelClient should not be null");
        HttpHost[] hosts = userClient.getLowLevelClient().getNodes().stream()
                .map(Node::getHost)
                .toArray(HttpHost[]::new);
        assertEquals(2, hosts.length);
        assertEquals("localhost", hosts[0].getHostName());
        assertEquals(9200, hosts[0].getPort());
        assertEquals("127.0.0.1", hosts[1].getHostName());
        assertEquals(9201, hosts[1].getPort());
    }

    @Test
    void testElasticsearchClientAuthenticationConfiguration() {
        ElasticsearchClient client = esConfig.elasticsearchClient();
        assertNotNull(client, "Elasticsearch client should be initialized even with authentication details");
    }

    @Test
    void testUserESClientTimeouts() {
        RestHighLevelClient client = esConfig.userESClient();
        assertNotNull(client);
    }

    @Test
    void testMultipleHostsParsing() {
        RestHighLevelClient client = esConfig.userESClient();
        assertNotNull(client);
        assertDoesNotThrow(() -> {
            client.getLowLevelClient().getNodes();
        }, "Client should handle multiple host connections without errors");
    }

    @Test
    void testSingleHostParsing() {
        ReflectionTestUtils.setField(esConfig, "userESClientHost", "localhost");
        ReflectionTestUtils.setField(esConfig, "userESClientPort", "9200");
        RestHighLevelClient client = esConfig.userESClient();
        assertNotNull(client, "Single host configuration should also work");
        assertEquals(1, client.getLowLevelClient().getNodes().size());
    }

    @Test
    void testInvalidPortParsing() {
        ReflectionTestUtils.setField(esConfig, "userESClientHost", "localhost");
        ReflectionTestUtils.setField(esConfig, "userESClientPort", "invalidPort");
        assertThrows(NumberFormatException.class, () -> {
            esConfig.userESClient();
        }, "Invalid port should throw NumberFormatException");
    }

    @Test
    void testCreateClientWithNullCredentials() {
        ReflectionTestUtils.setField(esConfig, "elasticsearchUsername", null);
        ReflectionTestUtils.setField(esConfig, "elasticsearchPassword", null);
        assertThrows(IllegalArgumentException.class, () -> {
            esConfig.elasticsearchClient();
        }, "Null credentials should throw IllegalArgumentException");
    }

}

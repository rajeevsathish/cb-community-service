package com.igot.cb.pores.elasticsearch.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EsConfigTest {

  private EsConfig esConfig;

  @BeforeEach
  void setUp() {
    esConfig = new EsConfig();
    ReflectionTestUtils.setField(esConfig, "elasticsearchHost", "localhost");
    ReflectionTestUtils.setField(esConfig, "elasticsearchPort", 9200);
    ReflectionTestUtils.setField(esConfig, "elasticsearchUsername", "elastic");
    ReflectionTestUtils.setField(esConfig, "elasticsearchPassword", "changeme");
    ReflectionTestUtils.setField(esConfig, "userESClientHost", "localhost");
    ReflectionTestUtils.setField(esConfig, "userESClientPort", "9200");
  }

  @Test
  void elasticsearchClient_buildsSuccessfully() {
    ElasticsearchClient client = esConfig.elasticsearchClient();

    assertNotNull(client);
  }

  @Test
  void userESClient_buildsSuccessfully_forSingleHost() {
    RestHighLevelClient client = esConfig.userESClient();

    assertNotNull(client);
  }

  @Test
  void userESClient_buildsSuccessfully_forMultipleCommaSeparatedHosts() {
    ReflectionTestUtils.setField(esConfig, "userESClientHost", "es1.internal,es2.internal");
    ReflectionTestUtils.setField(esConfig, "userESClientPort", "9200,9201");

    RestHighLevelClient client = esConfig.userESClient();

    assertNotNull(client);
  }
}

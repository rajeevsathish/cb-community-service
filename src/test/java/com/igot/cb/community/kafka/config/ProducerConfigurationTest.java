package com.igot.cb.community.kafka.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

class ProducerConfigurationTest {

  private ProducerConfiguration producerConfiguration;

  @BeforeEach
  void setUp() {
    producerConfiguration = new ProducerConfiguration();
    ReflectionTestUtils.setField(producerConfiguration, "kafkabootstrapAddress", "localhost:9092");
  }

  @Test
  void producerFactory_isConfiguredWithBootstrapServersAndStringSerializers() {
    ProducerFactory<String, String> factory = producerConfiguration.producerFactory();

    assertNotNull(factory);
    assertEquals("localhost:9092",
        factory.getConfigurationProperties().get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
  }

  @Test
  void kafkaTemplate_wrapsProducerFactory() {
    KafkaTemplate<String, String> template = producerConfiguration.kafkaTemplate();

    assertNotNull(template);
    assertEquals("localhost:9092",
        template.getProducerFactory().getConfigurationProperties().get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
  }
}

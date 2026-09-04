package com.igot.cb.community.kafka.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.util.ReflectionTestUtils;

class ConsumerConfigurationTest {

  private ConsumerConfiguration consumerConfiguration;

  @BeforeEach
  void setUp() {
    consumerConfiguration = new ConsumerConfiguration();
    ReflectionTestUtils.setField(consumerConfiguration, "kafkabootstrapAddress", "localhost:9092");
    ReflectionTestUtils.setField(consumerConfiguration, "kafkaOffsetResetValue", "latest");
    ReflectionTestUtils.setField(consumerConfiguration, "kafkaMaxPollInterval", 15000);
    ReflectionTestUtils.setField(consumerConfiguration, "kafkaMaxPollRecords", 100);
    ReflectionTestUtils.setField(consumerConfiguration, "kafkaAutoCommitInterval", 10000);
  }

  @Test
  void consumerConfigs_populatesAllExpectedKafkaProperties() {
    Map<String, Object> configs = consumerConfiguration.consumerConfigs();

    assertEquals("localhost:9092", configs.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
    assertEquals(true, configs.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
    assertEquals("latest", configs.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
    assertEquals(15000, configs.get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG));
    assertEquals(100, configs.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
    assertEquals(10000, configs.get(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG));
  }

  @Test
  void consumerFactory_isBuiltFromConsumerConfigs() {
    ConsumerFactory<String, String> factory = consumerConfiguration.consumerFactory();

    assertNotNull(factory);
    assertEquals("localhost:9092",
        factory.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
  }

  @Test
  void kafkaListenerContainerFactory_isNotNull() {
    KafkaListenerContainerFactory<?> factory = consumerConfiguration.kafkaListenerContainerFactory();

    assertNotNull(factory);
    assertTrue(factory instanceof org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory);
  }
}

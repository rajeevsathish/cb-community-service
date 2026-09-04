package com.igot.cb.community.kafka.producer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProducerTest {

  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;

  private Producer producer;

  @BeforeEach
  void setUp() {
    producer = new Producer();
    ReflectionTestUtils.setField(producer, "kafkaTemplate", kafkaTemplate);
  }

  @Test
  void push_sendsSerializedJsonToTopic() {
    CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
    when(kafkaTemplate.send(eq("community.user.count"), eq("{\"communityId\":\"comm1\",\"count\":5}")))
        .thenReturn(future);

    producer.push("community.user.count", new LinkedHashMapPayload());

    verify(kafkaTemplate).send(eq("community.user.count"), eq("{\"communityId\":\"comm1\",\"count\":5}"));
  }

  @Test
  void push_propagatesException_whenKafkaTemplateFails() {
    when(kafkaTemplate.send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenThrow(new RuntimeException("broker unavailable"));

    assertThrows(RuntimeException.class, () -> producer.push("community.user.count", Map.of("id", "comm1")));
  }

  @Test
  void push_doesNotCallKafka_whenValueIsUnserializable() {
    producer.push("community.user.count", new SelfReferencing());

    verify(kafkaTemplate, never()).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  private static class LinkedHashMapPayload extends java.util.LinkedHashMap<String, Object> {
    LinkedHashMapPayload() {
      put("communityId", "comm1");
      put("count", 5);
    }
  }

  private static class SelfReferencing {
    @SuppressWarnings("unused")
    private final SelfReferencing self = this;
  }
}

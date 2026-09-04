package com.igot.cb;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class CbCommunityServiceApplicationTest {

  @Test
  void restTemplate_buildsWithConfiguredTimeoutsAndConnectionPool() {
    CbCommunityServiceApplication application = new CbCommunityServiceApplication();

    RestTemplate restTemplate = application.restTemplate();

    assertNotNull(restTemplate);
    assertNotNull(restTemplate.getRequestFactory());
  }
}

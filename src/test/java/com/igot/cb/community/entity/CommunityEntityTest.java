package com.igot.cb.community.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;

class CommunityEntityTest {

  @Test
  void allArgsConstructor_setsEveryField() {
    ObjectNode data = new ObjectMapper().createObjectNode().put("name", "Community One");
    Timestamp now = new Timestamp(System.currentTimeMillis());

    CommunityEntity entity = new CommunityEntity("comm1", data, now, now, "user1", true);

    assertEquals("comm1", entity.getCommunityId());
    assertEquals(data, entity.getData());
    assertEquals(now, entity.getCreatedOn());
    assertEquals(now, entity.getUpdatedOn());
    assertEquals("user1", entity.getCreated_by());
    assertTrue(entity.isActive());
  }

  @Test
  void settersUpdateFieldsIndependently() {
    CommunityEntity entity = new CommunityEntity();

    entity.setCommunityId("comm2");
    entity.setActive(false);

    assertEquals("comm2", entity.getCommunityId());
    assertTrue(!entity.isActive());
  }
}

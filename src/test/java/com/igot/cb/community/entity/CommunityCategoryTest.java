package com.igot.cb.community.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;

class CommunityCategoryTest {

  @Test
  void isActive_defaultsToTrue_forNoArgsConstructor() {
    CommunityCategory category = new CommunityCategory();

    assertTrue(category.getIsActive());
  }

  @Test
  void gettersAndSetters_roundTripAllFields() {
    CommunityCategory category = new CommunityCategory();
    Timestamp now = new Timestamp(System.currentTimeMillis());

    category.setCategoryId(1);
    category.setCategoryName("Technology");
    category.setDescription("Tech communities");
    category.setParentId(null);
    category.setIsActive(false);
    category.setCreatedAt(now);
    category.setLastUpdatedAt(now);
    category.setDepartmentId("dept1");
    category.setCountOfCommunities(5L);

    assertEquals(1, category.getCategoryId());
    assertEquals("Technology", category.getCategoryName());
    assertEquals("Tech communities", category.getDescription());
    assertEquals(false, category.getIsActive());
    assertEquals(now, category.getCreatedAt());
    assertEquals(now, category.getLastUpdatedAt());
    assertEquals("dept1", category.getDepartmentId());
    assertEquals(5L, category.getCountOfCommunities());
  }
}

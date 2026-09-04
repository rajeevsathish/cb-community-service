package com.igot.cb.pores.elasticsearch.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchCriteriaTest {

  @Test
  void gettersAndSetters_roundTripAllFields() {
    SearchCriteria criteria = new SearchCriteria();
    HashMap<String, Object> filterMap = new HashMap<>();
    filterMap.put("status", "ACTIVE");

    criteria.setFilterCriteriaMap(filterMap);
    criteria.setRequestedFields(List.of("communityId", "communityName"));
    criteria.setPageNumber(2);
    criteria.setPageSize(20);
    criteria.setOrderBy("createdOn");
    criteria.setOrderDirection("desc");
    criteria.setSearchString("test community");
    criteria.setFacets(List.of("category"));
    criteria.setQuery(Map.of("match_all", Map.of()));
    criteria.setOverrideCache(true);

    assertEquals("ACTIVE", criteria.getFilterCriteriaMap().get("status"));
    assertEquals(List.of("communityId", "communityName"), criteria.getRequestedFields());
    assertEquals(2, criteria.getPageNumber());
    assertEquals(20, criteria.getPageSize());
    assertEquals("createdOn", criteria.getOrderBy());
    assertEquals("desc", criteria.getOrderDirection());
    assertEquals("test community", criteria.getSearchString());
    assertEquals(List.of("category"), criteria.getFacets());
    assertTrue(criteria.getQuery().containsKey("match_all"));
    assertTrue(criteria.isOverrideCache());
  }

  @Test
  void allArgsConstructor_setsEveryField() {
    HashMap<String, Object> filterMap = new HashMap<>();
    SearchCriteria criteria = new SearchCriteria(filterMap, List.of("id"), 0, 10, "name", "asc",
        "query", List.of("facet1"), Map.of(), false);

    assertEquals(0, criteria.getPageNumber());
    assertEquals(10, criteria.getPageSize());
    assertEquals("query", criteria.getSearchString());
  }
}

package com.igot.cb.pores.elasticsearch.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchResultTest {

  @Test
  void allArgsConstructor_setsEveryField() {
    ObjectNode data = new ObjectMapper().createObjectNode().put("id", "comm1");
    Map<String, List<FacetDTO>> facets = Map.of("category", List.of(new FacetDTO("Tech", 3L)));
    List<Map<String, Object>> additionalInfo = List.of(Map.of("orgId", "org1"));

    SearchResult result = new SearchResult(data, facets, 42L, additionalInfo);

    assertEquals(data, result.getData());
    assertEquals(facets, result.getFacets());
    assertEquals(42L, result.getTotalCount());
    assertEquals(additionalInfo, result.getAdditionalInfo());
  }

  @Test
  void noArgsConstructor_thenSetters_roundTrip() {
    SearchResult result = new SearchResult();

    result.setTotalCount(7L);

    assertEquals(7L, result.getTotalCount());
  }
}

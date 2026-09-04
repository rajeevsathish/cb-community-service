package com.igot.cb.pores.elasticsearch.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FacetDTOTest {

  @Test
  void allArgsConstructor_setsBothFields() {
    FacetDTO facet = new FacetDTO("Bangalore", 12L);

    assertEquals("Bangalore", facet.getValue());
    assertEquals(12L, facet.getCount());
  }

  @Test
  void settersUpdateFieldsIndependently() {
    FacetDTO facet = new FacetDTO();

    facet.setValue("Delhi");
    facet.setCount(5L);

    assertEquals("Delhi", facet.getValue());
    assertEquals(5L, facet.getCount());
  }
}

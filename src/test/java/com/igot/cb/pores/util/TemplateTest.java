package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateTest {

  @Test
  void constructor_setsAllFields() {
    Map<String, Object> params = Map.of("communityName", "My Community");

    Template template = new Template("<html>...</html>", "moderator-template", params);

    assertEquals("<html>...</html>", template.getData());
    assertEquals("moderator-template", template.getId());
    assertEquals(params, template.getParams());
  }

  @Test
  void setters_updateFieldsIndependently() {
    Template template = new Template(null, null, null);

    template.setData("new-data");
    template.setId("new-id");
    template.setParams(Map.of("k", "v"));

    assertEquals("new-data", template.getData());
    assertEquals("new-id", template.getId());
    assertEquals("v", template.getParams().get("k"));
  }

  @Test
  void toString_includesAllFieldValues() {
    Template template = new Template("body", "tpl-1", Map.of("k", "v"));

    String result = template.toString();

    assertTrue(result.contains("data='body'"));
    assertTrue(result.contains("id='tpl-1'"));
    assertTrue(result.contains("k=v"));
  }

  @Test
  void nullFields_areTolerated() {
    Template template = new Template(null, null, null);

    assertNull(template.getData());
    assertNull(template.getId());
    assertNull(template.getParams());
  }
}

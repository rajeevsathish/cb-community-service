package com.igot.cb.transactional.cassandrautils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class CassandraUtilTest {

  @Test
  void getPreparedStatement_buildsParameterizedInsertQuery() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", "comm1");
    record.put("name", "Community One");

    String query = CassandraUtil.getPreparedStatement("sunbird", "community", record);

    assertEquals("INSERT INTO sunbird.community(id,name) VALUES (?,?);", query);
  }

  @Test
  void getPreparedStatement_handlesSingleColumn() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", "comm1");

    String query = CassandraUtil.getPreparedStatement("sunbird", "community", record);

    assertEquals("INSERT INTO sunbird.community(id) VALUES (?);", query);
  }

  private ColumnDefinition columnDefinition(String name) {
    ColumnDefinition definition = mock(ColumnDefinition.class);
    when(definition.getName()).thenReturn(CqlIdentifier.fromInternal(name));
    return definition;
  }

  // CALLS_REAL_METHODS so the inherited Iterable#forEach default method actually
  // delegates to the stubbed iterator() below instead of being a Mockito no-op.
  private ColumnDefinitions columnDefinitions(List<String> names) {
    ColumnDefinitions definitions = mock(ColumnDefinitions.class, Answers.CALLS_REAL_METHODS);
    List<ColumnDefinition> defs = names.stream().map(this::columnDefinition).toList();
    when(definitions.iterator()).thenReturn(defs.iterator());
    return definitions;
  }

  private Row rowWithValues(Map<String, Object> values) {
    Row row = mock(Row.class);
    values.forEach((key, value) -> when(row.getObject(key)).thenReturn(value));
    return row;
  }

  @Test
  void createResponse_mapsRowsUsingColumnDefinitions() {
    ResultSet resultSet = mock(ResultSet.class);
    ColumnDefinitions definitions = columnDefinitions(List.of("id", "name"));
    when(resultSet.getColumnDefinitions()).thenReturn(definitions);

    Row row1 = rowWithValues(Map.of("id", "comm1", "name", "Community One"));
    Row row2 = rowWithValues(Map.of("id", "comm2", "name", "Community Two"));
    Iterator<Row> rowIterator = List.of(row1, row2).iterator();
    when(resultSet.iterator()).thenReturn(rowIterator);

    List<Map<String, Object>> response = CassandraUtil.createResponse(resultSet);

    assertEquals(2, response.size());
    assertEquals("comm1", response.get(0).get("id"));
    assertEquals("Community Two", response.get(1).get("name"));
  }

  @Test
  void createResponse_returnsEmptyList_whenNoRows() {
    ResultSet resultSet = mock(ResultSet.class);
    ColumnDefinitions definitions = columnDefinitions(List.of("id"));
    when(resultSet.getColumnDefinitions()).thenReturn(definitions);
    when(resultSet.iterator()).thenReturn(List.<Row>of().iterator());

    List<Map<String, Object>> response = CassandraUtil.createResponse(resultSet);

    assertTrue(response.isEmpty());
  }

  @Test
  void createResponseWithKey_indexesRowsByGivenColumn() {
    ResultSet resultSet = mock(ResultSet.class);
    ColumnDefinitions definitions = columnDefinitions(List.of("id", "name"));
    when(resultSet.getColumnDefinitions()).thenReturn(definitions);
    Row row1 = rowWithValues(Map.of("id", "comm1", "name", "Community One"));
    when(resultSet.iterator()).thenReturn(List.of(row1).iterator());

    Map<String, Object> response = CassandraUtil.createResponse(resultSet, "id");

    assertTrue(response.containsKey("comm1"));
    Map<?, ?> rowMap = (Map<?, ?>) response.get("comm1");
    assertEquals("Community One", rowMap.get("name"));
  }

  @Test
  void fetchColumnsMapping_mapsInternalColumnNames() {
    ResultSet resultSet = mock(ResultSet.class);
    ColumnDefinitions definitions = columnDefinitions(List.of("community_id"));
    when(resultSet.getColumnDefinitions()).thenReturn(definitions);

    Map<String, String> mapping = CassandraUtil.fetchColumnsMapping(resultSet);

    assertEquals("community_id", mapping.get("community_id"));
  }
}

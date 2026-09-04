package com.igot.cb.transactional.cassandrautils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CassandraOperationImplTest {

  @Mock
  private CassandraConnectionManager connectionManager;

  @Mock
  private CqlSession session;

  private CassandraOperationImpl cassandraOperation;

  @BeforeEach
  void setUp() {
    cassandraOperation = new CassandraOperationImpl();
    ReflectionTestUtils.setField(cassandraOperation, "connectionManager", connectionManager);
  }

  private ResultSet emptyResultSet() {
    ResultSet resultSet = mock(ResultSet.class);
    ColumnDefinitions definitions = mock(ColumnDefinitions.class, Answers.CALLS_REAL_METHODS);
    when(definitions.iterator()).thenReturn(List.<ColumnDefinition>of().iterator());
    when(resultSet.getColumnDefinitions()).thenReturn(definitions);
    when(resultSet.iterator()).thenReturn(List.<Row>of().iterator());
    return resultSet;
  }

  @Test
  void insertRecord_returnsSuccessResponse_whenExecutionSucceeds() {
    when(connectionManager.getSession("sunbird")).thenReturn(session);
    PreparedStatement preparedStatement = mock(PreparedStatement.class);
    BoundStatement boundStatement = mock(BoundStatement.class);
    when(session.prepare(anyString())).thenReturn(preparedStatement);
    when(preparedStatement.bind(any(Object[].class))).thenReturn(boundStatement);
    when(session.execute(any(Statement.class))).thenReturn(mock(ResultSet.class));

    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", "comm1");

    Object result = cassandraOperation.insertRecord("sunbird", "community", record);

    ApiResponse response = (ApiResponse) result;
    assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
  }

  @Test
  void insertRecord_returnsFailedResponse_whenSessionThrows() {
    when(connectionManager.getSession("sunbird")).thenReturn(session);
    when(session.prepare(anyString())).thenThrow(new RuntimeException("connection reset"));

    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", "comm1");

    Object result = cassandraOperation.insertRecord("sunbird", "community", record);

    ApiResponse response = (ApiResponse) result;
    assertEquals(Constants.FAILED, response.get(Constants.RESPONSE));
    assertTrue(((String) response.get(Constants.ERROR_MESSAGE)).contains("connection reset"));
  }

  @Test
  void getRecordsByPropertiesWithoutFiltering_returnsMappedRows() {
    when(connectionManager.getSession("sunbird")).thenReturn(session);
    ResultSet resultSet = mock(ResultSet.class);
    ColumnDefinition idColumn = mock(ColumnDefinition.class);
    when(idColumn.getName()).thenReturn(CqlIdentifier.fromInternal("id"));
    ColumnDefinitions definitions = mock(ColumnDefinitions.class, Answers.CALLS_REAL_METHODS);
    when(definitions.iterator()).thenReturn(List.of(idColumn).iterator());
    when(resultSet.getColumnDefinitions()).thenReturn(definitions);
    Row row = mock(Row.class);
    when(row.getObject("id")).thenReturn("comm1");
    when(resultSet.iterator()).thenReturn(List.of(row).iterator());
    when(session.execute(any(Statement.class))).thenReturn(resultSet);

    Map<String, Object> propertyMap = new LinkedHashMap<>();
    propertyMap.put("status", "ACTIVE");
    propertyMap.put("id", List.of("comm1", "comm2"));

    List<Map<String, Object>> records = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        "sunbird", "community", propertyMap, List.of("id"), 10);

    assertEquals(1, records.size());
    assertEquals("comm1", records.get(0).get("id"));
  }

  @Test
  void getRecordsByPropertiesWithoutFiltering_returnsEmptyList_whenSessionThrows() {
    when(connectionManager.getSession("sunbird")).thenThrow(new RuntimeException("host unreachable"));

    List<Map<String, Object>> records = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        "sunbird", "community", Map.of(), null, null);

    assertTrue(records.isEmpty());
  }

  @Test
  void getRecordsByPropertiesWithoutFiltering_returnsEmptyList_whenNoPropertiesOrFields() {
    when(connectionManager.getSession("sunbird")).thenReturn(session);
    ResultSet resultSet = emptyResultSet();
    when(session.execute(any(Statement.class))).thenReturn(resultSet);

    List<Map<String, Object>> records = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        "sunbird", "community", Map.of(), null, null);

    assertTrue(records.isEmpty());
  }

  @Test
  void updateRecord_returnsSuccessResponse_whenExecutionSucceeds() {
    when(connectionManager.getSession("sunbird")).thenReturn(session);
    when(session.execute(any(Statement.class))).thenReturn(mock(ResultSet.class));

    Map<String, Object> updateAttributes = Map.of("status", "INACTIVE");
    Map<String, Object> compositeKey = Map.of("id", "comm1");

    Map<String, Object> result = cassandraOperation.updateRecord("sunbird", "community", updateAttributes, compositeKey);

    assertEquals(Constants.SUCCESS, result.get(Constants.RESPONSE));
  }

  @Test
  void updateRecord_rethrowsException_whenSessionFails() {
    when(connectionManager.getSession("sunbird")).thenReturn(session);
    when(session.execute(any(Statement.class))).thenThrow(new RuntimeException("write timeout"));

    Map<String, Object> updateAttributes = Map.of("status", "INACTIVE");
    Map<String, Object> compositeKey = Map.of("id", "comm1");

    assertThrows(RuntimeException.class, () ->
        cassandraOperation.updateRecord("sunbird", "community", updateAttributes, compositeKey));
  }

  @Test
  void insertRecord_doesNotThrow_forEmptyRecord() {
    when(connectionManager.getSession("sunbird")).thenReturn(session);
    when(session.prepare(anyString())).thenThrow(new RuntimeException("empty statement"));

    assertDoesNotThrow(() -> cassandraOperation.insertRecord("sunbird", "community", new LinkedHashMap<>()));
  }
}

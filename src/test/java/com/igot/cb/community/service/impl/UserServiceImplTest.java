package com.igot.cb.community.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

  @Mock
  private CassandraOperation cassandraOperation;

  @InjectMocks
  private UserServiceImpl userService;

  @BeforeEach
  void setUp() {
    userService = new UserServiceImpl();
    userService.cassandraOperation = cassandraOperation;
    ReflectionTestUtils.setField(userService, "objectMapper", new ObjectMapper());
  }

  private Map<String, Object> cassandraRow(String id, String firstName, String channel, String profileDetails) {
    Map<String, Object> row = new HashMap<>();
    row.put(Constants.ID, id);
    row.put(Constants.FIRST_NAME, firstName);
    row.put(Constants.CHANNEL, channel);
    row.put(Constants.PROFILE_DETAILS, profileDetails);
    return row;
  }

  @Test
  void fetchUserFromprimary_returnsBasicUserFields_whenNoProfileDetails() {
    List<Map<String, Object>> rows = List.of(cassandraRow("u1", "John", "org1", null));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), eq(null)))
        .thenReturn(rows);

    List<Object> result = userService.fetchUserFromprimary(Arrays.asList("u1"));

    assertEquals(1, result.size());
    Map<?, ?> userMap = (Map<?, ?>) result.get(0);
    assertEquals("u1", userMap.get(Constants.USER_ID_KEY));
    assertEquals("John", userMap.get(Constants.FIRST_NAME_KEY));
    assertEquals("org1", userMap.get(Constants.DEPARTMENT));
  }

  @Test
  void fetchUserFromprimary_extractsProfileImageDesignationAndStatus_whenProfileDetailsPresent() {
    String profileDetails = "{"
        + "\"profileImageUrl\":\"http://img.png\","
        + "\"professionalDetails\":[{\"designation\":\"Engineer\"}],"
        + "\"profileStatus\":\"VERIFIED\""
        + "}";
    List<Map<String, Object>> rows = List.of(cassandraRow("u2", "Jane", "org2", profileDetails));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), eq(null)))
        .thenReturn(rows);

    List<Object> result = userService.fetchUserFromprimary(Arrays.asList("u2"));

    Map<?, ?> userMap = (Map<?, ?>) result.get(0);
    assertEquals("http://img.png", userMap.get(Constants.PROFILE_IMG_KEY));
    assertEquals("Engineer", userMap.get(Constants.DESIGNATION_KEY));
    assertEquals("VERIFIED", userMap.get(Constants.PROFILE_STATUS));
  }

  @Test
  void fetchUserFromprimary_returnsEmptyList_whenNoUsersFound() {
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), eq(null)))
        .thenReturn(new ArrayList<>());

    List<Object> result = userService.fetchUserFromprimary(Arrays.asList("missing"));

    assertEquals(0, result.size());
  }

  @Test
  void fetchUserFromprimary_throwsCustomException_whenProfileDetailsIsMalformedJson() {
    List<Map<String, Object>> rows = List.of(cassandraRow("u3", "Bad", "org3", "{not-json"));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), eq(null)))
        .thenReturn(rows);

    assertThrows(CustomException.class,
        () -> userService.fetchUserFromprimary(Arrays.asList("u3")));
  }
}

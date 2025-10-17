package com.igot.cb.community.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;

import java.io.IOException;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void testFetchUserFromprimary_withValidProfileDetails() throws Exception {
        List<String> userIds = List.of("user-1");
        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.ID, "user-1");
        dbRecord.put(Constants.FIRST_NAME, "John");
        dbRecord.put(Constants.CHANNEL, "channel-1");
        dbRecord.put(Constants.PROFILE_DETAILS, "{\"profileImg\":\"img.png\",\"professionalDetails\":[{\"designation\":\"Engineer\"}],\"profileStatus\":\"active\"}");

        List<Map<String, Object>> dbResult = List.of(dbRecord);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(dbResult);

        Map<String, Object> profileMap = new HashMap<>();
        profileMap.put(Constants.PROFILE_IMG, "img.png");
        profileMap.put(Constants.PROFESSIONAL_DETAILS, List.of(Map.of(Constants.DESIGNATION, "Engineer")));
        profileMap.put(Constants.PROFILE_STATUS_KEY, "active");

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(profileMap);

        List<Object> result = userService.fetchUserFromprimary(userIds);

        assertNotNull(result);
        assertEquals(1, result.size());
        Map<String, Object> userMap = (Map<String, Object>) result.get(0);
        assertEquals("user-1", userMap.get(Constants.USER_ID_KEY));
        assertEquals("John", userMap.get(Constants.FIRST_NAME_KEY));
        assertEquals("img.png", userMap.get(Constants.PROFILE_IMG_KEY));
        assertEquals("Engineer", userMap.get(Constants.DESIGNATION_KEY));
        assertEquals("active", userMap.get(Constants.PROFILE_STATUS));
    }

    @Test
    void testFetchUserFromprimary_withBlankProfileDetails() {
        List<String> userIds = List.of("user-2");
        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.ID, "user-2");
        dbRecord.put(Constants.FIRST_NAME, "Alice");
        dbRecord.put(Constants.CHANNEL, "channel-2");
        dbRecord.put(Constants.PROFILE_DETAILS, ""); // blank profile

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(List.of(dbRecord));

        List<Object> result = userService.fetchUserFromprimary(userIds);

        assertNotNull(result);
        assertEquals(1, result.size());
        Map<String, Object> userMap = (Map<String, Object>) result.get(0);
        assertEquals("user-2", userMap.get(Constants.USER_ID_KEY));
        assertEquals("Alice", userMap.get(Constants.FIRST_NAME_KEY));
    }

}

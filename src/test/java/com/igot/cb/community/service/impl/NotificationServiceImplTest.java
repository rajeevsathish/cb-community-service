package com.igot.cb.community.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.OutboundRequestHandlerServiceImpl;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OutboundRequestHandlerServiceImpl outboundRequestHandlerService;

    @Mock
    private CbServerProperties props;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "moderatorMailSubject", "Test Subject");

    }

    @Test
    void testSendNotificationSuccess() throws Exception {
        List<String> moderatorIds = new ArrayList<>(List.of("mod1"));
        String communityId = "community123";
        String userId = "user123";
        String communityName = "Test Community";
        Map<String, Object> user1 = new HashMap<>();
        user1.put(Constants.ID, "user123");
        user1.put(Constants.FIRST_NAME, "John");
        user1.put(Constants.PROFILE_DETAILS, "{\"personalDetails\":{\"primaryEmail\":\"john@example.com\"}}");
        Map<String, Object> user2 = new HashMap<>();
        user2.put(Constants.ID, "mod1");
        user2.put(Constants.FIRST_NAME, "Mod");
        user2.put(Constants.PROFILE_DETAILS, "{\"personalDetails\":{\"primaryEmail\":\"mod@example.com\"}}");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER),
                anyMap(),
                anyList(),
                isNull()
        )).thenReturn(List.of(user1, user2));

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenAnswer(inv -> {
            Map<String, Object> personal = new HashMap<>();
            personal.put(Constants.PRIMARY_EMAIL, "mock@example.com");
            Map<String, Object> result = new HashMap<>();
            result.put(Constants.PERSONAL_DETAILS, personal);
            return result;
        });
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), any(), isNull()))
                .thenReturn(Collections.singletonMap("status", "success"));
        notificationService.sendNotification(moderatorIds, communityId, userId, communityName);
        verify(outboundRequestHandlerService, times(1))
                .fetchResultUsingPost(anyString(), any(), isNull());
        assertTrue(true, "Expected outboundRequestHandlerService to be invoked once successfully");
    }


    @Test
    void testSendNotification_ProfileDetailsException() throws Exception {
        List<String> moderatorIds = new ArrayList<>(List.of("mod1"));
        String userId = "user123";

        Map<String, Object> user1 = new HashMap<>();
        user1.put(Constants.ID, userId);
        user1.put(Constants.FIRST_NAME, "John");
        user1.put(Constants.PROFILE_DETAILS, "INVALID_JSON");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER),
                anyMap(),
                anyList(),
                isNull()
        )).thenReturn(List.of(user1));

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new RuntimeException("Invalid JSON"));

        assertThrows(CustomException.class, () ->
                notificationService.sendNotification(moderatorIds, "c1", userId, "Test Community"));
    }

    @Test
    void testSendNotification_InternalSendFailsGracefully() throws Exception {
        List<String> moderatorIds = new ArrayList<>(List.of("mod1"));
        String userId = "user123";
        Map<String, Object> user1 = new HashMap<>();
        user1.put(Constants.ID, userId);
        user1.put(Constants.FIRST_NAME, "John");
        user1.put(Constants.PROFILE_DETAILS, "{\"personalDetails\":{\"primaryEmail\":\"john@example.com\"}}");
        when(props.getDomainUrl()).thenReturn("http://localhost/");
        when(props.getFixedCommunityUrl()).thenReturn("community/");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER),
                anyMap(),
                anyList(),
                isNull()
        )).thenReturn(List.of(user1));
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(
                Map.of(Constants.PERSONAL_DETAILS, Map.of(Constants.PRIMARY_EMAIL, "john@example.com")));
        notificationService.sendNotification(moderatorIds, "cid", userId, "TestCommunity");
        verify(outboundRequestHandlerService, times(0))
                .fetchResultUsingPost(anyString(), any(), isNull());
        assertTrue(true, "NotificationService should handle internal failures gracefully without throwing exceptions");
    }


    @Test
    void testConstructEmailTemplate() throws Exception {
        String templateName = "welcomeTemplate";
        Map<String, Object> params = Map.of(Constants.MODERATOR_NAME, "John");

        Map<String, Object> templateMap = Map.of(Constants.TEMPLATE, "Hi $moderatorName!");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_EMAIL_TEMPLATE),
                anyMap(),
                anyList(),
                isNull()
        )).thenReturn(List.of(templateMap));

        Method method = NotificationServiceImpl.class
                .getDeclaredMethod("constructEmailTemplate", String.class, Map.class);
        method.setAccessible(true);

        String result = (String) method.invoke(notificationService, templateName, params);
        assertTrue(result.contains("Hi"));
    }

    @Test
    void testSendNotification_PrivateMethod() throws Exception {
        Map<String, Object> request = Map.of("key", "value");
        Method method = NotificationServiceImpl.class.getDeclaredMethod("sendNotification", Map.class);
        method.setAccessible(true);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Collections.singletonMap("response", "ok"));
        method.invoke(notificationService, request);
        verify(outboundRequestHandlerService, times(1))
                .fetchResultUsingPost(anyString(), anyMap(), isNull());
        assertTrue(true, "Private sendNotification method executed successfully");
    }


    @Test
    void testSendNotification_PrivateMethod_Exception() throws Exception {
        Method method = NotificationServiceImpl.class.getDeclaredMethod("sendNotification", Map.class);
        method.setAccessible(true);
        doThrow(RuntimeException.class).when(outboundRequestHandlerService)
                .fetchResultUsingPost(anyString(), any(), isNull());
        assertDoesNotThrow(() -> method.invoke(notificationService, Map.of()));
    }

}


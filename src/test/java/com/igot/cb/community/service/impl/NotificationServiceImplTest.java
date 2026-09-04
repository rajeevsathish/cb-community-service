package com.igot.cb.community.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.OutboundRequestHandlerServiceImpl;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

  @Mock
  private CassandraOperation cassandraOperation;

  @Mock
  private OutboundRequestHandlerServiceImpl outboundRequestHandlerService;

  private NotificationServiceImpl notificationService;

  @BeforeEach
  void setUp() {
    notificationService = new NotificationServiceImpl();
    notificationService.cassandraOperation = cassandraOperation;
    notificationService.objectMapper = new ObjectMapper();
    notificationService.outboundRequestHandlerService = outboundRequestHandlerService;
    CbServerProperties props = new CbServerProperties();
    props.setDomainUrl("https://example.org");
    props.setFixedCommunityUrl("/community/");
    props.setCommunityModeratorTemplate("moderator-template");
    props.setSupportEmail("support@example.org");
    props.setNotifyServiceHost("https://notify.example.org");
    props.setNotifyServicePathAsync("/v1/notify");
    notificationService.props = props;
    ReflectionTestUtils.setField(notificationService, "moderatorMailSubject", "New moderator request");
  }

  private Map<String, Object> userRow(String id, String firstName, String email) {
    Map<String, Object> row = new HashMap<>();
    row.put(Constants.ID, id);
    row.put(Constants.FIRST_NAME, firstName);
    row.put(Constants.PROFILE_DETAILS,
        "{\"personalDetails\":{\"primaryEmail\":\"" + email + "\"}}");
    return row;
  }

  @Test
  void sendNotification_sendsEmailToEachModeratorWithEmail() {
    List<Map<String, Object>> userRows = List.of(
        userRow("mod1", "Alice", "alice@example.org"),
        userRow("owner1", "Owner", "owner@example.org"));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), any()))
        .thenReturn(userRows);

    List<Map<String, Object>> templateRows = new ArrayList<>();
    Map<String, Object> templateRow = new HashMap<>();
    templateRow.put(Constants.TEMPLATE, "Hello moderator");
    templateRows.add(templateRow);
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_EMAIL_TEMPLATE), anyMap(), anyList(), any()))
        .thenReturn(templateRows);

    when(outboundRequestHandlerService.fetchResultUsingPost(any(), any(), any()))
        .thenReturn(new HashMap<>());

    List<String> moderatorIds = new ArrayList<>(List.of("mod1"));
    assertDoesNotThrow(() ->
        notificationService.sendNotification(moderatorIds, "community1", "owner1", "My Community"));

    verify(outboundRequestHandlerService, times(1))
        .fetchResultUsingPost(any(), any(), any());
  }

  @Test
  void sendNotification_skipsRecipientsWithoutEmail() {
    Map<String, Object> modWithoutEmail = new HashMap<>();
    modWithoutEmail.put(Constants.ID, "mod2");
    modWithoutEmail.put(Constants.FIRST_NAME, "Bob");
    modWithoutEmail.put(Constants.PROFILE_DETAILS, "{}");

    List<Map<String, Object>> userRows = List.of(
        modWithoutEmail,
        userRow("owner1", "Owner", "owner@example.org"));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), any()))
        .thenReturn(userRows);

    List<String> moderatorIds = new ArrayList<>(List.of("mod2"));
    notificationService.sendNotification(moderatorIds, "community1", "owner1", "My Community");

    verify(outboundRequestHandlerService, never())
        .fetchResultUsingPost(any(), any(), any());
  }
}

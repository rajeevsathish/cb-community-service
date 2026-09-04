package com.igot.cb.community.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.community.service.CommunityManagementService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class CommunityControllerTest {

  private static final String AUTH_TOKEN = "token-123";

  @Mock
  private CommunityManagementService communityManagementService;

  @InjectMocks
  private CommunityController communityController;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ApiResponse apiResponse(HttpStatus status) {
    ApiResponse response = new ApiResponse();
    response.setResponseCode(status);
    return response;
  }

  @Test
  void create_delegatesToServiceAndReturnsServiceStatus() {
    JsonNode payload = objectMapper.createObjectNode().put("name", "community-1");
    ApiResponse response = apiResponse(HttpStatus.CREATED);
    when(communityManagementService.create(payload, AUTH_TOKEN)).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.create(payload, AUTH_TOKEN);

    assertEquals(HttpStatus.CREATED, result.getStatusCode());
    assertEquals(response, result.getBody());
    verify(communityManagementService).create(payload, AUTH_TOKEN);
  }

  @Test
  void read_delegatesToServiceAndReturnsOk() {
    ApiResponse response = apiResponse(HttpStatus.OK);
    when(communityManagementService.read("comm1", AUTH_TOKEN)).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.read("comm1", AUTH_TOKEN);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals(response, result.getBody());
  }

  @Test
  void delete_delegatesToServiceAndReturnsOk() {
    ApiResponse response = apiResponse(HttpStatus.OK);
    when(communityManagementService.delete("comm1", AUTH_TOKEN)).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.delete("comm1", AUTH_TOKEN);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    verify(communityManagementService).delete("comm1", AUTH_TOKEN);
  }

  @Test
  void update_returnsServiceProvidedStatusCode() {
    JsonNode payload = objectMapper.createObjectNode().put("id", "comm1");
    ApiResponse response = apiResponse(HttpStatus.BAD_REQUEST);
    when(communityManagementService.update(payload, AUTH_TOKEN)).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.update(payload, AUTH_TOKEN);

    assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
  }

  @Test
  void join_delegatesToServiceAndReturnsServiceStatus() {
    Map<String, Object> request = Map.of("communityId", "comm1");
    ApiResponse response = apiResponse(HttpStatus.OK);
    when(communityManagementService.joinCommunity(request, AUTH_TOKEN)).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.join(request, AUTH_TOKEN);

    assertEquals(HttpStatus.OK, result.getStatusCode());
  }

  @Test
  void unJoin_delegatesToServiceAndReturnsServiceStatus() {
    Map<String, Object> request = Map.of("communityId", "comm1");
    ApiResponse response = apiResponse(HttpStatus.OK);
    when(communityManagementService.unJoinCommunity(request, AUTH_TOKEN)).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.unJoin(request, AUTH_TOKEN);

    assertEquals(HttpStatus.OK, result.getStatusCode());
  }

  @Test
  void readJoin_delegatesToCommunitiesJoinedByUser() {
    ApiResponse response = apiResponse(HttpStatus.OK);
    when(communityManagementService.communitiesJoinedByUser(AUTH_TOKEN)).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.readJoin(AUTH_TOKEN);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals(response, result.getBody());
  }

  @Test
  void search_delegatesToServiceAndReturnsServiceStatus() {
    SearchCriteria criteria = new SearchCriteria();
    ApiResponse response = apiResponse(HttpStatus.OK);
    when(communityManagementService.searchCommunity(criteria)).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.search(criteria);

    assertEquals(HttpStatus.OK, result.getStatusCode());
  }

  @Test
  void report_delegatesToServiceWithTokenAndPayload() {
    Map<String, Object> reportData = Map.of("reason", "spam");
    ApiResponse response = apiResponse(HttpStatus.OK);
    when(communityManagementService.report(AUTH_TOKEN, reportData)).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.report(reportData, AUTH_TOKEN);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    verify(communityManagementService).report(AUTH_TOKEN, reportData);
  }

  @Test
  void uploadFile_delegatesToServiceAndReturnsUploadResponseStatus() {
    MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", "a,b,c".getBytes());
    ApiResponse response = apiResponse(HttpStatus.OK);
    when(communityManagementService.uploadFile(file, "comm1")).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.uploadFile(file, "comm1");

    assertEquals(HttpStatus.OK, result.getStatusCode());
  }

  @Test
  void adminJoin_delegatesToServiceAndReturnsServiceStatus() {
    Map<String, Object> request = Map.of("userIds", "u1,u2");
    ApiResponse response = apiResponse(HttpStatus.OK);
    when(communityManagementService.adminJoinCommunity(request, AUTH_TOKEN)).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.adminJoin(request, AUTH_TOKEN);

    assertEquals(HttpStatus.OK, result.getStatusCode());
  }

  @Test
  void adminUnjoin_delegatesToServiceAndReturnsServiceStatus() {
    Map<String, Object> request = Map.of("userIds", "u1,u2");
    ApiResponse response = apiResponse(HttpStatus.OK);
    when(communityManagementService.adminUnjoinCommunity(request, AUTH_TOKEN)).thenReturn(response);

    ResponseEntity<ApiResponse> result = communityController.adminUnjoin(request, AUTH_TOKEN);

    assertEquals(HttpStatus.OK, result.getStatusCode());
  }
}

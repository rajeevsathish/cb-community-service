package com.igot.cb.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiRespParam;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.community.service.CommunityManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommunityControllerTest {
    @InjectMocks
    private CommunityController communityController;
    @Mock
    private CommunityManagementService communityManagementService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testCreateCommunitySuccess() {
        ObjectNode communityDetails = JsonNodeFactory.instance.objectNode();
        communityDetails.put("name", "Test Community");
        communityDetails.put("description", "Test Description");

        String authToken = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.CREATED);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("Community created successfully");
        mockResponse.setParams(params);
        when(communityManagementService.create(any(JsonNode.class), any(String.class))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.create(communityDetails, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        verify(communityManagementService, times(1)).create(communityDetails, authToken);
    }

    @Test
    void testReadCommunitySuccess() {
        String communityId = "test-community-123";
        String authToken = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        Map<String, Object> communityData = new HashMap<>();
        communityData.put("id", communityId);
        communityData.put("name", "Test Community");
        communityData.put("description", "Test Description");
        mockResponse.put("community", communityData);
        when(communityManagementService.read(communityId, authToken)).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.read(communityId, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        verify(communityManagementService, times(1)).read(communityId, authToken);
    }

    @Test
    void testDeleteCommunitySuccess() {
        String communityId = "test-community-123";
        String authToken = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("Community deleted successfully");
        mockResponse.setParams(params);
        when(communityManagementService.delete(communityId, authToken)).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.delete(communityId, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        verify(communityManagementService, times(1)).delete(communityId, authToken);
    }

    @Test
    void testUpdateCommunitySuccess() {
        ObjectNode communityDetails = JsonNodeFactory.instance.objectNode();
        communityDetails.put("id", "test-community-123");
        communityDetails.put("name", "Updated Community Name");
        communityDetails.put("description", "Updated Description");
        String authToken = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("Community updated successfully");
        mockResponse.setParams(params);
        when(communityManagementService.update(any(JsonNode.class), eq(authToken))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.update(communityDetails, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        verify(communityManagementService, times(1)).update(communityDetails, authToken);
    }

    @Test
    void testJoinCommunitySuccess() {
        Map<String, Object> request = new HashMap<>();
        request.put("communityId", "test-community-123");
        request.put("userId", "test-user-123");
        String authToken = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("Successfully joined the community");
        mockResponse.setParams(params);
        when(communityManagementService.joinCommunity(any(Map.class), eq(authToken))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.join(request, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        verify(communityManagementService, times(1)).joinCommunity(request, authToken);
    }

    @Test
    void testReadJoinSuccess() {
        String authToken = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        List<Map<String, Object>> communities = new ArrayList<>();
        Map<String, Object> community1 = new HashMap<>();
        community1.put("id", "community-1");
        community1.put("name", "Test Community 1");
        community1.put("description", "Description 1");
        Map<String, Object> community2 = new HashMap<>();
        community2.put("id", "community-2");
        community2.put("name", "Test Community 2");
        community2.put("description", "Description 2");
        communities.add(community1);
        communities.add(community2);
        mockResponse.put("communities", communities);
        when(communityManagementService.communitiesJoinedByUser(eq(authToken))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.readJoin(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testListOfUsersJoinedSuccess() {
        String authToken = "test-auth-token";
        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("communityId", "test-community-123");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        List<Map<String, Object>> users = new ArrayList<>();
        Map<String, Object> user1 = new HashMap<>();
        user1.put("userId", "user-1");
        user1.put("name", "Test User 1");
        user1.put("email", "user1@test.com");
        Map<String, Object> user2 = new HashMap<>();
        user2.put("userId", "user-2");
        user2.put("name", "Test User 2");
        user2.put("email", "user2@test.com");
        users.add(user1);
        users.add(user2);
        mockResponse.put("users", users);
        when(communityManagementService.listOfUsersJoined(eq(authToken), any(Map.class))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.listOfUsersJoined(authToken, requestPayload);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testUnJoinCommunitySuccess() {
        Map<String, Object> request = new HashMap<>();
        request.put("communityId", "test-community-123");
        request.put("userId", "test-user-123");
        String authToken = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("Successfully unjoined from the community");
        mockResponse.setParams(params);
        when(communityManagementService.unJoinCommunity(any(Map.class), eq(authToken))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.unJoin(request, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        verify(communityManagementService, times(1)).unJoinCommunity(request, authToken);
    }

    @Test
    void testSearchCommunitySuccess() {
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setSearchString("test community");
        searchCriteria.setOrderBy("name");
        searchCriteria.setOrderDirection("asc");
        HashMap<String, Object> filterMap = new HashMap<>();
        filterMap.put("status", "ACTIVE");
        searchCriteria.setFilterCriteriaMap(filterMap);
        List<String> requestedFields = new ArrayList<>();
        requestedFields.add("name");
        requestedFields.add("description");
        searchCriteria.setRequestedFields(requestedFields);
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        List<Map<String, Object>> communities = new ArrayList<>();
        Map<String, Object> community1 = new HashMap<>();
        community1.put("id", "community-1");
        community1.put("name", "Test Community 1");
        community1.put("description", "Description 1");
        Map<String, Object> community2 = new HashMap<>();
        community2.put("id", "community-2");
        community2.put("name", "Test Community 2");
        community2.put("description", "Description 2");
        communities.add(community1);
        communities.add(community2);
        mockResponse.put("communities", communities);
        mockResponse.put("totalHits", 2);
        when(communityManagementService.searchCommunity(any(SearchCriteria.class))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.search(searchCriteria);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        verify(communityManagementService, times(1)).searchCommunity(searchCriteria);
    }

    @Test
    void testCategoryCreateSuccess() {
        ObjectNode communityDetails = objectMapper.createObjectNode();
        communityDetails.put("name", "Technology");
        communityDetails.put("description", "Technology related communities");
        communityDetails.put("status", "ACTIVE");
        String authToken = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.CREATED);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("Category created successfully");
        mockResponse.setParams(params);
        ObjectNode createdCategory = objectMapper.createObjectNode();
        createdCategory.put("id", "category-123");
        createdCategory.put("name", "Technology");
        createdCategory.put("description", "Technology related communities");
        mockResponse.put("category", createdCategory);
        when(communityManagementService.categoryCreate(any(JsonNode.class), eq(authToken))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.communityCtreate(communityDetails, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        verify(communityManagementService, times(1)).categoryCreate(communityDetails, authToken);
    }

    @Test
    void testReadCategorySuccess() {
        String categoryId = "test-category-123";
        String authToken = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        Map<String, Object> category = new HashMap<>();
        category.put("id", categoryId);
        category.put("name", "Test Category");
        category.put("description", "Test Category Description");
        category.put("status", "ACTIVE");
        category.put("createdOn", "2024-01-20T10:00:00Z");
        category.put("updatedOn", "2024-01-20T10:00:00Z");
        mockResponse.put("category", category);
        when(communityManagementService.readCategory(eq(categoryId), eq(authToken))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.readCategory(categoryId, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testDeleteCategorySuccess() {
        String categoryId = "test-category-123";
        String authToken = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("Category deleted successfully");
        mockResponse.setParams(params);
        when(communityManagementService.deleteCategory(eq(categoryId), eq(authToken))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.deleteCategory(categoryId, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        verify(communityManagementService, times(1)).deleteCategory(categoryId, authToken);
    }

    @Test
    void testUpdateCategorySuccess() {
        ObjectNode categoryDetails = objectMapper.createObjectNode();
        categoryDetails.put("id", "test-category-123");
        categoryDetails.put("name", "Updated Category Name");
        categoryDetails.put("description", "Updated Category Description");
        categoryDetails.put("status", "ACTIVE");
        String authToken = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("Category updated successfully");
        mockResponse.setParams(params);
        when(communityManagementService.updateCategory(any(JsonNode.class), eq(authToken))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.updateCategory(categoryDetails, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        verify(communityManagementService, times(1)).updateCategory(categoryDetails, authToken);
    }

    @Test
    void testReadCategoryListSuccess() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        List<Map<String, Object>> categories = new ArrayList<>();
        Map<String, Object> category1 = new HashMap<>();
        category1.put("id", "category-1");
        category1.put("name", "Technology");
        category1.put("description", "Technology related communities");
        category1.put("status", "ACTIVE");
        category1.put("createdOn", "2024-01-20T10:00:00Z");
        Map<String, Object> category2 = new HashMap<>();
        category2.put("id", "category-2");
        category2.put("name", "Education");
        category2.put("description", "Education related communities");
        category2.put("status", "ACTIVE");
        category2.put("createdOn", "2024-01-20T11:00:00Z");
        categories.add(category1);
        categories.add(category2);
        mockResponse.put("categories", categories);
        when(communityManagementService.listOfCategory()).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.readCategory();
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testReadSubCategorySuccess() {
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setSearchString("test");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        List<Map<String, Object>> subcategories = new ArrayList<>();
        Map<String, Object> subcategory1 = new HashMap<>();
        subcategory1.put("id", "subcategory-1");
        subcategory1.put("name", "Java Programming");
        subcategory1.put("categoryId", "category-1");
        subcategory1.put("description", "Java Programming related discussions");
        subcategory1.put("status", "ACTIVE");
        Map<String, Object> subcategory2 = new HashMap<>();
        subcategory2.put("id", "subcategory-2");
        subcategory2.put("name", "Python Programming");
        subcategory2.put("categoryId", "category-1");
        subcategory2.put("description", "Python Programming related discussions");
        subcategory2.put("status", "ACTIVE");
        subcategories.add(subcategory1);
        subcategories.add(subcategory2);
        mockResponse.put("subcategories", subcategories);
        mockResponse.put("totalElements", 2);
        mockResponse.put("totalPages", 1);
        when(communityManagementService.listOfSubCategory(any(SearchCriteria.class))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.readSubCategory(searchCriteria);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testReadAllCategorySuccess() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        List<Map<String, Object>> categories = new ArrayList<>();
        Map<String, Object> category1 = new HashMap<>();
        category1.put("id", "cat-1");
        category1.put("name", "Technology");
        category1.put("description", "Technology related communities");
        category1.put("status", "ACTIVE");
        List<Map<String, Object>> subCategories1 = new ArrayList<>();
        Map<String, Object> subCat1 = new HashMap<>();
        subCat1.put("id", "sub-1");
        subCat1.put("name", "Programming");
        subCat1.put("description", "Programming related discussions");
        subCat1.put("status", "ACTIVE");
        subCategories1.add(subCat1);
        category1.put("subcategories", subCategories1);
        Map<String, Object> category2 = new HashMap<>();
        category2.put("id", "cat-2");
        category2.put("name", "Education");
        category2.put("description", "Education related communities");
        category2.put("status", "ACTIVE");
        List<Map<String, Object>> subCategories2 = new ArrayList<>();
        Map<String, Object> subCat2 = new HashMap<>();
        subCat2.put("id", "sub-2");
        subCat2.put("name", "Mathematics");
        subCat2.put("description", "Mathematics related discussions");
        subCat2.put("status", "ACTIVE");
        subCategories2.add(subCat2);
        category2.put("subcategories", subCategories2);
        categories.add(category1);
        categories.add(category2);
        mockResponse.put("categories", categories);
        when(communityManagementService.lisAllCategoryWithSubCat()).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.readAllCategory();
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testGetTopCommunitiesByMemberCount() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("field", "memberCount");
        payload.put("limit", 5);
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        List<Map<String, Object>> communities = new ArrayList<>();
        Map<String, Object> community1 = new HashMap<>();
        community1.put("id", "comm-1");
        community1.put("name", "Java Developers");
        community1.put("memberCount", 1000);
        community1.put("status", "ACTIVE");
        Map<String, Object> community2 = new HashMap<>();
        community2.put("id", "comm-2");
        community2.put("name", "Python Developers");
        community2.put("memberCount", 800);
        community2.put("status", "ACTIVE");
        communities.add(community1);
        communities.add(community2);
        mockResponse.put("communities", communities);
        when(communityManagementService.getPopularCommunitiesByField(any(Map.class))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.getTopCommunitiesByField(payload);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testReportSubmissionSuccess() {
        String token = "test-auth-token";
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("communityId", "comm-123");
        reportData.put("reason", "Inappropriate content");
        reportData.put("description", "Contains offensive material");
        reportData.put("reportType", "CONTENT");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("Report submitted successfully");
        mockResponse.setParams(params);
        when(communityManagementService.report(eq(token), any(Map.class))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.report(reportData, token);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        verify(communityManagementService, times(1)).report(token, reportData);
    }

    @Test
    void testUploadFileSuccess() {
        String communityId = "comm-123";
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test image content".getBytes());
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("File uploaded successfully");
        mockResponse.setParams(params);
        mockResponse.put("fileUrl", "https://example.com/files/test.jpg");
        mockResponse.put("fileName", "test.jpg");
        mockResponse.put("fileSize", file.getSize());
        mockResponse.put("mimeType", "image/jpeg");
        when(communityManagementService.uploadFile(any(MultipartFile.class), eq(communityId))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.uploadFile(file, communityId);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        assertEquals("test.jpg", response.getBody().get("fileName"));
        verify(communityManagementService, times(1)).uploadFile(file, communityId);
    }

    @Test
    void testTopicSearchSuccess() {
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setSearchString("java programming");
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setOrderBy("relevance");
        searchCriteria.setOrderDirection("desc");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        List<Map<String, Object>> topics = new ArrayList<>();
        Map<String, Object> topic1 = new HashMap<>();
        topic1.put("id", "topic-1");
        topic1.put("title", "Java Programming Basics");
        topic1.put("description", "Introduction to Java programming");
        topic1.put("createdAt", "2024-01-20T10:00:00Z");
        topic1.put("communityId", "comm-123");
        topic1.put("status", "ACTIVE");
        Map<String, Object> topic2 = new HashMap<>();
        topic2.put("id", "topic-2");
        topic2.put("title", "Advanced Java Concepts");
        topic2.put("description", "Advanced topics in Java");
        topic2.put("createdAt", "2024-01-19T15:00:00Z");
        topic2.put("communityId", "comm-123");
        topic2.put("status", "ACTIVE");
        topics.add(topic1);
        topics.add(topic2);
        mockResponse.put("topics", topics);
        mockResponse.put("totalHits", 2);
        mockResponse.put("totalPages", 1);
        when(communityManagementService.searchTopic(any(SearchCriteria.class))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.topicSearch(searchCriteria);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testListAllCommunitiesJoinedByUserSuccess() {
        String authToken = "valid-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        List<Map<String, Object>> communities = new ArrayList<>();
        Map<String, Object> community1 = new HashMap<>();
        community1.put("id", "comm-1");
        community1.put("name", "Java Developers");
        community1.put("description", "Community for Java developers");
        community1.put("memberCount", 1000);
        community1.put("joinedOn", "2024-01-01T10:00:00Z");
        community1.put("role", "MEMBER");
        community1.put("status", "ACTIVE");

        Map<String, Object> community2 = new HashMap<>();
        community2.put("id", "comm-2");
        community2.put("name", "Python Developers");
        community2.put("description", "Community for Python developers");
        community2.put("memberCount", 800);
        community2.put("joinedOn", "2024-01-05T15:00:00Z");
        community2.put("role", "MODERATOR");
        community2.put("status", "ACTIVE");
        communities.add(community1);
        communities.add(community2);

        mockResponse.put("communities", communities);
        mockResponse.put("totalCommunities", 2);
        when(communityManagementService.listAllCommunitiesJoinedByUser(anyString())).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.listAllCommunitiesJoinedByUser(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testPublishCommunitySuccess() {
        String authToken = "valid-auth-token";
        ObjectNode communityDetails = objectMapper.createObjectNode();
        communityDetails.put("communityId", "comm-123");
        communityDetails.put("name", "Test Community");
        communityDetails.put("description", "Test Description");
        communityDetails.put("status", "DRAFT");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("Community published successfully");
        mockResponse.setParams(params);
        mockResponse.put("communityId", "comm-123");
        mockResponse.put("status", "PUBLISHED");
        mockResponse.put("publishedAt", "2024-01-20T10:00:00Z");
        when(communityManagementService.publish(any(JsonNode.class), eq(authToken))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.publish(communityDetails, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
        assertEquals("PUBLISHED", response.getBody().get("status"));
        verify(communityManagementService, times(1)).publish(communityDetails, authToken);
    }

    @Test
    void testSearchCommunityFromEsSuccess() {
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setSearchString("java community");
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setOrderBy("relevance");
        searchCriteria.setOrderDirection("desc");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        List<Map<String, Object>> communities = new ArrayList<>();
        Map<String, Object> community1 = new HashMap<>();
        community1.put("id", "comm-1");
        community1.put("name", "Java Developers Community");
        community1.put("description", "A community for Java developers");
        community1.put("memberCount", 1000);
        community1.put("status", "ACTIVE");
        community1.put("createdAt", "2024-01-01T10:00:00Z");
        community1.put("category", "Technology");
        Map<String, Object> community2 = new HashMap<>();
        community2.put("id", "comm-2");
        community2.put("name", "Java Enterprise Community");
        community2.put("description", "Enterprise Java discussion group");
        community2.put("memberCount", 800);
        community2.put("status", "ACTIVE");
        community2.put("createdAt", "2024-01-05T15:00:00Z");
        community2.put("category", "Technology");
        communities.add(community1);
        communities.add(community2);
        mockResponse.put("communities", communities);
        mockResponse.put("totalHits", 2);
        mockResponse.put("totalPages", 1);
        when(communityManagementService.searchCommunityFromPrimary(any(SearchCriteria.class))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.searchCommunityFromEs(searchCriteria);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testAdminReadCommunitySuccess() {
        String communityId = "comm-123";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        mockResponse.setParams(params);
        Map<String, Object> communityDetails = new HashMap<>();
        communityDetails.put("id", communityId);
        communityDetails.put("name", "Test Community");
        communityDetails.put("description", "A test community");
        communityDetails.put("status", "ACTIVE");
        communityDetails.put("memberCount", 1000);
        communityDetails.put("createdAt", "2024-01-01T10:00:00Z");
        communityDetails.put("updatedAt", "2024-01-20T15:30:00Z");
        communityDetails.put("category", "Technology");
        communityDetails.put("type", "PUBLIC");
        List<Map<String, Object>> moderators = new ArrayList<>();
        Map<String, Object> moderator = new HashMap<>();
        moderator.put("userId", "user-1");
        moderator.put("role", "MODERATOR");
        moderators.add(moderator);
        communityDetails.put("moderators", moderators);
        mockResponse.put("community", communityDetails);
        when(communityManagementService.read(anyString())).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.adminReadCommunity(communityId);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testUserSyncSuccess() {
        String csvContent = "userId,communityId,role\nuser1,comm1,MEMBER\nuser2,comm1,MODERATOR";
        MockMultipartFile file = new MockMultipartFile("file", "users.csv", MediaType.TEXT_PLAIN_VALUE, csvContent.getBytes());
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("successful");
        params.setErrMsg("Users synchronized successfully");
        mockResponse.setParams(params);
        Map<String, Object> syncResults = new HashMap<>();
        syncResults.put("totalProcessed", 2);
        syncResults.put("successCount", 2);
        syncResults.put("failureCount", 0);
        mockResponse.put("syncResults", syncResults);
        when(communityManagementService.syncUserWithCommunity(any(MultipartFile.class))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.searchCommunityFromEs(file);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("successful", response.getBody().getParams().getStatus());
    }

    @Test
    void testUserSyncPartialSuccess() {
        String csvContent = "userId,communityId,role\nuser1,comm1,MEMBER\nuser2,invalid,MODERATOR";
        MockMultipartFile file = new MockMultipartFile("file", "users.csv", MediaType.TEXT_PLAIN_VALUE, csvContent.getBytes());
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.PARTIAL_CONTENT);
        ApiRespParam params = new ApiRespParam();
        params.setStatus("partial_success");
        params.setErrMsg("Some users could not be synchronized");
        mockResponse.setParams(params);
        Map<String, Object> syncResults = new HashMap<>();
        syncResults.put("totalProcessed", 2);
        syncResults.put("successCount", 1);
        syncResults.put("failureCount", 1);
        List<Map<String, String>> failures = new ArrayList<>();
        Map<String, String> failure = new HashMap<>();
        failure.put("userId", "user2");
        failure.put("reason", "Invalid community ID");
        failures.add(failure);
        syncResults.put("failures", failures);
        mockResponse.put("syncResults", syncResults);
        when(communityManagementService.syncUserWithCommunity(any(MultipartFile.class))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = communityController.searchCommunityFromEs(file);
        assertNotNull(response);
        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        assertEquals("partial_success", response.getBody().getParams().getStatus());
    }

}
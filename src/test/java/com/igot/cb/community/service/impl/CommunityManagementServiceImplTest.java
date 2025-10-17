package com.igot.cb.community.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.community.entity.CommunityCategory;
import com.igot.cb.community.entity.CommunityEntity;
import com.igot.cb.community.kafka.producer.Producer;
import com.igot.cb.community.repository.CommunityCategoryRepository;
import com.igot.cb.community.repository.CommunityEngagementRepository;
import com.igot.cb.community.service.NotificationService;
import com.igot.cb.community.service.UserService;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.*;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.cloud.storage.factory.StorageConfig;
import org.sunbird.cloud.storage.factory.StorageServiceFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class CommunityManagementServiceImplTest {

    @InjectMocks
    private CommunityManagementServiceImpl service;

    @Mock
    private EsUtilService esUtilService;
    @Mock
    private CacheService cacheService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private CbServerProperties cbServerProperties;
    @Mock
    private PayloadValidation payloadValidation;
    @Mock
    private CommunityEngagementRepository communityEngagementRepository;
    @Mock
    private AccessTokenValidator accessTokenValidator;
    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private CassandraOperation spyCassandraOperation;
    @Mock
    private RedisTemplate<String, SearchResult> redisTemplate;

    @Mock
    private CommunityCategoryRepository categoryRepository;
    @Mock
    private RedisTemplate<String, Object> objectRedisTemplate;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private FileProcessService fileProcessService;

    private MockedStatic<StorageServiceFactory> storageFactoryMockedStatic;

    @Mock
    BaseStorageService mockStorageService;

    @Mock
    private ValueOperations<String, SearchResult> valueOperations;

    @Mock
    private CommunityEntity savedCommunityEntity;
    @Mock
    private CommunityCategory communityCategory;
    @Mock
    private Producer producer;
    private JsonNode categoryDetails;

    private String failed = "Failed";
    public static final String COMMUNITY_ID = "communityId";
    public static final String COMMUNITY_ID_LOWERCASE = "communityid";
    public static final String COMMUNITY_DETAILS = "communityDetails";
    public static final String USER_ID = "userId";
    public static final String STATUS = "status";
    public static final String API_COMMUNITY_USER_JOINED = "api.community.user.joined";
    public static final String ERROR = "ERROR";
    public static final String USER_ID_DOESNT_EXIST = "User Id doesn't exist! Please supply a valid auth token";
    private static final String AUTH_TOKEN = "authToken";
    private static final String VALID_CATEGORY_ID = "101";
    private static final String CATEGORY_ID = "102";
    private static final int INTEGER_CATEGORY_ID = 101;
    private static final String REDIS_KEY = Constants.CATEGORY_LIST_REDIS_KEY_PREFIX;

    private SearchCriteria searchCriteria;
    private SearchResult searchResult;
    private SearchCriteria validSearchCriteria;

    @BeforeEach
    void setup() {
        searchCriteria = new SearchCriteria();
        searchCriteria.setSearchString("Test");
        searchResult = new SearchResult();
        searchResult.setData(new ObjectMapper().createArrayNode());
        ReflectionTestUtils.setField(service, "cbServerProperties", cbServerProperties);

        storageFactoryMockedStatic = Mockito.mockStatic(StorageServiceFactory.class);
        storageFactoryMockedStatic.when(() -> StorageServiceFactory.getStorageService(any(StorageConfig.class))).thenReturn(mockStorageService); // ✅ must match method return type

        service.init();
    }

    @AfterEach
    void tearDown() {
        if (storageFactoryMockedStatic != null) {
            storageFactoryMockedStatic.close();
        }
    }


    private ObjectNode getValidCommunityJsonNode() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put("communityName", "testCommunity");
        node.put("topicId", 123);
        node.put("CommunityCreationAllowed", true);
        return node;
    }

    @Test
    void testCreate_whenUserIdBlank_thenBadRequest() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("");

        ApiResponse response = service.create(getValidCommunityJsonNode(), "auth-token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(failed, response.getParams().getStatus());
    }

    @Test
    void testCreate_whenPayloadValidationFails_thenBadRequest() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("userId");
        doThrow(new CustomException("VALIDATION_FAILED", "Payload error", HttpStatus.BAD_REQUEST)).when(payloadValidation).validatePayload(anyString(), any());

        ApiResponse response = service.create(getValidCommunityJsonNode(), "auth-token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(failed, response.getParams().getStatus());
    }

    @Test
    void testCreate_whenCategoryInactive_thenBadRequest() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("userId");
        doNothing().when(payloadValidation).validatePayload(anyString(), any());
        when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true))).thenReturn(null);

        ApiResponse response = service.create(getValidCommunityJsonNode(), "auth-token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(failed, response.getParams().getStatus());
    }

    @Test
    void testCreate_whenUserNotFound_thenBadRequest() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("userId");
        doNothing().when(payloadValidation).validatePayload(anyString(), any());
        when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true))).thenReturn(new CommunityCategory());
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt())).thenReturn(Collections.emptyList());

        ApiResponse response = service.create(getValidCommunityJsonNode(), "auth-token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testCreate_whenCommunityAlreadyExists_thenConflict() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("userId");
        doNothing().when(payloadValidation).validatePayload(anyString(), any());
        when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true))).thenReturn(new CommunityCategory());

        Map<String, Object> user = new HashMap<>();
        user.put("rootOrgId", "org123");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), eq("user"), any(), any(), anyInt())).thenReturn(List.of(user));

        lenient().when(esUtilService.doesCommunityExist(anyString(), anyString())).thenReturn(true);

        ApiResponse response = service.create(getValidCommunityJsonNode(), "auth-token");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testCreate_whenCommunityNameExistsNotAllowed_thenPreconditionFailed() {
        ObjectNode node = getValidCommunityJsonNode();
        node.put("CommunityCreationAllowed", false);

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("userId");
        doNothing().when(payloadValidation).validatePayload(anyString(), any());
        when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true))).thenReturn(new CommunityCategory());

        Map<String, Object> user = Map.of("rootOrgId", "org123");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(eq("sunbird"), eq("user"), any(), any(), anyInt())).thenReturn(List.of(user));

        lenient().when(esUtilService.doesCommunityExist(anyString(), anyString())).thenReturn(false);
        lenient().when(esUtilService.doesCommunityNameExist(anyString())).thenReturn(true);

        ApiResponse response = service.create(node, "auth-token");

        assertEquals(HttpStatus.PRECONDITION_FAILED, response.getResponseCode());
    }

    @Test
    void testCreate_whenOrgDetailsMissing_thenNotFound() {

        when(cbServerProperties.getCloudStorageTypeName()).thenReturn("S3");
        when(cbServerProperties.getCloudStorageKey()).thenReturn("key");
        when(cbServerProperties.getCloudStorageSecret()).thenReturn("secret");
        when(cbServerProperties.getCloudStorageEndpoint()).thenReturn("endpoint");
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("userId");
        doNothing().when(payloadValidation).validatePayload(anyString(), any());
        when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true))).thenReturn(new CommunityCategory());

        Map<String, Object> user = Map.of("rootOrgId", "org123");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(eq("sunbird"), eq("user"), any(), any(), anyInt())).thenReturn(List.of(user));

        lenient().when(esUtilService.doesCommunityExist(anyString(), anyString())).thenReturn(false);
        lenient().when(esUtilService.doesCommunityNameExist(anyString())).thenReturn(false);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(eq("sunbird"), eq("org"), any(), any(), anyInt())).thenReturn(Collections.emptyList());

        ApiResponse response = service.create(getValidCommunityJsonNode(), "auth-token");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testCreateCommunitySuccess() {
        // Prepare mocks
        String authToken = "validToken";
        String userId = "user-123";
        int topicId = 456;
        String communityName = "Test Community";
        String rootOrgId = "org-789";

        JsonNode communityDetails = JsonNodeFactory.instance.objectNode();
        ((ObjectNode) communityDetails).put(Constants.TOPIC_ID, topicId);
        ((ObjectNode) communityDetails).put(Constants.COMMUNITY_NAME, communityName);
        ((ObjectNode) communityDetails).put(Constants.CommunityCreationAllowed, false);

        // Payload validation: no exception
        doNothing().when(payloadValidation).validatePayload(anyString(), any());

        // Access token validator returns a valid user
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);

        // Category found
        when(categoryRepository.findByCategoryIdAndIsActive(topicId, true)).thenReturn(communityCategory);

        // User root org details from Cassandra
        Map<String, Object> userDetailsMap = new HashMap<>();
        userDetailsMap.put(Constants.USER_ROOT_ORG_ID, rootOrgId);
        List<Map<String, Object>> userDetailsList = List.of(userDetailsMap);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), eq(Constants.TABLE_USER), any(), any(), anyInt())).thenReturn(userDetailsList);

        // Community name doesn't exist
        when(esUtilService.doesCommunityExist(eq(rootOrgId), eq(communityName))).thenReturn(false);
        when(esUtilService.doesCommunityNameExist(eq(communityName))).thenReturn(false);

        // Org details
        Map<String, Object> orgDetailsMap = Map.of(Constants.ORG_NAME, "Test Org");
        List<Map<String, Object>> orgDetailsList = List.of(orgDetailsMap);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), eq(Constants.ORG_TABLE), any(), any(), anyInt())).thenReturn(orgDetailsList);

        // Mock save
        when(communityEngagementRepository.save(any())).thenReturn(savedCommunityEntity);
        when(savedCommunityEntity.getData()).thenReturn(communityDetails);

        // Convert value
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());

        // Run
        ApiResponse result = service.create(communityDetails, authToken);

        // Assert
        assertEquals(HttpStatus.OK, result.getResponseCode());
        assertEquals(Constants.SUCCESSFULLY_CREATED, result.getResult().get(Constants.STATUS));
        assertTrue(result.getResult().containsKey(Constants.COMMUNITY_ID));

    }

    @Test
    void testCreateCommunity_conflictWhenCommunityAlreadyExistsInOrg() {
        // Given
        String authToken = "validToken";
        String userId = "user-123";
        int topicId = 456;
        String communityName = "Test Community";
        String rootOrgId = "org-789";

        ObjectNode communityDetails = JsonNodeFactory.instance.objectNode();
        communityDetails.put(Constants.TOPIC_ID, topicId);
        communityDetails.put(Constants.COMMUNITY_NAME, communityName);
        communityDetails.put(Constants.CommunityCreationAllowed, false);

        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(categoryRepository.findByCategoryIdAndIsActive(topicId, true)).thenReturn(new CommunityCategory());

        Map<String, Object> userDetailsMap = Map.of(Constants.USER_ROOT_ORG_ID, rootOrgId);
        List<Map<String, Object>> userDetailsList = List.of(userDetailsMap);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), any(), any(), anyInt())).thenReturn(userDetailsList);

        // Simulate community already exists in same org
        when(esUtilService.doesCommunityExist(rootOrgId, communityName)).thenReturn(true);

        // When
        ApiResponse result = service.create(communityDetails, authToken);

        // Then
        assertEquals(HttpStatus.CONFLICT, result.getResponseCode());
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertEquals(Constants.CREATE_ERROR_MSG_WITHIN_COMMUNITY, result.getParams().getErrMsg());

        // Verify that method exited before trying to save
        verify(communityEngagementRepository, never()).save(any());
    }

    @Test
    void testCreateCommunity_whenSavedDataIsNull_shouldReturnInternalServerError() {
        // Given
        String authToken = "validToken";
        String userId = "user-123";
        int topicId = 456;
        String communityName = "Test Community";
        String rootOrgId = "org-789";

        ObjectNode communityDetails = JsonNodeFactory.instance.objectNode();
        communityDetails.put(Constants.TOPIC_ID, topicId);
        communityDetails.put(Constants.COMMUNITY_NAME, communityName);
        communityDetails.put(Constants.CommunityCreationAllowed, true);

        CommunityCategory category = new CommunityCategory();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(categoryRepository.findByCategoryIdAndIsActive(topicId, true)).thenReturn(category);

        Map<String, Object> userDetailsMap = Map.of(Constants.USER_ROOT_ORG_ID, rootOrgId);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), any(), any(), anyInt())).thenReturn(List.of(userDetailsMap));

        when(esUtilService.doesCommunityExist(eq(rootOrgId), anyString())).thenReturn(false);
        when(esUtilService.doesCommunityNameExist(anyString())).thenReturn(false);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), any(), isNull(), eq(1))).thenReturn(List.of(Map.of(Constants.ORG_NAME, "Test Org")));

        CommunityEntity savedEntity = new CommunityEntity();
        savedEntity.setData(NullNode.getInstance());  // Simulate data is null
        when(communityEngagementRepository.save(any())).thenReturn(savedEntity);

        // When
        ApiResponse response = service.create(communityDetails, authToken);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.FAILED, response.getParams().getErrMsg());
    }

    @Test
    void testRead_SuccessFromCache() throws Exception {
        String communityId = "comm123";
        String token = "token123";
        String userId = "user123";
        String cachedJson = "{\"name\":\"test\"}";

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(cacheService.getCache(communityId)).thenReturn(cachedJson);
        when(objectMapper.readValue(eq(cachedJson), any(TypeReference.class))).thenReturn(Map.of("name", "test"));

        ApiResponse response = service.read(communityId, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("successfully read", response.getParams().getErrMsg());
        assertTrue(response.getResult().containsKey("communityDetails"));
    }

    @Test
    void testRead_SuccessFromDB() throws Exception {
        String communityId = "comm123";
        String token = "token123";
        String userId = "user123";

        CommunityEntity mockEntity = new CommunityEntity();
        mockEntity.setCommunityId(communityId);

        Map<String, Object> dataMap = Map.of("community", "value");
        JsonNode dataJson = new ObjectMapper().valueToTree(dataMap); // ✅ convert Map to JsonNode

        mockEntity.setData(dataJson);

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(cacheService.getCache(communityId)).thenReturn(null);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true)).thenReturn(Optional.of(mockEntity));
        when(objectMapper.convertValue(eq(dataJson), any(TypeReference.class))).thenReturn(dataMap);

        ApiResponse response = service.read(communityId, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("successfully read", response.getParams().getErrMsg());
        assertTrue(response.getResult().containsKey("communityDetails"));
    }

    @Test
    void testRead_BlankUserId() {
        String token = "invalid-token";
        when(accessTokenValidator.verifyUserToken(token)).thenReturn("");

        ApiResponse response = service.read("anyId", token);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Id not found", response.getParams().getErrMsg());
    }

    @Test
    void testRead_BlankCommunityId() {
        when(accessTokenValidator.verifyUserToken("token")).thenReturn("user123");

        ApiResponse response = service.read("", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Id not found", response.getParams().getErrMsg());
    }

    @Test
    void testRead_CommunityIdNotFound() {
        when(accessTokenValidator.verifyUserToken("token")).thenReturn("user123");
        when(cacheService.getCache("comm123")).thenReturn(null);
        when(communityEngagementRepository.findByCommunityIdAndIsActive("comm123", true)).thenReturn(Optional.empty());

        ApiResponse response = service.read("comm123", "token");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals("Invalid Community Id", response.getParams().getErrMsg());
    }

    @Test
    void testRead_JsonMappingException() throws Exception {
        String communityId = "comm123";
        String cachedJson = "{\"invalidJson\":}";

        when(accessTokenValidator.verifyUserToken("token")).thenReturn("user123");
        when(cacheService.getCache(communityId)).thenReturn(cachedJson);
        when(objectMapper.readValue(eq(cachedJson), any(TypeReference.class))).thenThrow(new RuntimeException("Mapping error"));

        CustomException exception = assertThrows(CustomException.class, () -> {
            service.read(communityId, "token");
        });

        assertEquals("ERROR", exception.getCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatusCode());
    }

    @Test
    void testDelete_Success() {
        String communityId = "test-community";
        String authToken = "valid-token";
        String userId = "user123";

        JsonNode mockData = new ObjectMapper().createObjectNode().put(Constants.TOPIC_ID, 10);
        CommunityEntity entity = new CommunityEntity();
        entity.setCommunityId(communityId);
        entity.setActive(true);
        entity.setData(mockData);

        CommunityCategory category = new CommunityCategory();
        category.setCategoryId(10);
        category.setCountOfCommunities(5L);

        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true)).thenReturn(Optional.of(entity));
        when(categoryRepository.findByCategoryIdAndIsActive(10, true)).thenReturn(category);
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(cbServerProperties.getElasticCommunityJsonPath()).thenReturn("path");
        when(cbServerProperties.getElasticCommunityCategoryJsonPath()).thenReturn("catPath");

        ApiResponse response = service.delete(communityId, authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("Deleted the community with id: " + communityId, response.getResult().get(Constants.RESPONSE));
    }

    @Test
    void testDelete_InvalidToken() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

        ApiResponse response = service.delete("communityId", "invalidToken");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testDelete_EmptyCommunityId() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user123");

        ApiResponse response = service.delete("", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.COMMUNITY_ID_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testDelete_CommunityNotFound() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user123");
        when(communityEngagementRepository.findByCommunityIdAndIsActive(anyString(), eq(true))).thenReturn(Optional.empty());

        ApiResponse response = service.delete("communityId", "token");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErrMsg());
    }

    @Test
    void testDelete_TopicInactive() {
        String communityId = "communityId";
        JsonNode mockData = new ObjectMapper().createObjectNode().put(Constants.TOPIC_ID, 10);

        CommunityEntity entity = new CommunityEntity();
        entity.setCommunityId(communityId);
        entity.setData(mockData);
        entity.setActive(true);

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user123");
        when(communityEngagementRepository.findByCommunityIdAndIsActive(eq(communityId), eq(true))).thenReturn(Optional.of(entity));
        when(categoryRepository.findByCategoryIdAndIsActive(eq(10), eq(true))).thenReturn(null);

        ApiResponse response = service.delete(communityId, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.TOPIC_IS_INACTIVE, response.getParams().getErrMsg());
    }

    @Test
    void testDelete_ExceptionThrown() {
        String communityId = "communityId";
        JsonNode mockData = new ObjectMapper().createObjectNode().put(Constants.TOPIC_ID, 10);

        CommunityEntity entity = new CommunityEntity();
        entity.setCommunityId(communityId);
        entity.setData(mockData);
        entity.setActive(true);

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user123");
        when(communityEngagementRepository.findByCommunityIdAndIsActive(eq(communityId), eq(true))).thenReturn(Optional.of(entity));
        when(categoryRepository.findByCategoryIdAndIsActive(eq(10), eq(true))).thenThrow(new RuntimeException("Unexpected error"));

        CustomException ex = assertThrows(CustomException.class, () -> {
            service.delete(communityId, "token");
        });

        assertEquals("error while processing", ex.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatusCode());
    }

    @Test
    void testUpdate_BlankToken() {
        ApiResponse response = service.update(JsonNodeFactory.instance.objectNode(), "");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testUpdate_CommunityIdMissing() {
        when(accessTokenValidator.verifyUserToken(any())).thenReturn("userId");
        ObjectNode node = JsonNodeFactory.instance.objectNode(); // no communityId
        ApiResponse response = service.update(node, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.COMMUNITY_ID_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testUpdate_CommunityNotFound() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(Constants.COMMUNITY_ID, "123");

        when(accessTokenValidator.verifyUserToken(any())).thenReturn("userId");
        when(communityEngagementRepository.findByCommunityIdAndIsActive("123", true)).thenReturn(Optional.empty());

        ApiResponse response = service.update(node, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErrMsg());
    }

    @Test
    void testUpdate_DuplicateCommunity() {
        ObjectNode updateNode = JsonNodeFactory.instance.objectNode();
        updateNode.put(Constants.COMMUNITY_ID, "123");

        ObjectNode dataNode = JsonNodeFactory.instance.objectNode();
        dataNode.put(Constants.COMMUNITY_NAME, "test");
        dataNode.put(Constants.COMMUNITY_ID, "123");
        dataNode.put(Constants.ORG_ID, "org1");

        CommunityEntity entity = new CommunityEntity();
        entity.setCommunityId("123");
        entity.setData(dataNode);

        when(accessTokenValidator.verifyUserToken(any())).thenReturn("userId");
        when(communityEngagementRepository.findByCommunityIdAndIsActive("123", true)).thenReturn(Optional.of(entity));
        when(esUtilService.isDuplicateCommunity("org1", "test", "123")).thenReturn(true);

        ApiResponse response = service.update(updateNode, "token");
        assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
        assertEquals(Constants.CREATE_ERROR_MSG_WITHIN_COMMUNITY, response.getParams().getErrMsg());
    }

    @Test
    void testUpdate_NameConflictWhenNotAllowed() {
        ObjectNode updateNode = JsonNodeFactory.instance.objectNode();
        updateNode.put(Constants.COMMUNITY_ID, "123");
        updateNode.put(Constants.CommunityCreationAllowed, false);

        ObjectNode dataNode = JsonNodeFactory.instance.objectNode();
        dataNode.put(Constants.COMMUNITY_NAME, "test");
        dataNode.put(Constants.COMMUNITY_ID, "123");
        dataNode.put(Constants.ORG_ID, "org1");

        CommunityEntity entity = new CommunityEntity();
        entity.setCommunityId("123");
        entity.setData(dataNode);

        when(accessTokenValidator.verifyUserToken(any())).thenReturn("userId");
        when(communityEngagementRepository.findByCommunityIdAndIsActive("123", true)).thenReturn(Optional.of(entity));
        when(esUtilService.isDuplicateCommunity(any(), any(), any())).thenReturn(false);
        when(esUtilService.doesCommunityNameExistForPublish(any(), any())).thenReturn(true);

        ApiResponse response = service.update(updateNode, "token");
        assertEquals(HttpStatus.PRECONDITION_FAILED, response.getResponseCode());
        assertEquals(Constants.CREATE_ERROR_MSG_COMMUNITY, response.getParams().getErrMsg());
    }

    @Test
    void testUpdate_SuccessfulUpdate() {
        ObjectNode updateNode = JsonNodeFactory.instance.objectNode();
        updateNode.put(Constants.COMMUNITY_ID, "123");
        updateNode.put(Constants.CommunityCreationAllowed, true);
        updateNode.put("newField", "value");

        ObjectNode dataNode = JsonNodeFactory.instance.objectNode();
        dataNode.put(Constants.COMMUNITY_NAME, "test");
        dataNode.put(Constants.COMMUNITY_ID, "123");
        dataNode.put(Constants.ORG_ID, "org1");

        CommunityEntity entity = new CommunityEntity();
        entity.setCommunityId("123");
        entity.setData(dataNode);

        when(accessTokenValidator.verifyUserToken(any())).thenReturn("userId");
        when(communityEngagementRepository.findByCommunityIdAndIsActive("123", true)).thenReturn(Optional.of(entity));
        when(esUtilService.isDuplicateCommunity(any(), any(), any())).thenReturn(false);

        ApiResponse response = service.update(updateNode, "token");

        assertEquals("Updated the community with id: 123", response.getResult().get(Constants.RESPONSE));
    }

    @Test
    void testUpdate_ExceptionThrown() {
        when(accessTokenValidator.verifyUserToken(any())).thenThrow(new RuntimeException("fail"));

        CustomException ex = assertThrows(CustomException.class, () -> service.update(JsonNodeFactory.instance.objectNode(), "token"));

        assertEquals("error while processing", ex.getMessage());
    }

    @Test
    void testJoinCommunity_Success_NewUser() {
        String token = "authToken";
        String userId = "user-123";
        String communityId = "community-123";

        Map<String, Object> request = Map.of(Constants.COMMUNITY_ID, communityId);

        CommunityEntity entity = new CommunityEntity();
        ObjectNode dataNode = new ObjectMapper().createObjectNode();
        dataNode.put("someKey", "value");
        entity.setData(dataNode);

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true)).thenReturn(Optional.of(entity));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), any(), anyInt())).thenReturn(Collections.emptyList());

        ApiResponse response = service.joinCommunity(request, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cassandraOperation).insertRecord(anyString(), anyString(), anyMap());
        verify(esUtilService).updateUserIndex(userId, communityId, true);
        verify(cacheService).deleteCache(anyString());
    }

    @Test
    void testJoinCommunity_Success_RejoiningUser() {
        String token = "authToken";
        String userId = "user-123";
        String communityId = "community-123";

        Map<String, Object> request = Map.of(Constants.COMMUNITY_ID, communityId);

        CommunityEntity entity = new CommunityEntity();
        ObjectNode dataNode = new ObjectMapper().createObjectNode();
        dataNode.put("someKey", "value");
        entity.setData(dataNode);

        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.STATUS, false);

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true)).thenReturn(Optional.of(entity));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), any(), anyInt())).thenReturn(List.of(dbRecord));

        ApiResponse response = service.joinCommunity(request, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cassandraOperation).updateRecord(anyString(), anyString(), anyMap(), anyMap());
        verify(esUtilService).updateUserIndex(userId, communityId, true);
        verify(cacheService).deleteCache(anyString());
    }

    @Test
    void testJoinCommunity_AlreadyJoinedUser() {
        String token = "authToken";
        String userId = "user-123";
        String communityId = "community-123";

        Map<String, Object> request = Map.of(Constants.COMMUNITY_ID, communityId);

        CommunityEntity entity = new CommunityEntity();
        ObjectNode dataNode = new ObjectMapper().createObjectNode();
        dataNode.put("someKey", "value");
        entity.setData(dataNode);

        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.STATUS, true);

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true)).thenReturn(Optional.of(entity));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), any(), anyInt())).thenReturn(List.of(dbRecord));

        ApiResponse response = service.joinCommunity(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ALREADY_JOINED_COMMUNITY, response.getParams().getErr());
    }

    @Test
    void testJoinCommunity_InvalidToken() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

        Map<String, Object> request = Map.of(Constants.COMMUNITY_ID, "communityId");

        ApiResponse response = service.joinCommunity(request, "authToken");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testJoinCommunity_InvalidCommunityId() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("userId");
        when(communityEngagementRepository.findByCommunityIdAndIsActive(anyString(), eq(true))).thenReturn(Optional.empty());

        Map<String, Object> request = Map.of(Constants.COMMUNITY_ID, "communityId");

        ApiResponse response = service.joinCommunity(request, "authToken");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErr());
    }

    @Test
    void testJoinCommunity_EmptyCommunityId() {
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.COMMUNITY_ID, "");

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("userId");

        ApiResponse response = service.joinCommunity(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains(Constants.COMMUNITY_ID));
    }

    @Test
    void testJoinCommunity_ExceptionThrown() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenThrow(new RuntimeException("error"));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.COMMUNITY_ID, "cid");

        CustomException exception = assertThrows(CustomException.class, () -> service.joinCommunity(request, "token"));

        assertEquals("error while processing", exception.getMessage());
    }

    @Test
    void testCommunitiesJoinedByUser_Success_WithCachedData() throws JsonProcessingException {
        String token = "valid-token";
        String userId = "user-123";
        String communityId = "comm-123";
        String cachedJson = "{\"name\":\"Java Community\"}";
        Map<String, Object> record = Map.of(COMMUNITY_ID, communityId, COMMUNITY_ID_LOWERCASE, communityId, STATUS, true);

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), anyList(), isNull())).thenReturn(List.of(record));
        when(cacheService.getCache(communityId)).thenReturn(cachedJson);
        when(objectMapper.readValue(eq(cachedJson), any(TypeReference.class))).thenReturn(Map.of("name", "Java Community"));
        when(objectMapper.convertValue(any(), any(TypeReference.class))).thenReturn(new ArrayList<>());

        ApiResponse response = service.communitiesJoinedByUser(token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(COMMUNITY_DETAILS));
        assertTrue(response.getResult().containsKey(COMMUNITY_ID));
    }

    @Test
    void testCommunitiesJoinedByUser_Success_WithRepositoryFallback() {
        String token = "valid-token";
        String userId = "user-123";
        String communityId = "comm-123";
        Map<String, Object> record = Map.of(COMMUNITY_ID, communityId, COMMUNITY_ID_LOWERCASE, communityId, STATUS, true);

        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("name", "Fallback Community");
        CommunityEntity entity = new CommunityEntity();
        entity.setData(node);

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), anyList(), isNull())).thenReturn(List.of(record));
        when(cacheService.getCache(communityId)).thenReturn(null);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true)).thenReturn(Optional.of(entity));
        when(objectMapper.convertValue(any(), any(TypeReference.class))).thenReturn(new ArrayList<>());

        ApiResponse response = service.communitiesJoinedByUser(token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cacheService).putCache(eq(communityId), eq(node));
    }

    @Test
    void testCommunitiesJoinedByUser_Success_NoCommunities() {
        String token = "valid-token";
        String userId = "user-123";

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), anyList(), isNull())).thenReturn(Collections.emptyList());
        when(objectMapper.convertValue(any(), any(TypeReference.class))).thenReturn(new ArrayList<>());

        ApiResponse response = service.communitiesJoinedByUser(token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(((List<?>) response.getResult().get(COMMUNITY_ID)).isEmpty());
        assertTrue(((List<?>) response.getResult().get(COMMUNITY_DETAILS)).isEmpty());
    }

    @Test
    void testCommunitiesJoinedByUser_InvalidUser() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("");

        ApiResponse response = service.communitiesJoinedByUser("token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testCommunitiesJoinedByUser_JsonParseException() throws JsonProcessingException {
        String token = "valid-token";
        String userId = "user-123";
        String communityId = "comm-123";

        Map<String, Object> record = Map.of(COMMUNITY_ID, communityId, COMMUNITY_ID_LOWERCASE, communityId, STATUS, true);

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), anyList(), isNull())).thenReturn(List.of(record));
        when(cacheService.getCache(communityId)).thenReturn("invalid-json");
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenThrow(JsonProcessingException.class);

        CustomException ex = assertThrows(CustomException.class, () -> service.communitiesJoinedByUser(token));
        assertEquals("error while processing", ex.getMessage());
    }

    @Test
    void testCommunitiesJoinedByUser_GenericException() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenThrow(new RuntimeException("DB failure"));

        CustomException ex = assertThrows(CustomException.class, () -> service.communitiesJoinedByUser("token"));
        assertEquals("error while processing", ex.getMessage());
    }

    @Test
    void testListOfUsersJoined_fallbackToCassandra() throws JsonProcessingException {
        String authToken = "valid-token";
        String communityId = "community-2";
        Map<String, Object> request = Map.of(Constants.COMMUNITY_ID, communityId, Constants.OFFSET, 0, Constants.LIMIT, 10);

        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn("user-id");
        when(cacheService.getListSize(Constants.CMMUNITY_USER_REDIS_PREFIX + communityId)).thenReturn(0L);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any())).thenReturn(List.of(Map.of(Constants.STATUS, true, Constants.USER_ID_LOWER_CASE, "user3")));
        when(cacheService.getPaginatedUsersFromHash(any(), anyInt(), anyInt())).thenReturn(List.of("user:user3"));
        when(cacheService.hget(List.of("user:user3"))).thenReturn(List.of("{\"userId\":\"user3\",\"designation\":\"Engineer\"}"));
        when(objectMapper.readValue(anyString(), eq(Object.class))).thenReturn(Map.of(Constants.USER_ID_KEY, "user3", Constants.DESIGNATION, "Engineer"));
        when(userService.fetchUserFromprimary(anyList())).thenReturn(Collections.emptyList());

        ApiResponse response = service.listOfUsersJoined(authToken, request);
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testListOfUsersJoined_invalidPayload() {
        Map<String, Object> request = Map.of(Constants.COMMUNITY_ID, "community-1");

        ApiResponse response = service.listOfUsersJoined("token", request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testListOfUsersJoined_emptyUserIdFromToken() {
        Map<String, Object> request = Map.of(Constants.COMMUNITY_ID, "community-id", Constants.OFFSET, 0, Constants.LIMIT, 10);

        when(accessTokenValidator.verifyUserToken(any())).thenReturn("");

        ApiResponse response = service.listOfUsersJoined("token", request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains(Constants.USER_ID_DOESNT_EXIST));
    }

    @Test
    void testListOfUsersJoined_Exception() {
        Map<String, Object> request = Map.of(Constants.COMMUNITY_ID, "community-id", Constants.OFFSET, 0, Constants.LIMIT, 10);

        when(accessTokenValidator.verifyUserToken(any())).thenReturn("user-id");
        when(cacheService.getListSize(any())).thenThrow(new RuntimeException("Redis down"));

        CustomException ex = assertThrows(CustomException.class, () -> service.listOfUsersJoined("token", request));

        assertEquals("error while processing", ex.getMessage());
        assertEquals(Constants.ERROR, ex.getCode());
    }

    @Test
    void testUnJoinCommunity_success() throws Exception {
        String token = "valid-token";
        String userId = "user123";
        String communityId = "comm123";

        Map<String, Object> request = Map.of("communityId", communityId);

        CommunityEntity entity = new CommunityEntity();
        entity.setCommunityId(communityId);
        ObjectNode dataNode = new ObjectMapper().createObjectNode();
        dataNode.put("countOfPeopleJoined", 5);
        entity.setData(dataNode);

        Map<String, Object> existingRecord = new HashMap<>();
        existingRecord.put("status", true);
        List<Map<String, Object>> userCommunityDetails = List.of(existingRecord);

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true)).thenReturn(Optional.of(entity));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt())).thenReturn(userCommunityDetails);
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(Map.of());

        ApiResponse response = service.unJoinCommunity(request, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(communityEngagementRepository).save(any());
        verify(cacheService).deleteUserFromHash(any(), any());
        verify(esUtilService).updateUserIndex(userId, communityId, false);
    }

    @Test
    void testUnJoinCommunity_invalidUserId() {
        String token = "invalid-token";
        Map<String, Object> request = Map.of("communityId", "comm123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn("");

        ApiResponse response = service.unJoinCommunity(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("User Id doesn't exist! Please supply a valid auth token", response.getParams().getErrMsg());
    }

    @Test
    void testUnJoinCommunity_invalidPayload() {
        String token = "valid-token";
        String userId = "user123";

        Map<String, Object> request = new HashMap<>();
        request.put("communityId", "");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);

        ApiResponse response = service.unJoinCommunity(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains("Failed Due To Missing Params"));
    }

    @Test
    void testUnJoinCommunity_invalidCommunity() {
        String token = "valid-token";
        String userId = "user123";
        Map<String, Object> request = Map.of("communityId", "comm123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(anyString(), anyBoolean())).thenReturn(Optional.empty());

        ApiResponse response = service.unJoinCommunity(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid Community Id", response.getParams().getErr());
    }

    @Test
    void testUnJoinCommunity_alreadyUnjoined() {
        String token = "valid-token";
        String userId = "user123";
        String communityId = "comm123";

        Map<String, Object> request = Map.of("communityId", communityId);
        CommunityEntity entity = new CommunityEntity();
        entity.setData(new ObjectMapper().createObjectNode());

        Map<String, Object> existingRecord = Map.of("status", false);
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true)).thenReturn(Optional.of(entity));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt())).thenReturn(List.of(existingRecord));

        ApiResponse response = service.unJoinCommunity(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid Community Id", response.getParams().getErr());
    }

    @Test
    void testUnJoinCommunity_neverJoined() {
        String token = "valid-token";
        String userId = "user123";
        Map<String, Object> request = Map.of("communityId", "comm123");

        CommunityEntity entity = new CommunityEntity();
        entity.setData(new ObjectMapper().createObjectNode());

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(anyString(), anyBoolean())).thenReturn(Optional.of(entity));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt())).thenReturn(Collections.emptyList());

        ApiResponse response = service.unJoinCommunity(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid Community Id", response.getParams().getErr());
    }

    @Test
    void testUnJoinCommunity_exception() {
        String token = "valid-token";
        String userId = "user123";
        Map<String, Object> request = Map.of("communityId", "comm123");

        when(accessTokenValidator.verifyUserToken(token)).thenThrow(new RuntimeException("Unexpected"));

        CustomException thrown = assertThrows(CustomException.class, () -> {
            service.unJoinCommunity(request, token);
        });

        assertEquals("error while processing", thrown.getMessage());
    }

    @Test
    void testSearchCommunity_InvalidSearchString() {
        searchCriteria.setSearchString("A");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ApiResponse response = service.searchCommunity(searchCriteria);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED_CONST, response.getParams().getStatus());
    }

    @Test
    void testSearchCommunity_CacheMissAndFetchSuccess() throws Exception {
        searchCriteria.setSearchString("Test");
        searchCriteria.setOverrideCache(true);

        when(esUtilService.searchDocuments(any(), any())).thenReturn(searchResult);
        when(objectMapper.convertValue(any(), any(TypeReference.class))).thenReturn(new ArrayList<>());
        when(objectMapper.writeValueAsString(any())).thenReturn("payload");
        when(cbServerProperties.getSearchResultRedisTtl()).thenReturn(3600L);

        // ✅ Fix for your error
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ApiResponse response = service.searchCommunity(searchCriteria);
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testHandleSearchAndCache_Exception() throws Exception {
        searchCriteria.setOverrideCache(true);
        when(esUtilService.searchDocuments(any(), any())).thenThrow(new RuntimeException("Elasticsearch error"));

        CustomException exception = assertThrows(CustomException.class, () -> service.searchCommunity(searchCriteria));
        assertEquals("error while processing", exception.getMessage());
    }

    @Test
    void testFetchDataForKeys_WithValidJson() throws Exception {
        List<String> keys = List.of("ORG_1");
        List<Object> values = List.of("{\"id\":\"ORG_1\", \"name\":\"Org Name\"}");
        when(cacheService.hget(keys)).thenReturn(values);
        when(objectMapper.readValue(anyString(), eq(Object.class))).thenReturn(Map.of("id", "ORG_1"));

        List<Object> result = service.fetchDataForKeys(keys);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchDataForKeys_WithInvalidJson() throws Exception {
        List<String> keys = List.of("ORG_1");
        List<Object> values = List.of("invalid-json");
        when(cacheService.hget(keys)).thenReturn(values);
        when(objectMapper.readValue(anyString(), eq(Object.class))).thenThrow(new RuntimeException("fail"));

        List<Object> result = service.fetchDataForKeys(keys);
        assertEquals(1, result.size());
        assertNull(result.get(0));
    }

    @Test
    void testCreateErrorResponse() {
        ApiResponse response = new ApiResponse();
        service.createErrorResponse(response, "Bad input", HttpStatus.BAD_REQUEST, Constants.FAILED_CONST);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Bad input", response.getParams().getErrMsg());
        assertEquals(Constants.FAILED_CONST, response.getParams().getStatus());
    }

    @Test
    void testCategoryCreate_BlankUserId() throws JsonProcessingException {
        categoryDetails = new ObjectMapper().readTree("{\"categoryName\":\"Test\",\"description\":\"Desc\",\"departmentId\":\"D1\"}");
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn("");

        ApiResponse response = service.categoryCreate(categoryDetails, AUTH_TOKEN);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testCategoryCreate_UserRootOrgMissing() throws JsonProcessingException {
        categoryDetails = new ObjectMapper().readTree("{\"categoryName\":\"Test\",\"description\":\"Desc\",\"departmentId\":\"D1\"}");
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), eq(Arrays.asList(Constants.ROOT_ORG_ID, Constants.FIRST_NAME)), eq(2))).thenReturn(Collections.emptyList());

        ApiResponse response = service.categoryCreate(categoryDetails, AUTH_TOKEN);

        assertEquals(Constants.USER_DETAILS_NOT_FOUND, response.getParams().getErrMsg());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testCategoryCreate_PayloadValidationSuccess() throws Exception {
        categoryDetails = new ObjectMapper().readTree("{\"categoryName\":\"Test\",\"description\":\"Desc\",\"departmentId\":\"D1\"}");
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt())).thenReturn(List.of(Map.of(Constants.ROOT_ORG_ID, "org1")));
        doThrow(new CustomException()).when(payloadValidation).validatePayload(eq(Constants.CATEGORY_PAYLOAD_VALIDATION_FILE), any());

        ApiResponse response = service.categoryCreate(categoryDetails, AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_DETAILS_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testCategoryCreate_AlreadyExistsUnderParent() throws JsonProcessingException {
        categoryDetails = new ObjectMapper().readTree("{\"categoryName\":\"Test\",\"description\":\"Desc\",\"departmentId\":\"D1\"}");
        ((ObjectNode) categoryDetails).put(Constants.PARENT_ID, 1);

        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt())).thenReturn(List.of(Map.of(Constants.ROOT_ORG_ID, "org1")));
        when(categoryRepository.findByParentIdAndCategoryNameAndDepartmentIdAndIsActive(eq(1), eq("Test"), eq("D1"), eq(true))).thenReturn(new CommunityCategory());

        ApiResponse response = service.categoryCreate(categoryDetails, AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_DETAILS_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testCategoryCreate_TopLevelAlreadyExists() throws JsonProcessingException {
        categoryDetails = new ObjectMapper().readTree("{\"categoryName\":\"Test\",\"description\":\"Desc\",\"departmentId\":\"D1\"}");
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt())).thenReturn(List.of(Map.of(Constants.ROOT_ORG_ID, "org1")));
        when(categoryRepository.findByCategoryNameAndIsActive(eq("Test"), eq(true))).thenReturn(new CommunityCategory());

        ApiResponse response = service.categoryCreate(categoryDetails, AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_DETAILS_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testCategoryCreate_Success_TopLevel() throws Exception {
        categoryDetails = new ObjectMapper().readTree("{\"categoryName\":\"Test\",\"description\":\"Desc\",\"departmentId\":\"D1\"}");
        CommunityCategory savedCategory = new CommunityCategory();
        savedCategory.setCategoryId(123);
        savedCategory.setCategoryName("Test");

        when(accessTokenValidator.verifyUserToken(any())).thenReturn(USER_ID);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt())).thenReturn(List.of(Map.of(Constants.USER_ROOT_ORG_ID, "org1")));
        when(categoryRepository.findByCategoryNameAndIsActive(eq("Test"), eq(true))).thenReturn(null);
        when(categoryRepository.save(any())).thenReturn(savedCategory);
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());

        ApiResponse response = service.categoryCreate(categoryDetails, AUTH_TOKEN);

        assertEquals(Constants.SUCCESSFULLY_CREATED, response.getResult().get(Constants.STATUS));
    }

    @Test
    void testCategoryCreate_UnexpectedError() throws JsonProcessingException {
        // Mock user validation
        when(accessTokenValidator.verifyUserToken(any())).thenReturn(USER_ID);

        // Mock valid user root org id
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), anyInt())).thenReturn(List.of(Map.of(Constants.USER_ROOT_ORG_ID, "ROOT_ORG")));

        // Payload without parentId -> goes to "else" block
        categoryDetails = new ObjectMapper().readTree("{\"categoryName\":\"Test\",\"description\":\"Desc\",\"departmentId\":\"D1\"}");

        // Simulate unexpected error
        when(categoryRepository.findByCategoryNameAndIsActive(anyString(), eq(true))).thenThrow(new RuntimeException("DB error"));

        // Assert custom exception is thrown
        CustomException ex = assertThrows(CustomException.class, () -> {
            service.categoryCreate(categoryDetails, AUTH_TOKEN);
        });

        assertEquals("DB error", ex.getMessage());
    }

    @Test
    void testReadCategory_Success() {
        CommunityCategory category = new CommunityCategory();
        category.setCategoryId(101);
        category.setCategoryName("Tech");

        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        when(categoryRepository.findByCategoryIdAndIsActive(101, true)).thenReturn(category);
        when(objectMapper.convertValue(any(), any(TypeReference.class))).thenReturn(Map.of("categoryName", "Tech"));

        ApiResponse response = service.readCategory(VALID_CATEGORY_ID, AUTH_TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.COMMUNITY_DETAILS));
        assertEquals(Constants.SUCCESSFULLY_READING, response.getParams().getErrMsg());
    }

    @Test
    void testReadCategory_InvalidCategoryId_NotFound() {
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        when(categoryRepository.findByCategoryIdAndIsActive(101, true)).thenReturn(null);

        ApiResponse response = service.readCategory(VALID_CATEGORY_ID, AUTH_TOKEN);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.INVALID_CATEGORY_ID, response.getParams().getErrMsg());
    }

    @Test
    void testReadCategory_BlankUserId() {
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn("");

        ApiResponse response = service.readCategory(VALID_CATEGORY_ID, AUTH_TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testReadCategory_BlankCategoryId() {
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);

        ApiResponse response = service.readCategory("", AUTH_TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testReadCategory_ExceptionThrown() {
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        when(categoryRepository.findByCategoryIdAndIsActive(101, true)).thenThrow(new RuntimeException("DB failure"));

        CustomException exception = assertThrows(CustomException.class, () -> service.readCategory(VALID_CATEGORY_ID, AUTH_TOKEN));

        assertEquals("error while processing", exception.getMessage());
    }

    @Test
    void testDeleteCategory_Success() throws Exception {
        CommunityCategory category = new CommunityCategory();
        category.setCategoryId(101);
        category.setCategoryName("TestCat");

        JsonNode node = new ObjectMapper().valueToTree(category);

        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        when(categoryRepository.findByCategoryIdAndIsActive(eq(101), eq(true))).thenReturn(category);
        when(objectMapper.valueToTree(any())).thenReturn(node);
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(new HashMap<>());

        ApiResponse response = service.deleteCategory("101", AUTH_TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().get(Constants.RESPONSE).toString().contains("101"));
    }


    @Test
    void testDeleteCategory_InvalidCategoryId() {
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        when(categoryRepository.findByCategoryIdAndIsActive(101, true)).thenReturn(null);

        ApiResponse response = service.deleteCategory(CATEGORY_ID, AUTH_TOKEN);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.INVALID_CATEGORY_ID, response.getParams().getErrMsg());
    }

    @Test
    void testDeleteCategory_BlankUserId() {
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn("");

        ApiResponse response = service.deleteCategory(CATEGORY_ID, AUTH_TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testDeleteCategory_BlankCategoryId() {
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);

        ApiResponse response = service.deleteCategory("", AUTH_TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testDeleteCategory_ThrowsException() {
        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        when(categoryRepository.findByCategoryIdAndIsActive(eq(102), eq(true))).thenReturn(communityCategory);

        CustomException ex = assertThrows(CustomException.class, () -> {
            service.deleteCategory("102", AUTH_TOKEN);
        });

        assertEquals("error while processing", ex.getMessage());
    }

    @Test
    void testUpdateCategory_Success() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode categoryDetails = mapper.readTree("{\"categoryId\":101,\"categoryName\":\"Updated\",\"departmentId\":\"dept1\"}");

        CommunityCategory existingCategory = new CommunityCategory();
        existingCategory.setCategoryId(INTEGER_CATEGORY_ID);
        existingCategory.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        CommunityCategory updatedCategory = new CommunityCategory();
        updatedCategory.setCategoryId(INTEGER_CATEGORY_ID);
        updatedCategory.setCategoryName("Updated");

        JsonNode esNode = mapper.valueToTree(updatedCategory);

        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        doNothing().when(payloadValidation).validatePayload(anyString(), eq(categoryDetails));
        when(categoryRepository.findByCategoryIdAndIsActive(INTEGER_CATEGORY_ID, true)).thenReturn(existingCategory);
        when(objectMapper.convertValue(categoryDetails, CommunityCategory.class)).thenReturn(updatedCategory);
        when(objectMapper.valueToTree(any())).thenReturn(esNode);
        when(objectMapper.convertValue(eq(esNode), eq(Map.class))).thenReturn(new HashMap<>());

        ApiResponse response = service.updateCategory(categoryDetails, AUTH_TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().get(Constants.RESPONSE).toString().contains("Updated the category with id"));
    }

    @Test
    void testUpdateCategory_MissingUserId() throws JsonProcessingException {
        JsonNode categoryDetails = new ObjectMapper().readTree("{\"categoryId\":101}");

        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn("");

        ApiResponse response = service.updateCategory(categoryDetails, AUTH_TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testUpdateCategory_ValidationFailure() throws JsonProcessingException {
        JsonNode categoryDetails = new ObjectMapper().readTree("{\"categoryId\":101}");

        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        doThrow(new CustomException()).when(payloadValidation).validatePayload(anyString(), eq(categoryDetails));

        ApiResponse response = service.updateCategory(categoryDetails, AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateCategory_MissingCategoryId() throws JsonProcessingException {
        JsonNode categoryDetails = new ObjectMapper().readTree("{\"categoryName\":\"Updated\"}");

        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        doNothing().when(payloadValidation).validatePayload(anyString(), eq(categoryDetails));

        ApiResponse response = service.updateCategory(categoryDetails, AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.COMMUNITY_ID_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testUpdateCategory_InvalidCategoryId() throws JsonProcessingException {
        JsonNode categoryDetails = new ObjectMapper().readTree("{\"categoryId\":999}");

        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        doNothing().when(payloadValidation).validatePayload(anyString(), eq(categoryDetails));
        when(categoryRepository.findByCategoryIdAndIsActive(999, true)).thenReturn(null);  // Or mock it to return null

        ApiResponse response = service.updateCategory(categoryDetails, AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.INVALID_CATEGORY_ID, response.getParams().getErrMsg());
    }

    @Test
    void testUpdateCategory_ExceptionWhileProcessing() throws JsonProcessingException {
        JsonNode categoryDetails = new ObjectMapper().readTree("{\"categoryId\":101}");

        when(accessTokenValidator.verifyUserToken(AUTH_TOKEN)).thenReturn(USER_ID);
        doNothing().when(payloadValidation).validatePayload(anyString(), eq(categoryDetails));
        when(categoryRepository.findByCategoryIdAndIsActive(101, true)).thenReturn(new CommunityCategory());
        when(objectMapper.convertValue(categoryDetails, CommunityCategory.class)).thenThrow(new RuntimeException("Error"));

        CustomException ex = assertThrows(CustomException.class, () -> {
            service.updateCategory(categoryDetails, AUTH_TOKEN);
        });

        assertEquals("error while processing", ex.getMessage());
    }

    @Test
    void testListOfCategory_FromCache() throws Exception {
        String cachedJson = "[{\"categoryId\": 1, \"categoryName\": \"Sample\"}]";
        when(cacheService.getCache(REDIS_KEY)).thenReturn(cachedJson);
        when(objectMapper.readValue(eq(cachedJson), any(TypeReference.class))).thenReturn(List.of(Map.of("categoryId", 1)));

        ApiResponse response = service.listOfCategory();

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESSFULLY_READING, response.getParams().getErrMsg());
        assertTrue(response.getResult().containsKey(Constants.CATEGORY_DETAILS));
    }

    @Test
    void testListOfCategory_FromDB() {
        when(cacheService.getCache(REDIS_KEY)).thenReturn(null);

        CommunityCategory category = new CommunityCategory();
        category.setCategoryId(1);
        category.setCategoryName("Tech");

        List<CommunityCategory> categoryList = List.of(category);

        when(categoryRepository.findByParentIdAndIsActive(0, true)).thenReturn(categoryList);
        when(objectMapper.convertValue(eq(categoryList), any(TypeReference.class))).thenReturn(List.of(Map.of("categoryId", 1)));

        ApiResponse response = service.listOfCategory();

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.CATEGORY_DETAILS));
        verify(cacheService).putCache(eq(REDIS_KEY), any());
    }

    @Test
    void testListOfCategory_NoCategories() {
        when(cacheService.getCache(REDIS_KEY)).thenReturn(null);
        when(categoryRepository.findByParentIdAndIsActive(0, true)).thenReturn(Collections.emptyList());

        ApiResponse response = service.listOfCategory();

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.CATEGORIES_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testListOfCategory_ExceptionThrown() {
        when(cacheService.getCache(REDIS_KEY)).thenThrow(new RuntimeException("Redis failure"));

        CustomException exception = assertThrows(CustomException.class, () -> {
            service.listOfCategory();
        });

        assertEquals("error while processing", exception.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatusCode());
    }

    @Test
    void testListOfSubCategory_FromRedisCache() throws JsonProcessingException {
        validSearchCriteria = new SearchCriteria();
        Map<String, Object> filter = new HashMap<>();
        filter.put(Constants.CATEGORY_ID, 101);
        validSearchCriteria.setFilterCriteriaMap((HashMap<String, Object>) filter);

        CommunityCategory category = new CommunityCategory();
        category.setCategoryId(INTEGER_CATEGORY_ID);
        SearchResult cachedResult = new SearchResult();

        when(categoryRepository.findByCategoryIdAndIsActive(INTEGER_CATEGORY_ID, true)).thenReturn(category);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(any())).thenReturn("redisKey");
        when(valueOperations.get("redisKey")).thenReturn(cachedResult);
        when(objectMapper.convertValue(eq(category), any(TypeReference.class))).thenReturn(new HashMap<>());

        ApiResponse response = service.listOfSubCategory(validSearchCriteria);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testListOfSubCategory_InvalidCategoryId_NullFilter() {
        SearchCriteria searchCriteria = new SearchCriteria(); // no filters

        ApiResponse response = service.listOfSubCategory(searchCriteria);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.INVALID_CATEGORY_ID, response.getParams().getErrMsg());
    }

    @Test
    void testListOfSubCategory_CategoryNotFound() {
        validSearchCriteria = new SearchCriteria();
        Map<String, Object> filter = new HashMap<>();
        filter.put(Constants.CATEGORY_ID, 101); // Use Integer, not "101"
        validSearchCriteria.setFilterCriteriaMap((HashMap<String, Object>) filter);


        when(categoryRepository.findByCategoryIdAndIsActive(INTEGER_CATEGORY_ID, true)).thenReturn(null);

        ApiResponse response = service.listOfSubCategory(validSearchCriteria);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.INVALID_CATEGORY_ID, response.getParams().getErrMsg());
    }

    @Test
    void testListOfSubCategory_SearchStringTooShort() {

        validSearchCriteria = new SearchCriteria();
        Map<String, Object> filter = new HashMap<>();
        filter.put(Constants.CATEGORY_ID, 101); // Use Integer, not "101"
        validSearchCriteria.setFilterCriteriaMap((HashMap<String, Object>) filter);

        CommunityCategory category = new CommunityCategory();
        category.setCategoryId(INTEGER_CATEGORY_ID);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(categoryRepository.findByCategoryIdAndIsActive(INTEGER_CATEGORY_ID, true)).thenReturn(category);

        validSearchCriteria.setSearchString("a");

        ApiResponse response = service.listOfSubCategory(validSearchCriteria);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.MINIMUM_CHARACTERS_NEEDED, response.getParams().getErrMsg());
    }

    @Test
    void testListOfSubCategory_ElasticSearchFallback() throws Exception {
        validSearchCriteria = new SearchCriteria();
        Map<String, Object> filter = new HashMap<>();
        filter.put(Constants.CATEGORY_ID, 101); // Use Integer, not "101"
        validSearchCriteria.setFilterCriteriaMap((HashMap<String, Object>) filter);

        CommunityCategory category = new CommunityCategory();
        category.setCategoryId(INTEGER_CATEGORY_ID);
        SearchResult esResult = new SearchResult();

        when(categoryRepository.findByCategoryIdAndIsActive(INTEGER_CATEGORY_ID, true)).thenReturn(category);
        when(objectMapper.writeValueAsString(any())).thenReturn("redisKey");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("redisKey")).thenReturn(null);
        when(esUtilService.searchDocuments(any(), any())).thenReturn(esResult);
        when(cbServerProperties.getSearchResultRedisTtl()).thenReturn(60L);
        when(objectMapper.convertValue(eq(category), any(TypeReference.class))).thenReturn(new HashMap<>());

        ApiResponse response = service.listOfSubCategory(validSearchCriteria);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SUB_CATEGORIES));
    }

    @Test
    void testListOfSubCategory_ThrowsException() {
        validSearchCriteria = new SearchCriteria();
        Map<String, Object> filter = new HashMap<>();
        filter.put(Constants.CATEGORY_ID, 101); // Use Integer, not "101"
        validSearchCriteria.setFilterCriteriaMap((HashMap<String, Object>) filter);

        when(categoryRepository.findByCategoryIdAndIsActive(INTEGER_CATEGORY_ID, true)).thenThrow(new RuntimeException("DB failure"));

        CustomException exception = assertThrows(CustomException.class, () -> service.listOfSubCategory(validSearchCriteria));

        assertEquals("error while processing", exception.getMessage());
    }

    @Test
    void testLisAllCategoryWithSubCat_fromCache_success() throws Exception {
        String cachedJson = "{\"key\": \"value\"}";
        Map<String, Object> cachedMap = Map.of("key", "value");

        when(cacheService.getCache(Constants.CATEGORY_LIST_ALL_REDIS_KEY_PREFIX)).thenReturn(cachedJson);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(cachedMap);

        ApiResponse response = service.lisAllCategoryWithSubCat();

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("value", ((Map<?, ?>) response.getResult()).get("key"));
        verify(cacheService, times(1)).getCache(any());
    }

    @Test
    void testLisAllCategoryWithSubCat_exceptionThrown() throws Exception {
        when(cacheService.getCache(any())).thenThrow(new RuntimeException("Redis failure"));

        CustomException thrown = assertThrows(CustomException.class, () -> {
            service.lisAllCategoryWithSubCat();
        });

        assertEquals("error while processing", thrown.getMessage());
        assertEquals(Constants.ERROR, thrown.getCode());
    }

    @Test
    void testGetPopularCommunitiesByField_missingField() {
        // Act
        ApiResponse response = service.getPopularCommunitiesByField(new HashMap<>());

        // Assert
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Field is mandatory", response.getParams().getErrMsg());
    }

    @Test
    void testGetPopularCommunitiesByField_elasticsearchException() {
        Map<String, Object> payload = new HashMap<>();
        payload.put(Constants.FIELD, "category");

        when(esUtilService.popularCommunities(any(), any())).thenThrow(new RuntimeException("ES failed"));

        CustomException ex = assertThrows(CustomException.class, () -> service.getPopularCommunitiesByField(payload));

        assertEquals("Cannot invoke \"java.lang.Integer.intValue()\" because \"this.noOfPopularCommunities\" is null", ex.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatusCode());
    }

    @Test
    void testInvalidToken() {
        Map<String, Object> payload = new HashMap<>();
        payload.put(Constants.COMMUNITY_ID, "cid");
        payload.put(Constants.REPORTED_REASON, List.of("spam"));

        when(accessTokenValidator.verifyUserToken("token")).thenReturn(Constants.UNAUTHORIZED);

        ApiResponse response = service.report("token", payload);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testCommunityNotFound() {
        Map<String, Object> payload = new HashMap<>();
        payload.put(Constants.COMMUNITY_ID, "cid");
        payload.put(Constants.REPORTED_REASON, List.of("spam"));

        when(accessTokenValidator.verifyUserToken("token")).thenReturn("uid");
        when(communityEngagementRepository.findById("cid")).thenReturn(Optional.empty());

        ApiResponse response = service.report("token", payload);
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testCommunityInactive() {
        Map<String, Object> payload = Map.of(Constants.COMMUNITY_ID, "cid", Constants.REPORTED_REASON, List.of("spam"));

        CommunityEntity community = new CommunityEntity();
        community.setActive(false);

        when(accessTokenValidator.verifyUserToken("token")).thenReturn("uid");
        when(communityEngagementRepository.findById("cid")).thenReturn(Optional.of(community));

        ApiResponse response = service.report("token", payload);
        assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    }

    @Test
    void testCommunitySuspended() {
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(Constants.STATUS, Constants.SUSPENDED);

        CommunityEntity community = new CommunityEntity();
        community.setActive(true);
        community.setData(data);

        when(accessTokenValidator.verifyUserToken("token")).thenReturn("uid");
        when(communityEngagementRepository.findById("cid")).thenReturn(Optional.of(community));

        Map<String, Object> payload = Map.of(Constants.COMMUNITY_ID, "cid", Constants.REPORTED_REASON, List.of("spam"));

        ApiResponse response = service.report("token", payload);
        assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    }

    @Test
    void testDuplicateReport() {
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(Constants.STATUS, Constants.ACTIVE);

        CommunityEntity community = new CommunityEntity();
        community.setActive(true);
        community.setData(data);

        Map<String, Object> payload = Map.of(Constants.COMMUNITY_ID, "cid", Constants.REPORTED_REASON, List.of("spam"));

        when(accessTokenValidator.verifyUserToken("token")).thenReturn("uid");
        when(communityEngagementRepository.findById("cid")).thenReturn(Optional.of(community));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any())).thenReturn(List.of(Map.of()));  // Simulates user already reported

        ApiResponse response = service.report("token", payload);
        assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    }

    @Test
    void testSuccessfulReport(){
        // Mock ObjectMapper behavior
        ObjectNode jsonNode = mock(ObjectNode.class);
        ObjectNode dataNode = mock(ObjectNode.class);
        ArrayNode reportedByArray = mock(ArrayNode.class);
        JsonNode statusNode = mock(JsonNode.class);

        when(objectMapper.createObjectNode()).thenReturn(jsonNode);
        when(jsonNode.setAll(any(ObjectNode.class))).thenReturn(jsonNode);
        when(objectMapper.convertValue(any(ObjectNode.class), eq(Map.class))).thenReturn(new HashMap<>());

        // new line: mock array node creation
        when(objectMapper.createArrayNode()).thenReturn(reportedByArray);
        when(reportedByArray.add(anyString())).thenReturn(reportedByArray); // allow .add(userId)

        // Setup status node on data
        when(dataNode.get(Constants.STATUS)).thenReturn(statusNode);
        when(statusNode.textValue()).thenReturn(Constants.REPORTED);

        // Allow set(REPORTED_BY, ...)
        when(dataNode.set(eq(Constants.REPORTED_BY), eq(reportedByArray))).thenReturn(dataNode);

        // Setup community
        CommunityEntity community = new CommunityEntity();
        community.setActive(true);
        community.setData(dataNode);

        Map<String, Object> payload = Map.of(Constants.COMMUNITY_ID, "cid", Constants.REPORTED_REASON, List.of("spam"));

        when(accessTokenValidator.verifyUserToken("token")).thenReturn("uid");
        when(communityEngagementRepository.findById("cid")).thenReturn(Optional.of(community));

        // No prior report
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), eq(Constants.USER_REPORTED_COMMUNITY), any(), any(), any())).thenReturn(List.of());

        // 2 reports exist
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), eq(Constants.COMMUNITY_REPORTED_BY_USER), any(), any(), any())).thenReturn(List.of(new HashMap<>(), new HashMap<>()));

        ApiResponse response = service.report("token", payload);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.COMMUNITY_ID));
    }

    @Test
    void testExceptionHandling() {
        Map<String, Object> payload = Map.of(Constants.COMMUNITY_ID, "cid", Constants.REPORTED_REASON, List.of("spam"));

        when(accessTokenValidator.verifyUserToken("token")).thenReturn("uid");
        when(communityEngagementRepository.findById("cid")).thenThrow(new RuntimeException("DB failure"));

        ApiResponse response = service.report("token", payload);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testUploadFile_EmptyFile() {
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.isEmpty()).thenReturn(true);

        ApiResponse response = service.uploadFile(multipartFile, "communityId");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.COMMUNITY_FILE_EMPTY, response.getParams().getErr());
    }

    @Test
    void testUploadFile_BlankCommunityId() {
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.isEmpty()).thenReturn(false);

        ApiResponse response = service.uploadFile(multipartFile, " ");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErr());
    }

    @Test
    void testUploadFile_Success() throws Exception {
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("test.txt");
        when(multipartFile.getBytes()).thenReturn("Sample content".getBytes());

        File tempFile = new File(System.currentTimeMillis() + "_test.txt");
        tempFile.createNewFile();

        Field field = CommunityManagementServiceImpl.class.getDeclaredField("storageService");
        field.setAccessible(true);
        field.set(service, mockStorageService);

        when(cbServerProperties.getDiscussionCloudFolderName()).thenReturn("folder");
        when(cbServerProperties.getDiscussionContainerName()).thenReturn("container");
        when(mockStorageService.upload(eq("testContainer"), anyString(), contains("sample.txt"), any(), any(), any(), any())).thenReturn("http://mocked.url");

        ApiResponse response = service.uploadFile(multipartFile, "communityId");

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testUploadFile_Exception() throws Exception {
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("test.txt");
        when(multipartFile.getBytes()).thenThrow(new IOException("Simulated IO error"));

        ApiResponse response = service.uploadFile(multipartFile, "communityId");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("Simulated IO error"));
    }

    @Test
    void testUploadFileToCloud_Success() throws IllegalAccessException, NoSuchFieldException {
        File file = mock(File.class);
        when(file.getName()).thenReturn("file.txt");
        when(file.getAbsolutePath()).thenReturn("/tmp/file.txt");

        mockStorageService = mock(BaseStorageService.class);
        Field field = CommunityManagementServiceImpl.class.getDeclaredField("storageService");
        field.setAccessible(true);
        field.set(service, mockStorageService);

        when(mockStorageService.upload(eq("testContainer"), anyString(), contains("sample.txt"), any(), any(), any(), any())).thenReturn("http://mocked.url");

        ApiResponse response = service.uploadFile(file, "folder", "container");

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testUploadFileToCloud_Exception() throws NoSuchFieldException, IllegalAccessException {
        // Mock the File object
        File file = mock(File.class);
        when(file.getName()).thenReturn("file.txt");
        when(file.getAbsolutePath()).thenReturn("/tmp/file.txt");

        // Inject mock storageService into the service
        BaseStorageService mockStorageService = mock(BaseStorageService.class);
        Field field = CommunityManagementServiceImpl.class.getDeclaredField("storageService");
        field.setAccessible(true);
        field.set(service, mockStorageService);

        // Mock upload to throw exception
        when(mockStorageService.upload(eq("container"), // must match this string exactly
                anyString(), contains("file.txt"), any(), any(), any(), any())).thenThrow(new RuntimeException("Simulated cloud error"));

        // Call method
        ApiResponse response = service.uploadFile(file, "folder", "container");

        // Verify result
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Simulated cloud error"));
    }


    @Test
    void testSearchTopic_FromRedisCache() throws JsonProcessingException {
        searchCriteria = new SearchCriteria();
        searchCriteria.setSearchString("test");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        SearchResult mockResult = new SearchResult();
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        when(valueOperations.get(anyString())).thenReturn(mockResult);

        ApiResponse response = service.searchTopic(searchCriteria);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.SEARCH_RESULTS));
    }

    @Test
    void testSearchTopic_InvalidSearchStringTooShort() {
        searchCriteria.setSearchString("a");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ApiResponse response = service.searchTopic(searchCriteria);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED_CONST, response.getParams().getStatus());
    }

    @Test
    void testGenerateRedisJwtTokenKey_JsonProcessingException() throws JsonProcessingException {
        SearchCriteria criteria = new SearchCriteria();
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("test") {
        });

        // Accessing private method via reflection for test completeness
        String key = ReflectionTestUtils.invokeMethod(service, "generateRedisJwtTokenKey", criteria);
        assertEquals("", key);
    }

    @Test
    void testSearchTopic_ExceptionFromES() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        when(valueOperations.get(anyString())).thenReturn(null);
        when(esUtilService.searchDocuments(anyString(), any())).thenThrow(new RuntimeException("ES Error"));

        CustomException ex = assertThrows(CustomException.class, () -> service.searchTopic(searchCriteria));
        assertEquals("error while processing", ex.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatusCode());
    }

    @Test
    void testListAllCommunitiesJoinedByUser_Success_CacheHit() throws Exception {
        String authToken = "token";
        String userId = "user-123";
        String communityId = "community-001";
        String cachedJson = "{\"communityName\": \"Test Community\", \"communityId\": \"community-001\"}";

        Map<String, Object> record = new HashMap<>();
        record.put("status", true);
        record.put("communityid", communityId);

        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), anyMap(), anyList(), isNull())).thenReturn(Collections.singletonList(record));
        when(cacheService.getCache(communityId)).thenReturn(cachedJson);

        Map<String, Object> communityMap = new HashMap<>();
        communityMap.put("communityName", "Test Community");
        communityMap.put("communityId", communityId);
        when(objectMapper.readValue(eq(cachedJson), any(TypeReference.class))).thenReturn(communityMap);

        ApiResponse response = service.listAllCommunitiesJoinedByUser(authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testListAllCommunitiesJoinedByUser_Success_DBHit() throws Exception {
        String authToken = "token";
        String userId = "user-123";
        String communityId = "community-002";

        Map<String, Object> record = new HashMap<>();
        record.put("status", true);
        record.put("communityid", communityId);

        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), anyMap(), anyList(), isNull())).thenReturn(Collections.singletonList(record));
        when(cacheService.getCache(communityId)).thenReturn(null);

        CommunityEntity communityEntity = mock(CommunityEntity.class);

        ObjectMapper realObjectMapper = new ObjectMapper();
        JsonNode communityDataNode = realObjectMapper.readTree("{\"communityName\": \"Test Community DB\", \"communityId\": \"" + communityId + "\"}");
        Map<String, Object> communityMap = new HashMap<>();
        communityMap.put("communityName", "Test Community DB");
        communityMap.put("communityId", communityId);

        when(communityEntity.getData()).thenReturn(communityDataNode);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true)).thenReturn(Optional.of(communityEntity));
        when(objectMapper.convertValue(eq(communityDataNode), any(TypeReference.class))).thenReturn(communityMap);

        ApiResponse response = service.listAllCommunitiesJoinedByUser(authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testListAllCommunitiesJoinedByUser_InvalidUser() {
        String authToken = "invalidToken";

        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn("");

        ApiResponse response = service.listAllCommunitiesJoinedByUser(authToken);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testListAllCommunitiesJoinedByUser_StatusFalse() {
        String authToken = "token";
        String userId = "user-123";

        Map<String, Object> record = new HashMap<>();
        record.put("status", false);
        record.put("communityid", "community-001");

        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), anyMap(), anyList(), isNull())).thenReturn(Collections.singletonList(record));

        ApiResponse response = service.listAllCommunitiesJoinedByUser(authToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testListAllCommunitiesJoinedByUser_Exception() {
        String authToken = "token";

        when(accessTokenValidator.verifyUserToken(authToken)).thenThrow(new RuntimeException("Something went wrong"));

        CustomException exception = assertThrows(CustomException.class, () -> service.listAllCommunitiesJoinedByUser(authToken));

        assertEquals("error while processing", exception.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatusCode());
    }

    @Test
    void testListAllCommunitiesJoinedByUser_CommunityMissingFields() throws Exception {
        String authToken = "token";
        String userId = "user-123";
        String communityId = "community-001";
        String cachedJson = "{\"otherField\": \"value\"}";

        Map<String, Object> record = new HashMap<>();
        record.put("status", true);
        record.put("communityid", communityId);

        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), anyMap(), anyList(), isNull())).thenReturn(Collections.singletonList(record));
        when(cacheService.getCache(communityId)).thenReturn(cachedJson);

        Map<String, Object> incompleteMap = new HashMap<>();
        incompleteMap.put("otherField", "value");

        when(objectMapper.readValue(eq(cachedJson), any(TypeReference.class))).thenReturn(incompleteMap);

        ApiResponse response = service.listAllCommunitiesJoinedByUser(authToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());

    }

    @Test
    void testPublish_UserIdBlank() {
        when(accessTokenValidator.verifyUserToken("token")).thenReturn("");
        ApiResponse response = service.publish(mock(JsonNode.class), "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testPublish_PayloadValidationFailure() {
        JsonNode input = mock(JsonNode.class);
        when(accessTokenValidator.verifyUserToken("token")).thenReturn("user123");
        doThrow(new CustomException("VALIDATION", "Invalid", HttpStatus.BAD_REQUEST)).when(payloadValidation).validatePayload(anyString(), eq(input));

        ApiResponse response = service.publish(input, "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid", response.getParams().getErrMsg());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testPublish_DuplicateCommunityDetected() throws Exception {
        JsonNode input = mock(JsonNode.class);
        when(accessTokenValidator.verifyUserToken("token")).thenReturn("user123");
        when(input.get(Constants.ORG_ID)).thenReturn(createTextNode("org1"));
        when(input.get(Constants.COMMUNITY_NAME)).thenReturn(createTextNode("Test Community"));
        when(input.get(Constants.COMMUNITY_ID)).thenReturn(createTextNode("cid123"));
        doNothing().when(payloadValidation).validatePayload(anyString(), eq(input));
        when(esUtilService.isDuplicateCommunity("org1", "Test Community", "cid123")).thenReturn(true);

        ApiResponse response = service.publish(input, "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.CREATE_ERROR_MSG_WITHIN_COMMUNITY, response.getParams().getErrMsg());
        assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    }

    @Test
    void testPublish_NameExistsInES_ReturnsPreconditionFailed() {
        JsonNode input = mock(JsonNode.class);
        when(accessTokenValidator.verifyUserToken("token")).thenReturn("user123");
        when(input.get(Constants.ORG_ID)).thenReturn(createTextNode("org1"));
        when(input.get(Constants.COMMUNITY_NAME)).thenReturn(createTextNode("Test"));
        when(input.get(Constants.COMMUNITY_ID)).thenReturn(createTextNode("cid123"));
        when(input.has(Constants.CommunityCreationAllowed)).thenReturn(true);
        when(input.get(Constants.CommunityCreationAllowed)).thenReturn(BooleanNode.FALSE);

        doNothing().when(payloadValidation).validatePayload(anyString(), eq(input));
        when(esUtilService.isDuplicateCommunity(any(), any(), any())).thenReturn(false);
        when(esUtilService.doesCommunityNameExistForPublish("Test", "cid123")).thenReturn(true);

        ApiResponse response = service.publish(input, "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.CREATE_ERROR_MSG_COMMUNITY, response.getParams().getErrMsg());
        assertEquals(HttpStatus.PRECONDITION_FAILED, response.getResponseCode());
    }

    @Test
    void testPublish_CommunityIdNotFound() {
        JsonNode input = getValidInputJson();
        when(accessTokenValidator.verifyUserToken("token")).thenReturn("user123");
        doNothing().when(payloadValidation).validatePayload(anyString(), any());
        when(esUtilService.isDuplicateCommunity(any(), any(), any())).thenReturn(false);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(any(), anyBoolean())).thenReturn(Optional.empty());

        ApiResponse response = service.publish(input, "token");

        assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErrMsg());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testPublish_Success_WithModerators() {
        JsonNode input = getValidInputJson();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode dataNode = mapper.createObjectNode();

        ArrayNode moderators = mapper.createArrayNode();
        ObjectNode moderator = mapper.createObjectNode();
        moderator.put(Constants.MODERATOR_ID, "mod123");
        moderators.add(moderator);
        dataNode.set(Constants.MODERATORS, moderators);

        CommunityEntity community = new CommunityEntity();
        community.setCommunityId("cid123");
        community.setData(dataNode);
        community.setUpdatedOn(new Timestamp(System.currentTimeMillis()));

        when(accessTokenValidator.verifyUserToken("token")).thenReturn("user123");
        doNothing().when(payloadValidation).validatePayload(anyString(), any());
        when(esUtilService.isDuplicateCommunity(any(), any(), any())).thenReturn(false);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(any(), anyBoolean())).thenReturn(Optional.of(community));

        ApiResponse response = service.publish(input, "token");

        assertEquals("Published the community with id: cid123", response.getResult().get(Constants.RESPONSE));
    }


    @Test
    void testSearchCommunityFromPrimary_withShortSearchString() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("a");
        ApiResponse response = service.searchCommunityFromPrimary(criteria);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED_CONST, response.getParams().getStatus());
        assertEquals(Constants.MINIMUM_CHARACTERS_NEEDED, response.getParams().getErrMsg());
    }

    @Test
    void testSearchCommunityFromPrimary_withValidCriteria_success() throws Exception {
        // Arrange
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("valid");

        // Mock Elasticsearch result
        JsonNode esResultData = new ObjectMapper().readTree("[{\"createdBy\":\"user1\"}]");
        SearchResult mockResult = new SearchResult();
        mockResult.setData(esResultData);
        when(esUtilService.searchDocuments(any(), any())).thenReturn(mockResult);

        // Mock cache data
        List<Object> cachedValues = List.of("{\"designation\":\"Manager\"}");
        when(cacheService.hget(anyList())).thenReturn(cachedValues);
        when(objectMapper.readValue(eq("{\"designation\":\"Manager\"}"), eq(Object.class))).thenReturn(new HashMap<>(Map.of("designation", "Manager")));

        // Act
        ApiResponse response = service.searchCommunityFromPrimary(criteria);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.SEARCH_RESULTS));
    }


    @Test
    void testSearchCommunityFromPrimary_exceptionThrown() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("valid");

        when(esUtilService.searchDocuments(any(), any())).thenThrow(new RuntimeException("Search failed"));

        CustomException ex = assertThrows(CustomException.class, () -> service.searchCommunityFromPrimary(criteria));

        assertEquals("error while processing", ex.getMessage());
    }

    @Test
    void testFetchDataForKeys_withValidJson() throws Exception {
        List<String> keys = List.of("user1");
        when(cacheService.hget(keys)).thenReturn(List.of("{\"designation\":\"Manager\"}"));
        when(objectMapper.readValue(anyString(), eq(Object.class))).thenReturn(Map.of("designation", "Manager"));

        List<Object> result = service.fetchDataForKeys(keys);

        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof Map);
    }

    @Test
    void testFetchDataForKeys_withInvalidJson(){
        List<String> keys = List.of("user1");
        when(cacheService.hget(keys)).thenReturn(List.of("{invalidJson}"));

        List<Object> result = service.fetchDataForKeys(keys);

        assertEquals(1, result.size());
        assertNull(result.get(0));
    }

    @Test
    void testCreateErrorResponse_searchCommunityFromPrimary() {
        ApiResponse response = new ApiResponse();
        service.createErrorResponse(response, "Error Occurred", HttpStatus.BAD_REQUEST, "FAILED");

        assertEquals("Error Occurred", response.getParams().getErrMsg());
        assertEquals("FAILED", response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testSyncUserWithCommunity_validCsv_success() throws Exception {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("users.csv");
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

        List<Map<String, String>> processedData = List.of(Map.of("status", "true", "userId", "user123", "communityId", "community456"));

        @NotNull List<HashMap<String, String>> convertedData = processedData.stream().map(map -> new HashMap<>(map)).toList();

        when(fileProcessService.processCsvAndSendMessage(any())).thenReturn(processedData);
        when(objectMapper.convertValue(eq(processedData), any(TypeReference.class))).thenReturn(convertedData);

        ApiResponse response = service.syncUserWithCommunity(mockFile);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals("User sync completed successfully.", response.getParams().getErrMsg());

    }


    @Test
    void testSyncUserWithCommunity_invalidFileExtension_throwsException() {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("file.txt");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.syncUserWithCommunity(mockFile);
        });

        assertEquals("error while processing", exception.getMessage());
    }

    @Test
    void testSyncUserWithCommunity_nullFileName_throwsException() {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.syncUserWithCommunity(mockFile);
        });

        assertEquals("error while processing", exception.getMessage());
    }

    @Test
    void testSyncUserWithCommunity_fileIOException_throwsException() throws IOException {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("users.csv");
        when(mockFile.getInputStream()).thenThrow(new IOException("File read error"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.syncUserWithCommunity(mockFile);
        });

        assertEquals("error while processing", exception.getMessage());
    }

    @Test
    void testSyncUserWithCommunity_exceptionDuringProcessing_throwsCustomException() throws Exception {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("users.csv");
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

        when(fileProcessService.processCsvAndSendMessage(any())).thenThrow(new RuntimeException("Unexpected error"));

        CustomException exception = assertThrows(CustomException.class, () -> {
            service.syncUserWithCommunity(mockFile);
        });

        assertEquals("error while processing", exception.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatusCode());
    }

    @Test
    void testSyncUserWithCommunity_validCsv_skippedRecord() throws Exception {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("users.csv");
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

        // Original processed data returned by the file processing service
        Map<String, String> record = new HashMap<>();
        record.put("status", "false"); // this will skip update
        record.put("userId", "user123");
        record.put("communityId", "community456");
        List<Map<String, String>> processedData = List.of(record);

        // Converted record returned by ObjectMapper
        Map<String, Object> convertedRecord = new HashMap<>(record);
        List<Map<String, Object>> convertedData = List.of(convertedRecord);

        // Mocks
        when(fileProcessService.processCsvAndSendMessage(any())).thenReturn(processedData);
        when(objectMapper.convertValue(eq(processedData), any(TypeReference.class))).thenReturn(convertedData);

        ApiResponse response = service.syncUserWithCommunity(mockFile);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(esUtilService, never()).updateUserIndex(any(), any(), anyBoolean()); // should skip
    }

    @Test
    void testRead_emptyCommunityId() {
        ApiResponse response = service.read("");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testRead_dataFromRedis() throws Exception {
        String communityId = "community123";
        String jsonData = "{\"key\":\"value\"}";

        when(cacheService.getCache(communityId)).thenReturn(jsonData);
        when(objectMapper.readValue(eq(jsonData), any(TypeReference.class))).thenReturn(Map.of("key", "value"));

        ApiResponse response = service.read(communityId);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESSFULLY_READING, response.getParams().getErrMsg());
        assertTrue(response.getResult().containsKey(Constants.COMMUNITY_DETAILS));
    }

    @Test
    void testRead_dataFromDatabase() throws Exception {
        String communityId = "community123";

        when(cacheService.getCache(communityId)).thenReturn(null);

        // Create actual JsonNode from Map
        Map<String, Object> mapData = Map.of("field", "value");
        JsonNode jsonNode = new ObjectMapper().valueToTree(mapData);

        CommunityEntity entity = new CommunityEntity();
        entity.setCommunityId(communityId);
        entity.setData(jsonNode);  // ✅ Correct JsonNode

        when(communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true)).thenReturn(Optional.of(entity));
        when(objectMapper.convertValue(eq(jsonNode), any(TypeReference.class))).thenReturn(mapData);

        ApiResponse response = service.read(communityId);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESSFULLY_READING, response.getParams().getErrMsg());
        assertTrue(response.getResult().containsKey(Constants.COMMUNITY_DETAILS));
        verify(cacheService).putCache(eq(communityId), eq(jsonNode)); // ✅ cache original JsonNode
    }

    @Test
    void testRead_communityNotFound() {
        String communityId = "invalidCommunity";

        when(cacheService.getCache(communityId)).thenReturn(null);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true)).thenReturn(Optional.empty());

        ApiResponse response = service.read(communityId);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErrMsg());
    }

    @Test
    void testRead_jsonMappingException() throws Exception {
        String communityId = "community123";
        String jsonData = "{\"key\":\"value\"}";

        when(cacheService.getCache(communityId)).thenReturn(jsonData);
        when(objectMapper.readValue(eq(jsonData), any(TypeReference.class))).thenThrow(new RuntimeException("Mapping failed"));

        CustomException ex = assertThrows(CustomException.class, () -> service.read(communityId));
        assertEquals("error while processing", ex.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatusCode());
    }

    @Test
    void testEnrichOrgInfo_withPartialRedisHit_shouldFetchFromCassandra() throws Exception {
        // Arrange
        SearchCriteria criteria = new SearchCriteria();
        SearchResult searchResult = new SearchResult();

        Set<String> uniqueOrgIds = new HashSet<>(List.of("org1", "org2"));
        List<String> orgIdList = List.of("org1", "org2");

        // Return JSON strings as Redis would

        String redisJson = new ObjectMapper().writeValueAsString(Map.of(Constants.ID, "org1", Constants.ORG_NAME, "Redis Org"));
        List<Object> redisResults = Arrays.asList(redisJson, null); // Use Arrays.asList here

        when(cacheService.hget(anyList())).thenReturn(redisResults);

        // Cassandra returns info for org2
        Map<String, Object> cassandraOrgInfo = new HashMap<>();
        cassandraOrgInfo.put(Constants.ID, "org2");
        cassandraOrgInfo.put(Constants.ORG_NAME, "Cassandra Org");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_ORGANISATION), anyMap(), anyList(), isNull())).thenReturn(List.of(cassandraOrgInfo));

        // Act - invoke private method via reflection
        Method method = CommunityManagementServiceImpl.class.getDeclaredMethod("enrichOrgInfo", SearchCriteria.class, SearchResult.class, Set.class, List.class);
        method.setAccessible(true);
        method.invoke(service, criteria, searchResult, uniqueOrgIds, orgIdList);

        // Assert
        List<Map<String, Object>> additionalInfo = searchResult.getAdditionalInfo();
        assertNotNull(additionalInfo);
        assertEquals(1, additionalInfo.size());

    }


    private JsonNode createTextNode(String value) {
        return new TextNode(value);
    }

    private JsonNode getValidInputJson() {
        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put(Constants.ORG_ID, "org1");
        node.put(Constants.COMMUNITY_NAME, "Test Community");
        node.put(Constants.COMMUNITY_ID, "cid123");
        return node;
    }


}



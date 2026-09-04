package com.igot.cb.community.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.FileProcessService;
import com.igot.cb.pores.util.PayloadValidation;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Covers the highest-risk public methods of CommunityManagementServiceImpl: create, read,
 * update, delete, joinCommunity, unJoinCommunity, communitiesJoinedByUser, searchCommunity,
 * categoryCreate, readCategory, updateCategory, deleteCategory, listOfCategory, publish,
 * adminJoinCommunity, adminUnjoinCommunity, listOfUsersJoined, listOfSubCategory,
 * lisAllCategoryWithSubCat, report, uploadFile, searchTopic, listAllCommunitiesJoinedByUser,
 * searchCommunityFromPrimary and syncUserWithCommunity.
 *
 * getPopularCommunitiesByField is intentionally not covered: it depends on building a real
 * co.elastic.clients SearchRequest/SearchResponse tree that isn't worth hand-rolling for a unit
 * test. uploadFile's cloud-storage success branch is skipped because storageService is only
 * assigned in @PostConstruct, which never runs in a plain Mockito unit test; the tests below
 * instead exercise the validation branches and the (storageService == null) failure branch that
 * production code already guards with its own try/catch.
 */
@ExtendWith(MockitoExtension.class)
class CommunityManagementServiceImplTest {

  @Mock
  private EsUtilService esUtilService;
  @Mock
  private CacheService cacheService;
  @Mock
  private PayloadValidation payloadValidation;
  @Mock
  private CommunityEngagementRepository communityEngagementRepository;
  @Mock
  private AccessTokenValidator accessTokenValidator;
  @Mock
  private CassandraOperation cassandraOperation;
  @Mock
  private RedisTemplate<String, SearchResult> searchResultRedisTemplate;
  @Mock
  private ValueOperations<String, SearchResult> searchResultValueOperations;
  @Mock
  private CommunityCategoryRepository categoryRepository;
  @Mock
  private Producer producer;
  @Mock
  private RedisTemplate<String, Object> objectRedisTemplate;
  @Mock
  private UserService userService;
  @Mock
  private NotificationService notificationService;
  @Mock
  private FileProcessService fileProcessService;

  private CommunityManagementServiceImpl service;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    service = new CommunityManagementServiceImpl();
    ReflectionTestUtils.setField(service, "esUtilService", esUtilService);
    ReflectionTestUtils.setField(service, "cacheService", cacheService);
    ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    CbServerProperties props = new CbServerProperties();
    props.setElasticCommunityJsonPath("/es/community.json");
    props.setElasticCommunityCategoryJsonPath("/es/category.json");
    props.setReporCommunityUserLimit(5);
    props.setSearchResultRedisTtl(600L);
    props.setCommunityAdminJoinMaxUser(50);
    props.setDiscussionCloudFolderName("discussion");
    props.setDiscussionContainerName("container");
    ReflectionTestUtils.setField(service, "cbServerProperties", props);
    ReflectionTestUtils.setField(service, "payloadValidation", payloadValidation);
    ReflectionTestUtils.setField(service, "communityEngagementRepository", communityEngagementRepository);
    ReflectionTestUtils.setField(service, "accessTokenValidator", accessTokenValidator);
    ReflectionTestUtils.setField(service, "cassandraOperation", cassandraOperation);
    ReflectionTestUtils.setField(service, "redisTemplate", searchResultRedisTemplate);
    ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
    ReflectionTestUtils.setField(service, "producer", producer);
    ReflectionTestUtils.setField(service, "userCountUpdateTopic", "community.user.count.topic");
    ReflectionTestUtils.setField(service, "noOfPopularCommunities", 10);
    ReflectionTestUtils.setField(service, "communityCategoryIndex", "community-category");
    ReflectionTestUtils.setField(service, "communityIndex", "community");
    ReflectionTestUtils.setField(service, "objectRedisTemplate", objectRedisTemplate);
    ReflectionTestUtils.setField(service, "userService", userService);
    ReflectionTestUtils.setField(service, "notificationService", notificationService);
    ReflectionTestUtils.setField(service, "fileProcessService", fileProcessService);
  }

  // ---------- helpers ----------

  private ObjectNode communityCreatePayload(String communityName, int topicId) {
    ObjectNode node = mapper.createObjectNode();
    node.put(Constants.COMMUNITY_NAME, communityName);
    node.put(Constants.TOPIC_ID, topicId);
    return node;
  }

  private ObjectNode communityData(String communityId, int topicId) {
    ObjectNode node = mapper.createObjectNode();
    node.put(Constants.COMMUNITY_ID, communityId);
    node.put(Constants.TOPIC_ID, topicId);
    node.put(Constants.COMMUNITY_NAME, "Tech Community");
    node.put(Constants.ORG_ID, "org1");
    return node;
  }

  private CommunityEntity communityEntity(String communityId, ObjectNode data) {
    CommunityEntity entity = new CommunityEntity();
    entity.setCommunityId(communityId);
    entity.setData(data);
    entity.setActive(true);
    return entity;
  }

  private CommunityCategory activeCategory(int id) {
    CommunityCategory category = new CommunityCategory();
    category.setCategoryId(id);
    category.setCategoryName("Tech");
    category.setIsActive(true);
    category.setCountOfCommunities(2L);
    return category;
  }

  private void mockUserOrgLookup(String rootOrgId) {
    Map<String, Object> row = new HashMap<>();
    row.put(Constants.USER_ROOT_ORG_ID, rootOrgId);
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), any()))
        .thenReturn(List.of(row));
  }

  private ObjectNode categoryPayload(String name, String description) {
    ObjectNode node = mapper.createObjectNode();
    node.put(Constants.CATEGORY_NAME, name);
    node.put(Constants.DESCRIPTION, description);
    return node;
  }

  // ================= create =================

  @Test
  void create_blankUserId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.create(communityCreatePayload("Tech", 1), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
  }

  @Test
  void create_validationFails_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    doThrow(new CustomException("code", "bad payload", HttpStatus.BAD_REQUEST))
        .when(payloadValidation).validatePayload(eq(Constants.PAYLOAD_VALIDATION_FILE), any());

    ApiResponse response = service.create(communityCreatePayload("Tech", 1), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals("bad payload", response.getParams().getErrMsg());
  }

  @Test
  void create_topicInactive_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true))).thenReturn(null);

    ApiResponse response = service.create(communityCreatePayload("Tech", 1), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.TOPIC_IS_INACTIVE, response.getParams().getErrMsg());
  }

  @Test
  void create_userDetailsNotFound_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true)))
        .thenReturn(activeCategory(1));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), any()))
        .thenReturn(Collections.emptyList());

    ApiResponse response = service.create(communityCreatePayload("Tech", 1), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_DETAILS_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void create_communityAlreadyExistsInOrg_returnsConflict() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true)))
        .thenReturn(activeCategory(1));
    mockUserOrgLookup("org1");
    when(esUtilService.doesCommunityExist(eq("org1"), eq("Tech"))).thenReturn(true);

    ApiResponse response = service.create(communityCreatePayload("Tech", 1), "token");

    assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    assertEquals(Constants.CREATE_ERROR_MSG_WITHIN_COMMUNITY, response.getParams().getErrMsg());
  }

  @Test
  void create_communityNameExistsAndNotAllowed_returnsPreconditionFailed() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true)))
        .thenReturn(activeCategory(1));
    mockUserOrgLookup("org1");
    when(esUtilService.doesCommunityExist(anyString(), anyString())).thenReturn(false);
    when(esUtilService.doesCommunityNameExist(eq("Tech"))).thenReturn(true);

    ApiResponse response = service.create(communityCreatePayload("Tech", 1), "token");

    assertEquals(HttpStatus.PRECONDITION_FAILED, response.getResponseCode());
    assertEquals(Constants.CREATE_ERROR_MSG_COMMUNITY, response.getParams().getErrMsg());
  }

  @Test
  void create_orgDetailsNotFound_returnsNotFound() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true)))
        .thenReturn(activeCategory(1));
    mockUserOrgLookup("org1");
    when(esUtilService.doesCommunityExist(anyString(), anyString())).thenReturn(false);
    when(esUtilService.doesCommunityNameExist(anyString())).thenReturn(false);
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), anyMap(), any(), any()))
        .thenReturn(Collections.emptyList());

    ApiResponse response = service.create(communityCreatePayload("Tech", 1), "token");

    assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    assertEquals(Constants.ORG_DETAILS_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void create_success_returnsCreatedResponse() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true)))
        .thenReturn(activeCategory(1));
    mockUserOrgLookup("org1");
    when(esUtilService.doesCommunityExist(anyString(), anyString())).thenReturn(false);
    when(esUtilService.doesCommunityNameExist(anyString())).thenReturn(false);
    Map<String, Object> orgRow = new HashMap<>();
    orgRow.put(Constants.ORG_NAME, "Org One");
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), anyMap(), any(), any()))
        .thenReturn(List.of(orgRow));
    when(communityEngagementRepository.save(any(CommunityEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ApiResponse response = service.create(communityCreatePayload("Tech", 1), "token");

    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(Constants.SUCCESSFULLY_CREATED, response.getResult().get(Constants.STATUS));
    assertNotNull(response.getResult().get(Constants.COMMUNITY_ID));
    verify(esUtilService).addDocument(eq("community"), eq(Constants.INDEX_TYPE), anyString(), anyMap(), anyString());
    verify(cacheService).putCache(anyString(), anyMap());
  }

  @Test
  void create_repositoryThrows_throwsCustomException() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true)))
        .thenReturn(activeCategory(1));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), any()))
        .thenThrow(new RuntimeException("cassandra down"));

    assertThrows(CustomException.class, () -> service.create(communityCreatePayload("Tech", 1), "token"));
  }

  // ================= read(communityId, authToken) =================

  @Test
  void read_blankUserId_returnsInternalServerError() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.read("c1", "token");

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void read_emptyCommunityId_returnsInternalServerError() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");

    ApiResponse response = service.read("", "token");

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void read_cacheHit_returnsCachedCommunity() throws Exception {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cacheService.getCache("c1")).thenReturn("{\"communityId\":\"c1\"}");

    ApiResponse response = service.read("c1", "token");

    assertEquals(Constants.SUCCESSFULLY_READING, response.getParams().getErrMsg());
    assertNotNull(response.getResult().get(Constants.COMMUNITY_DETAILS));
    verify(communityEngagementRepository, never()).findByCommunityIdAndIsActive(anyString(), anyBoolean());
  }

  @Test
  void read_cacheMissDbHit_returnsCommunityAndPopulatesCache() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cacheService.getCache("c1")).thenReturn(null);
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));

    ApiResponse response = service.read("c1", "token");

    assertEquals(Constants.SUCCESSFULLY_READING, response.getParams().getErrMsg());
    assertNotNull(response.getResult().get(Constants.COMMUNITY_DETAILS));
    verify(cacheService).putCache(eq("c1"), any());
  }

  @Test
  void read_communityNotFound_returnsNotFound() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cacheService.getCache("c1")).thenReturn(null);
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.empty());

    ApiResponse response = service.read("c1", "token");

    assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErrMsg());
  }

  @Test
  void read_cacheThrows_throwsCustomException() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cacheService.getCache("c1")).thenThrow(new RuntimeException("redis down"));

    assertThrows(CustomException.class, () -> service.read("c1", "token"));
  }

  // ================= read(communityId) =================

  @Test
  void readNoToken_emptyCommunityId_returnsInternalServerError() {
    ApiResponse response = service.read("");

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void readNoToken_cacheHit_returnsCachedCommunity() {
    when(cacheService.getCache("c1")).thenReturn("{\"communityId\":\"c1\"}");

    ApiResponse response = service.read("c1");

    assertEquals(Constants.SUCCESSFULLY_READING, response.getParams().getErrMsg());
    assertNotNull(response.getResult().get(Constants.COMMUNITY_DETAILS));
  }

  @Test
  void readNoToken_communityNotFound_returnsNotFound() {
    when(cacheService.getCache("c1")).thenReturn(null);
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.empty());

    ApiResponse response = service.read("c1");

    assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErrMsg());
  }

  // ================= delete =================

  @Test
  void delete_blankUserId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.delete("c1", "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
  }

  @Test
  void delete_emptyCommunityId_returnsInternalServerError() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");

    ApiResponse response = service.delete("", "token");

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    assertEquals(Constants.COMMUNITY_ID_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void delete_communityNotFound_returnsNotFound() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.empty());

    ApiResponse response = service.delete("c1", "token");

    assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErrMsg());
  }

  @Test
  void delete_topicInactive_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true))).thenReturn(null);

    ApiResponse response = service.delete("c1", "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.TOPIC_IS_INACTIVE, response.getParams().getErrMsg());
  }

  @Test
  void delete_success_returnsDeletedResponse() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    when(categoryRepository.findByCategoryIdAndIsActive(anyInt(), eq(true)))
        .thenReturn(activeCategory(1));

    ApiResponse response = service.delete("c1", "token");

    assertTrue(((String) response.getResult().get(Constants.RESPONSE)).contains("c1"));
    verify(communityEngagementRepository).save(any(CommunityEntity.class));
    verify(esUtilService).updateDocument(eq("community"), eq(Constants.INDEX_TYPE), eq("c1"), anyMap(), anyString());
    verify(cacheService).deleteCache("c1");
  }

  // ================= update =================

  @Test
  void update_blankUserId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.update(mapper.createObjectNode(), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
  }

  @Test
  void update_missingCommunityId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");

    ApiResponse response = service.update(mapper.createObjectNode(), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.COMMUNITY_ID_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void update_communityNotFound_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.empty());
    ObjectNode payload = mapper.createObjectNode();
    payload.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.update(payload, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErrMsg());
  }

  @Test
  void update_duplicateCommunity_returnsConflict() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    when(esUtilService.isDuplicateCommunity(anyString(), anyString(), anyString())).thenReturn(true);
    ObjectNode payload = mapper.createObjectNode();
    payload.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.update(payload, "token");

    assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    assertEquals(Constants.CREATE_ERROR_MSG_WITHIN_COMMUNITY, response.getParams().getErrMsg());
  }

  @Test
  void update_communityNameExistsForPublish_returnsPreconditionFailed() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    when(esUtilService.isDuplicateCommunity(anyString(), anyString(), anyString())).thenReturn(false);
    when(esUtilService.doesCommunityNameExistForPublish(anyString(), anyString())).thenReturn(true);
    ObjectNode payload = mapper.createObjectNode();
    payload.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.update(payload, "token");

    assertEquals(HttpStatus.PRECONDITION_FAILED, response.getResponseCode());
    assertEquals(Constants.CREATE_ERROR_MSG_COMMUNITY, response.getParams().getErrMsg());
  }

  @Test
  void update_success_returnsUpdatedResponse() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    when(esUtilService.isDuplicateCommunity(anyString(), anyString(), anyString())).thenReturn(false);
    when(esUtilService.doesCommunityNameExistForPublish(anyString(), anyString())).thenReturn(false);
    ObjectNode payload = mapper.createObjectNode();
    payload.put(Constants.COMMUNITY_ID, "c1");
    payload.put(Constants.COMMUNITY_NAME, "Updated Name");

    ApiResponse response = service.update(payload, "token");

    assertTrue(((String) response.getResult().get(Constants.RESPONSE)).contains("c1"));
    verify(communityEngagementRepository).save(any(CommunityEntity.class));
    verify(esUtilService).updateDocument(eq("community"), eq(Constants.INDEX_TYPE), eq("c1"), anyMap(), anyString());
  }

  // ================= joinCommunity =================

  @Test
  void joinCommunity_blankUserId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.joinCommunity(new HashMap<>(), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
  }

  @Test
  void joinCommunity_validationError_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "");

    ApiResponse response = service.joinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertNotNull(response.getParams().getErr());
  }

  @Test
  void joinCommunity_communityNotFound_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.empty());
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.joinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErr());
  }

  @Test
  void joinCommunity_privateCommunity_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    ObjectNode data = communityData("c1", 1);
    data.put(Constants.COMMUNITY_ACCESS_LEVEL, Constants.PRIVATE);
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", data)));
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.joinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals("This is a private community. Users cannot join it directly.", response.getParams().getErr());
  }

  @Test
  void joinCommunity_newJoin_insertsRecordAndPublishesEvent() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), any(), eq(1)))
        .thenReturn(Collections.emptyList());
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.joinCommunity(request, "token");

    assertEquals(HttpStatus.OK, response.getResponseCode());
    verify(cassandraOperation).insertRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap());
    verify(producer).push(eq("community.user.count.topic"), anyMap());
    verify(esUtilService).updateUserIndex("user1", "c1", true);
  }

  @Test
  void joinCommunity_rejoinAfterUnjoin_updatesRecord() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    Map<String, Object> existing = new HashMap<>();
    existing.put(Constants.STATUS, false);
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), any(), eq(1)))
        .thenReturn(List.of(existing));
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.joinCommunity(request, "token");

    assertEquals(HttpStatus.OK, response.getResponseCode());
    verify(cassandraOperation).updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), anyMap());
  }

  @Test
  void joinCommunity_alreadyJoined_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    Map<String, Object> existing = new HashMap<>();
    existing.put(Constants.STATUS, true);
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), any(), eq(1)))
        .thenReturn(List.of(existing));
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.joinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.ALREADY_JOINED_COMMUNITY, response.getParams().getErr());
  }

  // ================= unJoinCommunity =================

  @Test
  void unJoinCommunity_blankUserId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.unJoinCommunity(new HashMap<>(), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
  }

  @Test
  void unJoinCommunity_validationError_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "");

    ApiResponse response = service.unJoinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertNotNull(response.getParams().getErr());
  }

  @Test
  void unJoinCommunity_communityNotFound_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.empty());
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.unJoinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErr());
  }

  @Test
  void unJoinCommunity_noExistingRecord_returnsNotJoinedAlready() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), any(), eq(1)))
        .thenReturn(Collections.emptyList());
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.unJoinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.NOT_JOINED_ALREADY, response.getParams().getErr());
  }

  @Test
  void unJoinCommunity_alreadyUnjoined_returnsNotJoinedAlready() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    Map<String, Object> existing = new HashMap<>();
    existing.put(Constants.STATUS, false);
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), any(), eq(1)))
        .thenReturn(List.of(existing));
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.unJoinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.NOT_JOINED_ALREADY, response.getParams().getErr());
  }

  @Test
  void unJoinCommunity_success_updatesRecordsAndIndex() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    ObjectNode data = communityData("c1", 1);
    data.put(Constants.COUNT_OF_PEOPLE_JOINED, 5);
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", data)));
    Map<String, Object> existing = new HashMap<>();
    existing.put(Constants.STATUS, true);
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), any(), eq(1)))
        .thenReturn(List.of(existing));
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");

    ApiResponse response = service.unJoinCommunity(request, "token");

    assertEquals(HttpStatus.OK, response.getResponseCode());
    verify(cacheService).deleteUserFromHash(eq(Constants.CMMUNITY_USER_REDIS_PREFIX + "c1"), eq(Constants.USER_PREFIX + "user1"));
    verify(esUtilService).updateUserIndex("user1", "c1", false);
  }

  // ================= communitiesJoinedByUser =================

  @Test
  void communitiesJoinedByUser_blankUserId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.communitiesJoinedByUser("token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
  }

  @Test
  void communitiesJoinedByUser_cacheHit_returnsCommunityDetails() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    Map<String, Object> row = new HashMap<>();
    row.put(Constants.STATUS, true);
    row.put(Constants.COMMUNITY_ID_LOWERCASE, "c1");
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), anyList(), any()))
        .thenReturn(List.of(row));
    when(cacheService.getCache("c1")).thenReturn("{\"communityId\":\"c1\"}");

    ApiResponse response = service.communitiesJoinedByUser("token");

    List<?> details = (List<?>) response.getResult().get(Constants.COMMUNITY_DETAILS);
    assertEquals(1, details.size());
  }

  @Test
  void communitiesJoinedByUser_cacheMiss_fallsBackToDatabase() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    Map<String, Object> row = new HashMap<>();
    row.put(Constants.STATUS, true);
    row.put(Constants.COMMUNITY_ID_LOWERCASE, "c1");
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), anyList(), any()))
        .thenReturn(List.of(row));
    when(cacheService.getCache("c1")).thenReturn(null);
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));

    ApiResponse response = service.communitiesJoinedByUser("token");

    List<?> details = (List<?>) response.getResult().get(Constants.COMMUNITY_DETAILS);
    assertEquals(1, details.size());
    verify(cacheService).putCache(eq("c1"), any());
  }

  // ================= searchCommunity =================

  @Test
  void searchCommunity_overrideCache_bypassesCacheAndSearches() throws Exception {
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    SearchResult searchResult = new SearchResult();
    searchResult.setData(mapper.createArrayNode());
    when(esUtilService.searchDocuments(eq("community"), any())).thenReturn(searchResult);
    SearchCriteria criteria = new SearchCriteria();
    criteria.setOverrideCache(true);

    ApiResponse response = service.searchCommunity(criteria);

    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(searchResult, response.getResult().get(Constants.SEARCH_RESULTS));
  }

  @Test
  void searchCommunity_cachedResult_returnsFromRedis() {
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    SearchResult cached = new SearchResult();
    when(searchResultValueOperations.get(anyString())).thenReturn(cached);
    SearchCriteria criteria = new SearchCriteria();

    ApiResponse response = service.searchCommunity(criteria);

    assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    assertEquals(cached, response.getResult().get(Constants.SEARCH_RESULTS));
  }

  @Test
  void searchCommunity_searchStringTooShort_returnsBadRequest() {
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    when(searchResultValueOperations.get(anyString())).thenReturn(null);
    SearchCriteria criteria = new SearchCriteria();
    criteria.setSearchString("a");

    ApiResponse response = service.searchCommunity(criteria);

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.MINIMUM_CHARACTERS_NEEDED, response.getParams().getErrMsg());
  }

  @Test
  void searchCommunity_success_fetchesFromEsAndCaches() throws Exception {
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    when(searchResultValueOperations.get(anyString())).thenReturn(null);
    SearchResult searchResult = new SearchResult();
    searchResult.setData(mapper.createArrayNode());
    when(esUtilService.searchDocuments(eq("community"), any())).thenReturn(searchResult);
    SearchCriteria criteria = new SearchCriteria();

    ApiResponse response = service.searchCommunity(criteria);

    assertEquals(HttpStatus.OK, response.getResponseCode());
    verify(searchResultValueOperations).set(anyString(), eq(searchResult), eq(600L), any());
  }

  @Test
  void searchCommunity_esThrows_throwsCustomException() throws Exception {
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    when(searchResultValueOperations.get(anyString())).thenReturn(null);
    when(esUtilService.searchDocuments(eq("community"), any())).thenThrow(new RuntimeException("es down"));
    SearchCriteria criteria = new SearchCriteria();

    assertThrows(CustomException.class, () -> service.searchCommunity(criteria));
  }

  // ================= categoryCreate =================

  @Test
  void categoryCreate_blankUserId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.categoryCreate(categoryPayload("Tech", "desc"), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
  }

  @Test
  void categoryCreate_userRootOrgMissing_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), any()))
        .thenReturn(Collections.emptyList());

    ApiResponse response = service.categoryCreate(categoryPayload("Tech", "desc"), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_DETAILS_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void categoryCreate_validationFails_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    mockUserOrgLookup("org1");
    doThrow(new CustomException("code", "bad category payload", HttpStatus.BAD_REQUEST))
        .when(payloadValidation).validatePayload(eq(Constants.CATEGORY_PAYLOAD_VALIDATION_FILE), any());

    ApiResponse response = service.categoryCreate(categoryPayload("Tech", "desc"), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals("bad category payload", response.getParams().getErrMsg());
  }

  @Test
  void categoryCreate_withParentIdDuplicate_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    mockUserOrgLookup("org1");
    ObjectNode payload = categoryPayload("Tech", "desc");
    payload.put(Constants.PARENT_ID, 1);
    payload.put(Constants.DEPARTMENT_ID, "org1");
    when(categoryRepository.findByParentIdAndCategoryNameAndDepartmentIdAndIsActive(1, "Tech", "org1", true))
        .thenReturn(activeCategory(2));

    ApiResponse response = service.categoryCreate(payload, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.ALREADY_PRESENT_COMMUNITY_UNDER_THIS_TOPIC, response.getParams().getErrMsg());
  }

  @Test
  void categoryCreate_withParentId_success() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    mockUserOrgLookup("org1");
    ObjectNode payload = categoryPayload("Tech", "desc");
    payload.put(Constants.PARENT_ID, 1);
    payload.put(Constants.DEPARTMENT_ID, "org1");
    when(categoryRepository.findByParentIdAndCategoryNameAndDepartmentIdAndIsActive(1, "Tech", "org1", true))
        .thenReturn(null);
    when(categoryRepository.save(any(CommunityCategory.class))).thenAnswer(invocation -> {
      CommunityCategory saved = invocation.getArgument(0);
      saved.setCategoryId(101);
      return saved;
    });

    ApiResponse response = service.categoryCreate(payload, "token");

    assertEquals(Constants.SUCCESSFULLY_CREATED, response.getResult().get(Constants.STATUS));
    assertEquals(101, response.getResult().get(Constants.CATEGORY_ID));
  }

  @Test
  void categoryCreate_noParentDuplicate_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    mockUserOrgLookup("org1");
    when(categoryRepository.findByCategoryNameAndIsActive("Tech", true)).thenReturn(activeCategory(3));

    ApiResponse response = service.categoryCreate(categoryPayload("Tech", "desc"), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.ALREADY_CATEGORY_PRESENT, response.getParams().getErrMsg());
  }

  @Test
  void categoryCreate_noParent_success() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    mockUserOrgLookup("org1");
    when(categoryRepository.findByCategoryNameAndIsActive("Tech", true)).thenReturn(null);
    when(categoryRepository.save(any(CommunityCategory.class))).thenAnswer(invocation -> {
      CommunityCategory saved = invocation.getArgument(0);
      saved.setCategoryId(102);
      return saved;
    });

    ApiResponse response = service.categoryCreate(categoryPayload("Tech", "desc"), "token");

    assertEquals(Constants.SUCCESSFULLY_CREATED, response.getResult().get(Constants.STATUS));
    assertEquals(102, response.getResult().get(Constants.CATEGORY_ID));
    verify(esUtilService).addDocument(eq("community-category"), eq(Constants.INDEX_TYPE), eq("102"), anyMap(), anyString());
  }

  // ================= readCategory =================

  @Test
  void readCategory_blankUserId_returnsInternalServerError() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.readCategory("1", "token");

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void readCategory_notFound_returnsNotFound() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(categoryRepository.findByCategoryIdAndIsActive(99, true)).thenReturn(null);

    ApiResponse response = service.readCategory("99", "token");

    assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    assertEquals(Constants.INVALID_CATEGORY_ID, response.getParams().getErrMsg());
  }

  @Test
  void readCategory_success_returnsCategoryDetails() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(categoryRepository.findByCategoryIdAndIsActive(1, true)).thenReturn(activeCategory(1));

    ApiResponse response = service.readCategory("1", "token");

    assertEquals(Constants.SUCCESSFULLY_READING, response.getParams().getErrMsg());
    assertNotNull(response.getResult().get(Constants.COMMUNITY_DETAILS));
  }

  // ================= updateCategory =================

  @Test
  void updateCategory_blankUserId_returnsInternalServerError() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.updateCategory(categoryPayload("Tech", "desc"), "token");

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void updateCategory_validationFails_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    doThrow(new CustomException("code", "bad category payload", HttpStatus.BAD_REQUEST))
        .when(payloadValidation).validatePayload(eq(Constants.CATEGORY_PAYLOAD_VALIDATION_FILE), any());

    ApiResponse response = service.updateCategory(categoryPayload("Tech", "desc"), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals("bad category payload", response.getParams().getErrMsg());
  }

  @Test
  void updateCategory_missingCategoryId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");

    ApiResponse response = service.updateCategory(categoryPayload("Tech", "desc"), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.COMMUNITY_ID_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void updateCategory_notFound_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    ObjectNode payload = categoryPayload("Tech", "desc");
    payload.put(Constants.CATEGORY_ID, 1);
    when(categoryRepository.findByCategoryIdAndIsActive(1, true)).thenReturn(null);

    ApiResponse response = service.updateCategory(payload, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.INVALID_CATEGORY_ID, response.getParams().getErrMsg());
  }

  @Test
  void updateCategory_success_returnsUpdatedResponse() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    ObjectNode payload = categoryPayload("Tech Updated", "desc");
    payload.put(Constants.CATEGORY_ID, 1);
    when(categoryRepository.findByCategoryIdAndIsActive(1, true)).thenReturn(activeCategory(1));

    ApiResponse response = service.updateCategory(payload, "token");

    assertTrue(((String) response.getResult().get(Constants.RESPONSE)).contains("1"));
    verify(categoryRepository).save(any(CommunityCategory.class));
    verify(esUtilService).updateDocument(eq("community-category"), eq(Constants.INDEX_TYPE), eq("1"), anyMap(), anyString());
  }

  // ================= deleteCategory =================

  @Test
  void deleteCategory_blankUserId_returnsInternalServerError() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.deleteCategory("1", "token");

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    assertEquals(Constants.ID_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void deleteCategory_notFound_returnsNotFound() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(categoryRepository.findByCategoryIdAndIsActive(99, true)).thenReturn(null);

    ApiResponse response = service.deleteCategory("99", "token");

    assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    assertEquals(Constants.INVALID_CATEGORY_ID, response.getParams().getErrMsg());
  }

  @Test
  void deleteCategory_success_returnsDeletedResponse() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(categoryRepository.findByCategoryIdAndIsActive(1, true)).thenReturn(activeCategory(1));

    ApiResponse response = service.deleteCategory("1", "token");

    assertTrue(((String) response.getResult().get(Constants.RESPONSE)).contains("1"));
    verify(categoryRepository).save(any(CommunityCategory.class));
    verify(esUtilService).updateDocument(eq("community-category"), eq(Constants.INDEX_TYPE), eq("1"), anyMap(), anyString());
  }

  // ================= listOfCategory =================

  @Test
  void listOfCategory_cacheHit_returnsCachedCategories() {
    when(cacheService.getCache(Constants.CATEGORY_LIST_REDIS_KEY_PREFIX)).thenReturn("[{\"categoryId\":1}]");

    ApiResponse response = service.listOfCategory();

    assertEquals(Constants.SUCCESSFULLY_READING, response.getParams().getErrMsg());
    assertNotNull(response.getResult().get(Constants.CATEGORY_DETAILS));
  }

  @Test
  void listOfCategory_emptyList_returnsBadRequest() {
    when(cacheService.getCache(Constants.CATEGORY_LIST_REDIS_KEY_PREFIX)).thenReturn(null);
    when(categoryRepository.findByParentIdAndIsActive(0, true)).thenReturn(Collections.emptyList());

    ApiResponse response = service.listOfCategory();

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.CATEGORIES_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void listOfCategory_success_returnsCategoriesAndPopulatesCache() {
    when(cacheService.getCache(Constants.CATEGORY_LIST_REDIS_KEY_PREFIX)).thenReturn(null);
    when(categoryRepository.findByParentIdAndIsActive(0, true)).thenReturn(List.of(activeCategory(1)));

    ApiResponse response = service.listOfCategory();

    List<?> details = (List<?>) response.getResult().get(Constants.CATEGORY_DETAILS);
    assertEquals(1, details.size());
    verify(cacheService).putCache(eq(Constants.CATEGORY_LIST_REDIS_KEY_PREFIX), anyList());
  }

  // ================= publish =================

  private ObjectNode publishPayload(String communityId) {
    ObjectNode payload = mapper.createObjectNode();
    payload.put(Constants.COMMUNITY_ID, communityId);
    payload.put(Constants.COMMUNITY_NAME, "Tech Community");
    payload.put(Constants.ORG_ID, "org1");
    return payload;
  }

  @Test
  void publish_blankUserId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.publish(publishPayload("c1"), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
  }

  @Test
  void publish_validationFails_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    doThrow(new CustomException("code", "bad publish payload", HttpStatus.BAD_REQUEST))
        .when(payloadValidation).validatePayload(eq(Constants.COMMUNITY_PUBLISH_PAYLOAD_VALIDATION_FILE), any());

    ApiResponse response = service.publish(publishPayload("c1"), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals("bad publish payload", response.getParams().getErrMsg());
  }

  @Test
  void publish_duplicateCommunity_returnsConflict() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(esUtilService.isDuplicateCommunity(anyString(), anyString(), anyString())).thenReturn(true);

    ApiResponse response = service.publish(publishPayload("c1"), "token");

    assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    assertEquals(Constants.CREATE_ERROR_MSG_WITHIN_COMMUNITY, response.getParams().getErrMsg());
  }

  @Test
  void publish_communityNotFound_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(esUtilService.isDuplicateCommunity(anyString(), anyString(), anyString())).thenReturn(false);
    when(esUtilService.doesCommunityNameExistForPublish(anyString(), anyString())).thenReturn(false);
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true)).thenReturn(Optional.empty());

    ApiResponse response = service.publish(publishPayload("c1"), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErrMsg());
  }

  @Test
  void publish_successWithoutModerators_doesNotSendNotification() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(esUtilService.isDuplicateCommunity(anyString(), anyString(), anyString())).thenReturn(false);
    when(esUtilService.doesCommunityNameExistForPublish(anyString(), anyString())).thenReturn(false);
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));

    ApiResponse response = service.publish(publishPayload("c1"), "token");

    assertTrue(((String) response.getResult().get(Constants.RESPONSE)).contains("c1"));
    verify(notificationService, never()).sendNotification(anyList(), anyString(), anyString(), anyString());
  }

  @Test
  void publish_successWithModerators_sendsNotification() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(esUtilService.isDuplicateCommunity(anyString(), anyString(), anyString())).thenReturn(false);
    when(esUtilService.doesCommunityNameExistForPublish(anyString(), anyString())).thenReturn(false);
    ObjectNode data = communityData("c1", 1);
    ArrayNode moderators = data.putArray(Constants.MODERATORS);
    ObjectNode moderator = mapper.createObjectNode();
    moderator.put(Constants.MODERATOR_ID, "mod1");
    moderators.add(moderator);
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", data)));

    ApiResponse response = service.publish(publishPayload("c1"), "token");

    assertTrue(((String) response.getResult().get(Constants.RESPONSE)).contains("c1"));
    verify(notificationService).sendNotification(eq(List.of("mod1")), eq("c1"), eq("user1"), eq("Tech Community"));
  }

  // ================= adminJoinCommunity =================

  @Test
  void adminJoinCommunity_invalidToken_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.adminJoinCommunity(new HashMap<>(), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals("Invalid user ID from auth token.", response.getParams().getErr());
  }

  @Test
  void adminJoinCommunity_missingCommunityId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("admin1");
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "");

    ApiResponse response = service.adminJoinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertNotNull(response.getParams().getErr());
  }

  @Test
  void adminJoinCommunity_userIdsNotAList_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("admin1");
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");
    request.put(Constants.USER_IDS, "notAList");

    ApiResponse response = service.adminJoinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals("Invalid userIds", response.getParams().getErr());
  }

  @Test
  void adminJoinCommunity_userIdsEmpty_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("admin1");
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");
    request.put(Constants.USER_IDS, new ArrayList<String>());

    ApiResponse response = service.adminJoinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals("userIds cannot be empty", response.getParams().getErr());
  }

  @Test
  void adminJoinCommunity_tooManyUsers_returnsBadRequest() {
    CbServerProperties props = (CbServerProperties) ReflectionTestUtils.getField(service, "cbServerProperties");
    props.setCommunityAdminJoinMaxUser(2);
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("admin1");
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");
    request.put(Constants.USER_IDS, new ArrayList<>(List.of("u1", "u2", "u3")));

    ApiResponse response = service.adminJoinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals("Maximum 2 users allowed per request", response.getParams().getErr());
  }

  @Test
  void adminJoinCommunity_communityNotFound_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("admin1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true)).thenReturn(Optional.empty());
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");
    request.put(Constants.USER_IDS, new ArrayList<>(List.of("u1")));

    ApiResponse response = service.adminJoinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErr());
  }

  @Test
  void adminJoinCommunity_success_joinsNewUsers() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("admin1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), any(), eq(1)))
        .thenReturn(Collections.emptyList());
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");
    request.put(Constants.USER_IDS, new ArrayList<>(List.of("u1")));

    ApiResponse response = service.adminJoinCommunity(request, "token");

    Map<?, ?> result = response.getResult();
    assertEquals(List.of("u1"), result.get(Constants.JOINED_USERS));
    assertTrue(((List<?>) result.get(Constants.ALREADY_JOINED_USERS)).isEmpty());
    assertTrue(((List<?>) result.get(Constants.FAILED_USERS)).isEmpty());
    verify(producer).push(eq("community.user.count.topic"), anyMap());
  }

  // ================= adminUnjoinCommunity =================

  @Test
  void adminUnjoinCommunity_invalidToken_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.adminUnjoinCommunity(new HashMap<>(), "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals("Invalid user ID from auth token.", response.getParams().getErr());
  }

  @Test
  void adminUnjoinCommunity_communityNotFound_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("admin1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true)).thenReturn(Optional.empty());
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");
    request.put(Constants.USER_IDS, new ArrayList<>(List.of("u1")));

    ApiResponse response = service.adminUnjoinCommunity(request, "token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErr());
  }

  @Test
  void adminUnjoinCommunity_userNotJoined_addsToNotJoinedList() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("admin1");
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), any(), eq(1)))
        .thenReturn(Collections.emptyList());
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");
    request.put(Constants.USER_IDS, new ArrayList<>(List.of("u1")));

    ApiResponse response = service.adminUnjoinCommunity(request, "token");

    Map<?, ?> result = response.getResult();
    assertEquals(List.of("u1"), result.get(Constants.NOT_JOINED_USERS));
    assertTrue(((List<?>) result.get(Constants.UNJOINED_USERS)).isEmpty());
  }

  @Test
  void adminUnjoinCommunity_success_unjoinsUsers() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("admin1");
    ObjectNode data = communityData("c1", 1);
    data.put(Constants.COUNT_OF_PEOPLE_JOINED, 5);
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", data)));
    Map<String, Object> existing = new HashMap<>();
    existing.put(Constants.STATUS, true);
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), any(), eq(1)))
        .thenReturn(List.of(existing));
    Map<String, Object> request = new HashMap<>();
    request.put(Constants.COMMUNITY_ID, "c1");
    request.put(Constants.USER_IDS, new ArrayList<>(List.of("u1")));

    ApiResponse response = service.adminUnjoinCommunity(request, "token");

    Map<?, ?> result = response.getResult();
    assertEquals(List.of("u1"), result.get(Constants.UNJOINED_USERS));
    verify(esUtilService).updateUserIndex("u1", "c1", false);
    verify(cacheService).deleteUserFromHash(eq(Constants.CMMUNITY_USER_REDIS_PREFIX + "c1"), eq(Constants.USER_PREFIX + "u1"));
  }

  // ================= listOfUsersJoined =================

  private Map<String, Object> listUsersPayload(String communityId, int offset, int limit) {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.COMMUNITY_ID, communityId);
    payload.put(Constants.OFFSET, offset);
    payload.put(Constants.LIMIT, limit);
    return payload;
  }

  @Test
  void listOfUsersJoined_missingMandatoryFields_returnsBadRequest() {
    ApiResponse response = service.listOfUsersJoined("token", new HashMap<>());

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.FAILED, response.getParams().getStatus());
    assertTrue(response.getParams().getErrMsg().contains(Constants.COMMUNITY_ID));
  }

  @Test
  void listOfUsersJoined_blankUserId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.listOfUsersJoined("token", listUsersPayload("c1", 0, 10));

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
  }

  @Test
  void listOfUsersJoined_listSizeNullAndPrimaryEmpty_returnsEmptyList() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cacheService.getListSize(anyString())).thenReturn(null);
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_LOOK_UP_TABLE), anyMap(), anyList(), any()))
        .thenReturn(Collections.emptyList());

    ApiResponse response = service.listOfUsersJoined("token", listUsersPayload("c1", 0, 10));

    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(Collections.emptyList(), response.getResult().get(Constants.USER_DETAILS));
    assertEquals(0L, response.getResult().get(Constants.USER_COUNT));
  }

  @Test
  void listOfUsersJoined_listSizeNullThenPrimaryHasUsers_throwsCustomException() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cacheService.getListSize(anyString())).thenReturn(null);
    Map<String, Object> row = new HashMap<>();
    row.put(Constants.STATUS, true);
    row.put(Constants.USER_ID_LOWER_CASE, "u1");
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_LOOK_UP_TABLE), anyMap(), anyList(), any()))
        .thenReturn(List.of(row));
    when(cacheService.getPaginatedUsersFromHash(anyString(), anyInt(), anyInt()))
        .thenReturn(List.of(Constants.USER_PREFIX + "u1"));

    // listSize stays null past the primary fallback, so "startIndex >= listSize" NPEs and is
    // wrapped into a CustomException by the outer catch block.
    assertThrows(CustomException.class,
        () -> service.listOfUsersJoined("token", listUsersPayload("c1", 0, 10)));
  }

  @Test
  void listOfUsersJoined_positiveListSize_returnsUsersFromCache() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cacheService.getListSize(anyString())).thenReturn(5L);
    when(cacheService.getPaginatedUsersFromHash(anyString(), eq(0), eq(10)))
        .thenReturn(List.of(Constants.USER_PREFIX + "u1"));
    Map<String, Object> u1Map = new HashMap<>();
    u1Map.put(Constants.USER_ID_KEY, "u1");
    u1Map.put(Constants.DESIGNATION, "null");
    String u1Json;
    try {
      u1Json = mapper.writeValueAsString(u1Map);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    when(cacheService.hget(anyList())).thenReturn(new ArrayList<>(List.of(u1Json)));

    ApiResponse response = service.listOfUsersJoined("token", listUsersPayload("c1", 0, 10));

    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(5L, response.getResult().get(Constants.USER_COUNT));
    assertNotNull(response.getResult().get(Constants.USER_DETAILS));
    verify(userService, never()).fetchUserFromprimary(anyList());
  }

  @Test
  void listOfUsersJoined_missingUserBackfillsFromPrimary() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cacheService.getListSize(anyString())).thenReturn(5L);
    when(cacheService.getPaginatedUsersFromHash(anyString(), eq(0), eq(10)))
        .thenReturn(List.of(Constants.USER_PREFIX + "u1"));
    when(cacheService.hget(anyList())).thenReturn(new ArrayList<>(Collections.singletonList(null)));
    when(userService.fetchUserFromprimary(anyList()))
        .thenReturn(List.of(Map.of(Constants.USER_ID_KEY, "u1")));

    ApiResponse response = service.listOfUsersJoined("token", listUsersPayload("c1", 0, 10));

    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(5L, response.getResult().get(Constants.USER_COUNT));
    verify(userService).fetchUserFromprimary(List.of("u1"));
  }

  @Test
  void listOfUsersJoined_startIndexPastListSize_returnsEmptyList() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cacheService.getListSize(anyString())).thenReturn(5L);

    ApiResponse response = service.listOfUsersJoined("token", listUsersPayload("c1", 3, 10));

    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(Collections.emptyList(), response.getResult().get(Constants.USER_DETAILS));
    assertEquals(0L, response.getResult().get(Constants.USER_COUNT));
  }

  @Test
  void listOfUsersJoined_hashEmptyAndPrimaryFallbackAlsoEmpty_returnsEmptyList() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cacheService.getListSize(anyString())).thenReturn(5L);
    when(cacheService.getPaginatedUsersFromHash(anyString(), eq(0), eq(10)))
        .thenReturn(Collections.emptyList());
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_LOOK_UP_TABLE), anyMap(), anyList(), any()))
        .thenReturn(Collections.emptyList());

    ApiResponse response = service.listOfUsersJoined("token", listUsersPayload("c1", 0, 10));

    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(Collections.emptyList(), response.getResult().get(Constants.USER_DETAILS));
  }

  @Test
  void listOfUsersJoined_exception_throwsCustomException() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cacheService.getListSize(anyString())).thenThrow(new RuntimeException("redis down"));

    assertThrows(CustomException.class,
        () -> service.listOfUsersJoined("token", listUsersPayload("c1", 0, 10)));
  }

  // ================= listOfSubCategory =================

  private SearchCriteria subCategoryCriteria(Integer categoryId) {
    SearchCriteria criteria = new SearchCriteria();
    HashMap<String, Object> filterMap = new HashMap<>();
    if (categoryId != null) {
      filterMap.put(Constants.CATEGORY_ID, categoryId);
    }
    criteria.setFilterCriteriaMap(filterMap);
    return criteria;
  }

  @Test
  void listOfSubCategory_missingCategoryId_returnsBadRequest() {
    ApiResponse response = service.listOfSubCategory(subCategoryCriteria(null));

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.INVALID_CATEGORY_ID, response.getParams().getErrMsg());
  }

  @Test
  void listOfSubCategory_nullSearchCriteria_returnsBadRequest() {
    ApiResponse response = service.listOfSubCategory(null);

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.INVALID_CATEGORY_ID, response.getParams().getErrMsg());
  }

  @Test
  void listOfSubCategory_categoryNotFound_returnsBadRequest() {
    when(categoryRepository.findByCategoryIdAndIsActive(1, true)).thenReturn(null);

    ApiResponse response = service.listOfSubCategory(subCategoryCriteria(1));

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.INVALID_CATEGORY_ID, response.getParams().getErrMsg());
  }

  @Test
  void listOfSubCategory_cachedResult_returnsFromRedis() {
    when(categoryRepository.findByCategoryIdAndIsActive(1, true)).thenReturn(activeCategory(1));
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    SearchResult cached = new SearchResult();
    when(searchResultValueOperations.get(anyString())).thenReturn(cached);

    ApiResponse response = service.listOfSubCategory(subCategoryCriteria(1));

    assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    assertEquals(cached, response.getResult().get(Constants.SUB_CATEGORIES));
    assertNotNull(response.getResult().get(Constants.CATEGORY_DETAILS));
  }

  @Test
  void listOfSubCategory_searchStringTooShort_returnsBadRequest() {
    when(categoryRepository.findByCategoryIdAndIsActive(1, true)).thenReturn(activeCategory(1));
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    when(searchResultValueOperations.get(anyString())).thenReturn(null);
    SearchCriteria criteria = subCategoryCriteria(1);
    criteria.setSearchString("a");

    ApiResponse response = service.listOfSubCategory(criteria);

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.MINIMUM_CHARACTERS_NEEDED, response.getParams().getErrMsg());
  }

  @Test
  void listOfSubCategory_success_searchesEsAndCaches() throws Exception {
    when(categoryRepository.findByCategoryIdAndIsActive(1, true)).thenReturn(activeCategory(1));
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    when(searchResultValueOperations.get(anyString())).thenReturn(null);
    SearchResult searchResult = new SearchResult();
    searchResult.setData(mapper.createArrayNode());
    when(esUtilService.searchDocuments(eq("community-category"), any())).thenReturn(searchResult);

    ApiResponse response = service.listOfSubCategory(subCategoryCriteria(1));

    assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    assertEquals(searchResult, response.getResult().get(Constants.SUB_CATEGORIES));
    verify(searchResultValueOperations).set(anyString(), eq(searchResult), eq(600L), any());
  }

  @Test
  void listOfSubCategory_esThrows_throwsCustomException() {
    when(categoryRepository.findByCategoryIdAndIsActive(1, true)).thenThrow(new RuntimeException("db down"));

    assertThrows(CustomException.class, () -> service.listOfSubCategory(subCategoryCriteria(1)));
  }

  // ================= lisAllCategoryWithSubCat =================

  @Test
  void lisAllCategoryWithSubCat_cacheHit_returnsCachedData() throws Exception {
    when(cacheService.getCache(Constants.CATEGORY_LIST_ALL_REDIS_KEY_PREFIX))
        .thenReturn("{\"data\":[]}");

    ApiResponse response = service.lisAllCategoryWithSubCat();

    assertEquals(Constants.SUCCESSFULLY_READING, response.getParams().getErrMsg());
    assertNotNull(response.getResult().get(Constants.DATA));
  }

  @Test
  void lisAllCategoryWithSubCat_noCategories_returnsNotFound() throws Exception {
    when(cacheService.getCache(Constants.CATEGORY_LIST_ALL_REDIS_KEY_PREFIX)).thenReturn(null);
    when(categoryRepository.findByParentIdAndIsActive(0, true)).thenReturn(Collections.emptyList());
    SearchResult emptyResult = new SearchResult();
    emptyResult.setData(mapper.createArrayNode());
    when(esUtilService.fetchTopCommunitiesForTopics(anyList(), eq("community"))).thenReturn(emptyResult);

    ApiResponse response = service.lisAllCategoryWithSubCat();

    assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    assertEquals(Constants.CATEGORIES_NOT_FOUND, response.getParams().getErrMsg());
  }

  @Test
  void lisAllCategoryWithSubCat_success_buildsCategoryTree() throws Exception {
    when(cacheService.getCache(Constants.CATEGORY_LIST_ALL_REDIS_KEY_PREFIX)).thenReturn(null);
    when(categoryRepository.findByParentIdAndIsActive(0, true)).thenReturn(List.of(activeCategory(1)));
    ArrayNode data = mapper.createArrayNode();
    ObjectNode community = mapper.createObjectNode();
    community.put(Constants.TOPIC_ID, 1);
    community.put(Constants.ORD_ID, "org1");
    data.add(community);
    SearchResult searchResult = new SearchResult();
    searchResult.setData(data);
    when(esUtilService.fetchTopCommunitiesForTopics(anyList(), eq("community"))).thenReturn(searchResult);
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_ORGANISATION), anyMap(), anyList(), any()))
        .thenReturn(Collections.emptyList());

    ApiResponse response = service.lisAllCategoryWithSubCat();

    assertEquals(HttpStatus.OK, response.getResponseCode());
    List<?> categoryList = (List<?>) response.getResult().get(Constants.DATA);
    assertEquals(1, categoryList.size());
  }

  @Test
  void lisAllCategoryWithSubCat_esThrows_throwsCustomException() throws Exception {
    when(cacheService.getCache(Constants.CATEGORY_LIST_ALL_REDIS_KEY_PREFIX)).thenReturn(null);
    when(categoryRepository.findByParentIdAndIsActive(0, true)).thenReturn(List.of(activeCategory(1)));
    when(esUtilService.fetchTopCommunitiesForTopics(anyList(), eq("community")))
        .thenThrow(new RuntimeException("es down"));

    assertThrows(CustomException.class, () -> service.lisAllCategoryWithSubCat());
  }

  // ================= report =================

  private Map<String, Object> reportPayload(String communityId) {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.COMMUNITY_ID, communityId);
    return payload;
  }

  @Test
  void report_invalidPayload_returnsBadRequestWithoutTokenCheck() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.COMMUNITY_ID, "");

    ApiResponse response = service.report("token", payload);

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertNotNull(response.getParams().getErr());
  }

  @Test
  void report_invalidToken_returnsUnauthorized() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(Constants.UNAUTHORIZED);

    ApiResponse response = service.report("token", reportPayload("c1"));

    assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    assertEquals(Constants.INVALID_AUTH_TOKEN, response.getParams().getErr());
  }

  @Test
  void report_communityNotFound_returnsNotFound() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findById("c1")).thenReturn(Optional.empty());

    ApiResponse response = service.report("token", reportPayload("c1"));

    assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    assertEquals(Constants.COMMUNITY_NOT_FOUND, response.getParams().getErr());
  }

  @Test
  void report_communityInactive_returnsConflict() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    CommunityEntity entity = communityEntity("c1", communityData("c1", 1));
    entity.setActive(false);
    when(communityEngagementRepository.findById("c1")).thenReturn(Optional.of(entity));

    ApiResponse response = service.report("token", reportPayload("c1"));

    assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    assertEquals(Constants.COMMUNITY_IS_INACTIVE, response.getParams().getErr());
  }

  @Test
  void report_communitySuspended_returnsConflict() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    ObjectNode data = communityData("c1", 1);
    data.put(Constants.STATUS, Constants.SUSPENDED);
    when(communityEngagementRepository.findById("c1")).thenReturn(Optional.of(communityEntity("c1", data)));

    ApiResponse response = service.report("token", reportPayload("c1"));

    assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    assertEquals(Constants.COMMUNITY_SUSPENDED, response.getParams().getErr());
  }

  @Test
  void report_alreadyReported_returnsConflict() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findById("c1"))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_REPORTED_COMMUNITY), anyMap(), any(), any()))
        .thenReturn(List.of(new HashMap<>()));

    ApiResponse response = service.report("token", reportPayload("c1"));

    assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    assertEquals("User has already reported this community", response.getParams().getErr());
  }

  @Test
  void report_success_newReportedByListBelowLimit_setsReportedStatus() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    ObjectNode reportData = communityData("c1", 1);
    reportData.put(Constants.STATUS, Constants.ACTIVE);
    when(communityEngagementRepository.findById("c1"))
        .thenReturn(Optional.of(communityEntity("c1", reportData)));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_REPORTED_COMMUNITY), anyMap(), any(), any()))
        .thenReturn(Collections.emptyList());
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.COMMUNITY_REPORTED_BY_USER), anyMap(), any(), any()))
        .thenReturn(List.of(new HashMap<>()));
    Map<String, Object> payload = reportPayload("c1");
    payload.put(Constants.REPORTED_REASON, List.of("Spam"));

    ApiResponse response = service.report("token", payload);

    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals("c1", response.getResult().get(Constants.COMMUNITY_ID));
    verify(cassandraOperation, org.mockito.Mockito.times(2))
        .insertRecord(eq(Constants.KEYSPACE_SUNBIRD), anyString(), anyMap());
    verify(esUtilService).updateDocument(eq("community"), eq(Constants.INDEX_TYPE), eq("c1"), anyMap(), anyString());
  }

  @Test
  void report_success_withOthersReasonAndExistingReportedByArray_setsSuspendedStatus() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    ObjectNode data = communityData("c1", 1);
    data.put(Constants.STATUS, Constants.ACTIVE);
    ArrayNode reportedBy = data.putArray(Constants.REPORTED_BY);
    reportedBy.add("user2");
    when(communityEngagementRepository.findById("c1")).thenReturn(Optional.of(communityEntity("c1", data)));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_REPORTED_COMMUNITY), anyMap(), any(), any()))
        .thenReturn(Collections.emptyList());
    List<Map<String, Object>> reportedByUsers = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      reportedByUsers.add(new HashMap<>());
    }
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.COMMUNITY_REPORTED_BY_USER), anyMap(), any(), any()))
        .thenReturn(reportedByUsers);
    Map<String, Object> payload = reportPayload("c1");
    payload.put(Constants.REPORTED_REASON, List.of("Spam", Constants.OTHERS));
    payload.put(Constants.OTHER_REASON, "custom reason");

    ApiResponse response = service.report("token", payload);

    assertEquals(HttpStatus.OK, response.getResponseCode());
    verify(cacheService).putCache(anyString(), any());
  }

  @Test
  void report_exceptionDuringProcessing_returnsInternalServerErrorWithoutThrowing() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(communityEngagementRepository.findById("c1"))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_REPORTED_COMMUNITY), anyMap(), any(), any()))
        .thenThrow(new RuntimeException("cassandra down"));

    ApiResponse response = service.report("token", reportPayload("c1"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    assertEquals(Constants.COMMUNITY_REPORT_FAILED, response.getParams().getErr());
  }

  // ================= uploadFile =================

  @Test
  void uploadFile_emptyFile_returnsBadRequest() {
    MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

    ApiResponse response = service.uploadFile(file, "c1");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.COMMUNITY_FILE_EMPTY, response.getParams().getErr());
  }

  @Test
  void uploadFile_blankCommunityId_returnsBadRequest() {
    MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain",
        "content".getBytes(StandardCharsets.UTF_8));

    ApiResponse response = service.uploadFile(file, "");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.INVALID_COMMUNITY_ID, response.getParams().getErr());
  }

  @Test
  void uploadFile_success_writesTempFileAndDelegatesToCloudUpload() {
    MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain",
        "content".getBytes(StandardCharsets.UTF_8));

    ApiResponse response = service.uploadFile(file, "c1");

    // storageService is only wired via @PostConstruct, so in this unit test it stays null and
    // the cloud-storage call inside the delegated 3-arg uploadFile fails; that failure is caught
    // by production code's own try/catch and surfaces as a FAILED response instead of a throw.
    assertEquals(Constants.FAILED, response.getParams().getStatus());
  }

  @Test
  void uploadFileThreeArg_storageServiceNull_returnsFailedResponse() {
    ApiResponse response = service.uploadFile(new File("nonexistent.txt"), "folder", "container");

    assertEquals(Constants.FAILED, response.getParams().getStatus());
    assertTrue(response.getParams().getErrMsg().contains("Failed to upload file"));
  }

  // ================= searchTopic =================

  @Test
  void searchTopic_cachedResult_returnsFromRedis() {
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    SearchResult cached = new SearchResult();
    when(searchResultValueOperations.get(anyString())).thenReturn(cached);

    ApiResponse response = service.searchTopic(new SearchCriteria());

    assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    assertEquals(cached, response.getResult().get(Constants.SEARCH_RESULTS));
  }

  @Test
  void searchTopic_searchStringTooShort_returnsBadRequest() {
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    when(searchResultValueOperations.get(anyString())).thenReturn(null);
    SearchCriteria criteria = new SearchCriteria();
    criteria.setSearchString("a");

    ApiResponse response = service.searchTopic(criteria);

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.MINIMUM_CHARACTERS_NEEDED, response.getParams().getErrMsg());
  }

  @Test
  void searchTopic_success_searchesCategoryIndex() throws Exception {
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    when(searchResultValueOperations.get(anyString())).thenReturn(null);
    SearchResult searchResult = new SearchResult();
    searchResult.setData(mapper.createArrayNode());
    when(esUtilService.searchDocuments(eq("community-category"), any())).thenReturn(searchResult);

    ApiResponse response = service.searchTopic(new SearchCriteria());

    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(searchResult, response.getResult().get(Constants.SEARCH_RESULTS));
  }

  @Test
  void searchTopic_esThrows_throwsCustomException() throws Exception {
    when(searchResultRedisTemplate.opsForValue()).thenReturn(searchResultValueOperations);
    when(searchResultValueOperations.get(anyString())).thenReturn(null);
    when(esUtilService.searchDocuments(eq("community-category"), any())).thenThrow(new RuntimeException("es down"));

    assertThrows(CustomException.class, () -> service.searchTopic(new SearchCriteria()));
  }

  // ================= listAllCommunitiesJoinedByUser =================

  @Test
  void listAllCommunitiesJoinedByUser_blankUserId_returnsBadRequest() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn(null);

    ApiResponse response = service.listAllCommunitiesJoinedByUser("token");

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
  }

  @Test
  void listAllCommunitiesJoinedByUser_noneJoined_returnsEmptyList() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), anyList(), any()))
        .thenReturn(Collections.emptyList());

    ApiResponse response = service.listAllCommunitiesJoinedByUser("token");

    List<?> details = (List<?>) response.getResult().get(Constants.COMMUNITY_ID);
    assertTrue(details.isEmpty());
  }

  @Test
  void listAllCommunitiesJoinedByUser_cacheHit_returnsCommunityNameAndId() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    Map<String, Object> row = new HashMap<>();
    row.put(Constants.STATUS, true);
    row.put(Constants.COMMUNITY_ID_LOWERCASE, "c1");
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), anyList(), any()))
        .thenReturn(List.of(row));
    when(cacheService.getCache("c1")).thenReturn("{\"communityName\":\"Tech\",\"communityId\":\"c1\"}");

    ApiResponse response = service.listAllCommunitiesJoinedByUser("token");

    List<?> details = (List<?>) response.getResult().get(Constants.COMMUNITY_ID);
    assertEquals(1, details.size());
  }

  @Test
  void listAllCommunitiesJoinedByUser_cacheMiss_fallsBackToDatabaseAndPopulatesCache() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    Map<String, Object> row = new HashMap<>();
    row.put(Constants.STATUS, true);
    row.put(Constants.COMMUNITY_ID_LOWERCASE, "c1");
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), anyList(), any()))
        .thenReturn(List.of(row));
    when(cacheService.getCache("c1")).thenReturn(null);
    when(communityEngagementRepository.findByCommunityIdAndIsActive("c1", true))
        .thenReturn(Optional.of(communityEntity("c1", communityData("c1", 1))));

    ApiResponse response = service.listAllCommunitiesJoinedByUser("token");

    List<?> details = (List<?>) response.getResult().get(Constants.COMMUNITY_ID);
    assertEquals(1, details.size());
    verify(cacheService).putCache(eq("c1"), any());
  }

  @Test
  void listAllCommunitiesJoinedByUser_missingNameOrId_skipsEntry() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    Map<String, Object> row = new HashMap<>();
    row.put(Constants.STATUS, true);
    row.put(Constants.COMMUNITY_ID_LOWERCASE, "c1");
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), anyList(), any()))
        .thenReturn(List.of(row));
    when(cacheService.getCache("c1")).thenReturn("{\"communityId\":\"c1\"}");

    ApiResponse response = service.listAllCommunitiesJoinedByUser("token");

    List<?> details = (List<?>) response.getResult().get(Constants.COMMUNITY_ID);
    assertTrue(details.isEmpty());
  }

  @Test
  void listAllCommunitiesJoinedByUser_exception_throwsCustomException() {
    when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user1");
    when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER_COMMUNITY_TABLE), anyMap(), anyList(), any()))
        .thenThrow(new RuntimeException("cassandra down"));

    assertThrows(CustomException.class, () -> service.listAllCommunitiesJoinedByUser("token"));
  }

  // ================= searchCommunityFromPrimary =================

  @Test
  void searchCommunityFromPrimary_searchStringTooShort_returnsBadRequest() {
    SearchCriteria criteria = new SearchCriteria();
    criteria.setSearchString("a");

    ApiResponse response = service.searchCommunityFromPrimary(criteria);

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.MINIMUM_CHARACTERS_NEEDED, response.getParams().getErrMsg());
  }

  @Test
  void searchCommunityFromPrimary_success_enrichesCreatedByUsers() throws Exception {
    ArrayNode data = mapper.createArrayNode();
    ObjectNode item = mapper.createObjectNode();
    item.put(Constants.CREATED_BY, "user1");
    data.add(item);
    SearchResult searchResult = new SearchResult();
    searchResult.setData(data);
    when(esUtilService.searchDocuments(eq(Constants.INDEX_NAME), any())).thenReturn(searchResult);
    when(cacheService.hget(anyList())).thenReturn(new ArrayList<>(Collections.singletonList(null)));

    ApiResponse response = service.searchCommunityFromPrimary(new SearchCriteria());

    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(searchResult, response.getResult().get(Constants.SEARCH_RESULTS));
  }

  @Test
  void searchCommunityFromPrimary_emptyResults_skipsEnrichment() throws Exception {
    SearchResult searchResult = new SearchResult();
    searchResult.setData(mapper.createArrayNode());
    when(esUtilService.searchDocuments(eq(Constants.INDEX_NAME), any())).thenReturn(searchResult);

    ApiResponse response = service.searchCommunityFromPrimary(new SearchCriteria());

    assertEquals(HttpStatus.OK, response.getResponseCode());
    verify(cacheService, never()).hget(anyList());
  }

  @Test
  void searchCommunityFromPrimary_esThrows_returnsBadRequestWithoutThrowing() throws Exception {
    when(esUtilService.searchDocuments(eq(Constants.INDEX_NAME), any())).thenThrow(new RuntimeException("es down"));

    ApiResponse response = service.searchCommunityFromPrimary(new SearchCriteria());

    assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    assertEquals(Constants.FAILED_CONST, response.getParams().getStatus());
  }

  // ================= syncUserWithCommunity =================

  @Test
  void syncUserWithCommunity_unsupportedFileType_throwsCustomException() {
    MockMultipartFile file = new MockMultipartFile("file", "data.txt", "text/plain",
        "content".getBytes(StandardCharsets.UTF_8));

    assertThrows(CustomException.class, () -> service.syncUserWithCommunity(file));
  }

  @Test
  void syncUserWithCommunity_noRecords_returnsSuccess() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv",
        "header\n".getBytes(StandardCharsets.UTF_8));
    when(fileProcessService.processCsvAndSendMessage(any(InputStream.class)))
        .thenReturn(Collections.emptyList());

    ApiResponse response = service.syncUserWithCommunity(file);

    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(Constants.SUCCESS, response.getParams().getStatus());
  }

  @Test
  void syncUserWithCommunity_processesActiveRowsAndSkipsInactive() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv",
        "header\n".getBytes(StandardCharsets.UTF_8));
    Map<String, String> activeRow = new HashMap<>();
    activeRow.put("status", "true");
    activeRow.put(Constants.USER_ID_LOWER_CASE, "u1");
    activeRow.put(Constants.COMMUNITY_ID_LOWERCASE, "c1");
    Map<String, String> inactiveRow = new HashMap<>();
    inactiveRow.put("status", "false");
    when(fileProcessService.processCsvAndSendMessage(any(InputStream.class)))
        .thenReturn(List.of(activeRow, inactiveRow));

    ApiResponse response = service.syncUserWithCommunity(file);

    assertEquals(HttpStatus.OK, response.getResponseCode());
    verify(esUtilService).updateUserIndex("u1", "c1", true);
    verify(esUtilService, org.mockito.Mockito.times(1)).updateUserIndex(anyString(), anyString(), anyBoolean());
  }

  @Test
  void syncUserWithCommunity_fileProcessingThrowsIOException_throwsCustomException() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv",
        "header\n".getBytes(StandardCharsets.UTF_8));
    when(fileProcessService.processCsvAndSendMessage(any(InputStream.class)))
        .thenThrow(new java.io.IOException("boom"));

    assertThrows(CustomException.class, () -> service.syncUserWithCommunity(file));
  }
}

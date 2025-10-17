package com.igot.cb.community.service.impl;

import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.community.entity.CommunityCategory;
import com.igot.cb.community.repository.CommunityCategoryRepository;
import com.igot.cb.community.service.NotificationService;
import com.igot.cb.community.service.UserService;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.*;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityManagementServiceImplMethodTest {

    @InjectMocks
    private CommunityManagementServiceImpl service;

    @Mock private EsUtilService esUtilService;
    @Mock private CacheService cacheService;
    @Mock private ObjectMapper objectMapper;
    @Mock private CbServerProperties cbServerProperties;
    @Mock private PayloadValidation payloadValidation;
    @Mock private CommunityCategoryRepository categoryRepository;
    @Mock private AccessTokenValidator accessTokenValidator;
    @Mock private CassandraOperation cassandraOperation;

    @Mock private RedisTemplate<String, SearchResult> redisTemplate;
    @Mock private RedisTemplate<String, Object> objectRedisTemplate;

    @Mock private UserService userService;
    @Mock private NotificationService notificationService;
    @Mock private FileProcessService fileProcessService;

    private final String authToken = "dummyToken";
    private final String userId = "user123";
    private final String categoryName = "Education";
    private final String departmentId = "org123";
    private final int parentId = 100;
    private static final String REDIS_CACHE_KEY = Constants.CATEGORY_LIST_ALL_REDIS_KEY_PREFIX;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "communityCategoryIndex", "community-category-index");
        ReflectionTestUtils.setField(service, "communityIndex", "community-index");
    }

    @Test
    void testCategoryCreate_withParentId_success() {
        JsonNode categoryDetails = new ObjectMapper().createObjectNode()
                .put(Constants.CATEGORY_NAME, categoryName)
                .put(Constants.DESCRIPTION, "desc")
                .put(Constants.DEPARTMENT_ID, departmentId)
                .put(Constants.PARENT_ID, parentId);

        Mockito.when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);

        List<Map<String, Object>> userDetails = new ArrayList<>();
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.USER_ROOT_ORG_ID, departmentId);
        userDetails.add(userMap);
        Mockito.when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                        anyString(), anyString(), anyMap(), anyList(), anyInt()))
                .thenReturn(userDetails);

        Mockito.doNothing().when(payloadValidation).validatePayload(anyString(), eq(categoryDetails));

        Mockito.when(categoryRepository.findByParentIdAndCategoryNameAndDepartmentIdAndIsActive(
                        eq(parentId), eq(categoryName), eq(departmentId), eq(true)))
                .thenReturn(null);

        CommunityCategory savedCategory = new CommunityCategory();
        savedCategory.setCategoryId(123);
        savedCategory.setCategoryName(categoryName);
        Mockito.when(categoryRepository.save(any())).thenReturn(savedCategory);

        Map<String, Object> convertedMap = Map.of("key", "value");
        Mockito.when(objectMapper.convertValue(eq(categoryDetails), eq(Map.class)))
                .thenReturn(convertedMap);
        Mockito.when(cbServerProperties.getElasticCommunityCategoryJsonPath()).thenReturn("dummy/path");

        ApiResponse response = service.categoryCreate(categoryDetails, authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESSFULLY_CREATED, response.getResult().get(Constants.STATUS));

        verify(esUtilService).updateDocument(
                eq("community-category-index"), eq(Constants.INDEX_TYPE),
                eq("123"), eq(convertedMap), eq("dummy/path"));
    }

    @Test
    void testCategoryCreate_withoutParentId_success() {
        JsonNode categoryDetails = new ObjectMapper().createObjectNode()
                .put(Constants.CATEGORY_NAME, categoryName)
                .put(Constants.DESCRIPTION, "desc")
                .put(Constants.DEPARTMENT_ID, departmentId);

        Mockito.when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);

        Map<String, Object> userMap = Map.of(Constants.USER_ROOT_ORG_ID, departmentId);
        Mockito.when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(userMap));

        Mockito.doNothing().when(payloadValidation).validatePayload(anyString(), eq(categoryDetails));
        Mockito.when(categoryRepository.findByCategoryNameAndIsActive(categoryName, true)).thenReturn(null);

        CommunityCategory savedCategory = new CommunityCategory();
        savedCategory.setCategoryId(456);
        savedCategory.setCategoryName(categoryName);
        Mockito.when(categoryRepository.save(any())).thenReturn(savedCategory);
        Mockito.when(cbServerProperties.getElasticCommunityCategoryJsonPath()).thenReturn("dummy/path");

        // ✅ Use mutable map to avoid UnsupportedOperationException
        Map<String, Object> convertedMap = new HashMap<>();
        convertedMap.put(Constants.CATEGORY_ID, 456);
        convertedMap.put(Constants.STATUS, Constants.ACTIVE);

        Mockito.when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(convertedMap);

        ApiResponse response = service.categoryCreate(categoryDetails, authToken);

        assertEquals(Constants.SUCCESSFULLY_CREATED, response.getResult().get(Constants.STATUS));
        verify(esUtilService).addDocument(eq("community-category-index"),
                eq(Constants.INDEX_TYPE), eq("456"), eq(convertedMap), eq("dummy/path"));
    }

    @Test
    void testCategoryCreate_duplicateWithParent() {
        JsonNode categoryDetails = new ObjectMapper().createObjectNode()
                .put(Constants.CATEGORY_NAME, categoryName)
                .put(Constants.DESCRIPTION, "desc")
                .put(Constants.DEPARTMENT_ID, departmentId)
                .put(Constants.PARENT_ID, parentId);

        Mockito.when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);

        Map<String, Object> userMap = Map.of(Constants.USER_ROOT_ORG_ID, departmentId);
        Mockito.when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(userMap));

        Mockito.doNothing().when(payloadValidation).validatePayload(anyString(), any());

        Mockito.when(categoryRepository.findByParentIdAndCategoryNameAndDepartmentIdAndIsActive(
                        anyInt(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new CommunityCategory());

        ApiResponse response = service.categoryCreate(categoryDetails, authToken);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.ALREADY_PRESENT_COMMUNITY_UNDER_THIS_TOPIC, response.getParams().getErrMsg());
    }

    @Test
    void testCategoryCreate_duplicateWithoutParent() {
        JsonNode categoryDetails = new ObjectMapper().createObjectNode()
                .put(Constants.CATEGORY_NAME, categoryName)
                .put(Constants.DESCRIPTION, "desc")
                .put(Constants.DEPARTMENT_ID, departmentId);

        Mockito.when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        Map<String, Object> userMap = Map.of(Constants.USER_ROOT_ORG_ID, departmentId);
        Mockito.when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(userMap));

        Mockito.doNothing().when(payloadValidation).validatePayload(anyString(), any());
        Mockito.when(categoryRepository.findByCategoryNameAndIsActive(categoryName, true)).thenReturn(new CommunityCategory());

        ApiResponse response = service.categoryCreate(categoryDetails, authToken);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.ALREADY_CATEGORY_PRESENT, response.getParams().getErrMsg());
    }

    @Test
    void testCategoryCreate_invalidUserId() {
        JsonNode categoryDetails = new ObjectMapper().createObjectNode();
        Mockito.when(accessTokenValidator.verifyUserToken(authToken)).thenReturn("");

        ApiResponse response = service.categoryCreate(categoryDetails, authToken);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testCategoryCreate_missingRootOrg() {
        JsonNode categoryDetails = new ObjectMapper().createObjectNode();
        Mockito.when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        Mockito.when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(Map.of())); // no USER_ROOT_ORG_ID

        ApiResponse response = service.categoryCreate(categoryDetails, authToken);

        assertEquals(Constants.USER_DETAILS_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testCategoryCreate_payloadValidationFails() throws Exception {
        JsonNode categoryDetails = new ObjectMapper().createObjectNode();
        Mockito.when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        Mockito.when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(Map.of(Constants.USER_ROOT_ORG_ID, departmentId)));

        Mockito.doThrow(new CustomException("error", "Validation Failed", HttpStatus.BAD_REQUEST))
                .when(payloadValidation).validatePayload(any(), eq(categoryDetails));

        ApiResponse response = service.categoryCreate(categoryDetails, authToken);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Validation Failed", response.getParams().getErrMsg());
    }

    @Test
    void testCategoryCreate_unexpectedException() {
        JsonNode categoryDetails = new ObjectMapper().createObjectNode()
                .put(Constants.CATEGORY_NAME, categoryName)
                .put(Constants.DESCRIPTION, "desc")
                .put(Constants.DEPARTMENT_ID, departmentId);

        Mockito.when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        Mockito.when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(Map.of(Constants.USER_ROOT_ORG_ID, departmentId)));

        Mockito.doNothing().when(payloadValidation).validatePayload(anyString(), any());

        Mockito.when(categoryRepository.findByCategoryNameAndIsActive(categoryName, true)).thenReturn(null);

        Mockito.when(categoryRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.categoryCreate(categoryDetails, authToken));
        assertEquals("error while processing", exception.getCode());
    }

    @Test
    void testLisAllCategoryWithSubCat_success_fromRedis() throws Exception {
        // Given
        String cachedJson = "{\"cached\":true}";
        Map<String, Object> cachedMap = Map.of("cached", true);

        when(cacheService.getCache(REDIS_CACHE_KEY)).thenReturn(cachedJson);

        // When
        ApiResponse response = service.lisAllCategoryWithSubCat();

        // Then
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESSFULLY_READING, response.getParams().getErrMsg());

        verify(cacheService).getCache(REDIS_CACHE_KEY);
    }

    @Test
    void testListAllCategoryWithSubCat_success_fromDB() throws Exception {
        // Given
        String cachedJson = null;  // simulate cache miss

        // Create realistic data with expected fields
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode dataArray = mapper.createArrayNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("orgId", "org123");
        item.put("categoryId", "cat001");
        item.put("name", "Sample Category");
        dataArray.add(item);

        when(cacheService.getCache(REDIS_CACHE_KEY)).thenReturn(cachedJson);

        SearchResult mockResult = Mockito.mock(SearchResult.class);
        when(mockResult.getData()).thenReturn(dataArray);

        when(esUtilService.fetchTopCommunitiesForTopics(anyList(), anyString()))
                .thenReturn(mockResult);

        // Provide mock list with the same fields
        List<Map<String, Object>> mockList = List.of(
                Map.of(
                        "orgId", "org123",
                        "categoryId", "cat001",
                        "name", "Sample Category"
                )
        );
        when(objectMapper.convertValue(eq(dataArray), any(TypeReference.class)))
                .thenReturn(mockList);

        // When
        ApiResponse response = service.lisAllCategoryWithSubCat();

        // Then
        assertEquals(HttpStatus.OK, response.getResponseCode());

    }

    @Test
    void testGetPopularCommunitiesByField_success() throws Exception {
        // Set private field noOfPopularCommunities to avoid NullPointerException
        Field field = CommunityManagementServiceImpl.class.getDeclaredField("noOfPopularCommunities");
        field.setAccessible(true);
        field.set(service, 10);

        // Prepare input payload
        Map<String, Object> payload = new HashMap<>();
        payload.put(Constants.FIELD, "popularity");

        // Mock hits
        Hit<Map<String, Object>> hit = mock(Hit.class);
        Map<String, Object> sourceMap = Map.of("id", "comm1", "popularity", 100);
        when(hit.source()).thenReturn(sourceMap);

        List<Hit<Map<String, Object>>> hitsList = List.of(hit);

        // Mock HitsMetadata
        HitsMetadata<Map<String, Object>> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(hitsList);

        // Mock SearchResponse
        SearchResponse<Map<String, Object>> searchResponse = mock(SearchResponse.class);
        when(searchResponse.hits()).thenReturn(hitsMetadata);

        // Mock buckets for aggregation
        StringTermsBucket bucket1 = StringTermsBucket.of(b -> b.key("pop1").docCount(10L));
        StringTermsBucket bucket2 = StringTermsBucket.of(b -> b.key("pop2").docCount(5L));
        List<StringTermsBucket> buckets = List.of(bucket1, bucket2);

        // Mock StringTermsAggregate
        StringTermsAggregate stringTermsAgg = StringTermsAggregate.of(st -> st.buckets(sb -> sb.array(buckets)));

        // Mock Aggregate with string terms
        Aggregate aggregate = Aggregate.of(a -> a.sterms(stringTermsAgg));

        Map<String, Aggregate> aggregations = Map.of("popularity_terms", aggregate);

        when(searchResponse.aggregations()).thenReturn(aggregations);

        // Mock esUtilService call
        when(esUtilService.popularCommunities(any(), any())).thenReturn(searchResponse);

        // Call the service method
        ApiResponse response = service.getPopularCommunitiesByField(payload);

        // Assertions
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNull(response.getParams().getErrMsg());

        // Data assertions
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getResult().get(Constants.DATA);
        assertNotNull(data);
        assertFalse(data.isEmpty());
        assertEquals("comm1", data.get(0).get("id"));

        // Facets assertions
        List<Map<String, Object>> facets = (List<Map<String, Object>>) response.getResult().get(Constants.FACETS);
        assertNotNull(facets);
        assertEquals(2, facets.size());
    }
}


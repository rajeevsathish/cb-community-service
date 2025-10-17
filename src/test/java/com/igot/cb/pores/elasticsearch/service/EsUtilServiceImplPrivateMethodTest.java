package com.igot.cb.pores.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.elasticsearch.config.EsConfig;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.CbServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EsUtilServiceImplPrivateMethodTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;
    @Mock private ElasticsearchClient sbESClient;
    @Mock private EsConfig esConfig;
    @Mock private ObjectMapper objectMapper;
    @Mock private CbServerProperties cbServerProperties;

    private EsUtilServiceImpl esUtilService;

    @BeforeEach
    void setup() throws Exception {
        esUtilService = new EsUtilServiceImpl(elasticsearchClient, esConfig, sbESClient);

        ElasticsearchClient mockClient = mock(ElasticsearchClient.class);
        EsConfig mockConfig = mock(EsConfig.class);
        ElasticsearchClient sbMockClient = mock(ElasticsearchClient.class);
        esUtilService = new EsUtilServiceImpl(mockClient, mockConfig, sbMockClient);

        Field objectMapperField = EsUtilServiceImpl.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);
        objectMapperField.set(esUtilService, objectMapper);

        Field cbPropsField = EsUtilServiceImpl.class.getDeclaredField("cbServerProperties");
        cbPropsField.setAccessible(true);
        cbPropsField.set(esUtilService, cbServerProperties);

        Field sbUserIndexField = EsUtilServiceImpl.class.getDeclaredField("sbUserIndex");
        sbUserIndexField.setAccessible(true);
        sbUserIndexField.set(esUtilService, "user-index");

        Field communityIndexField = EsUtilServiceImpl.class.getDeclaredField("communityIndex");
        communityIndexField.setAccessible(true);
        communityIndexField.set(esUtilService, "community-index");
    }

    private BoolQuery.Builder invokeBuildFilterQuery(Map<String, Object> filterCriteriaMap) throws Exception {
        Method method = EsUtilServiceImpl.class.getDeclaredMethod("buildFilterQuery", Map.class);
        method.setAccessible(true);
        return (BoolQuery.Builder) method.invoke(esUtilService, filterCriteriaMap);
    }

    @Test
    void testBuildFilterQuery_withNullMap_returnsNull() throws Exception {
        assertNull(invokeBuildFilterQuery(null));
    }

    @Test
    void testBuildFilterQuery_withEmptyMap_returnsNull() throws Exception {
        assertNull(invokeBuildFilterQuery(Collections.emptyMap()));
    }

    @Test
    void testBuildFilterQuery_withMustNotArray() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("must_not", new ArrayList<>(List.of("value1", "value2")));

        BoolQuery.Builder builder = invokeBuildFilterQuery(map);
        assertNotNull(builder);
        assertFalse(builder.build().mustNot().isEmpty());
    }

    @Test
    void testBuildFilterQuery_withBooleanField() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("isActive", true);

        BoolQuery.Builder builder = invokeBuildFilterQuery(map);
        assertNotNull(builder);
        assertFalse(builder.build().must().isEmpty());
    }

    @Test
    void testBuildFilterQuery_withArrayListField() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("category", new ArrayList<>(List.of("cat1", "cat2")));

        BoolQuery.Builder builder = invokeBuildFilterQuery(map);
        assertNotNull(builder);
        assertFalse(builder.build().must().isEmpty());
    }

    @Test
    void testBuildFilterQuery_withStringField() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "active");

        BoolQuery.Builder builder = invokeBuildFilterQuery(map);
        assertNotNull(builder);
        assertFalse(builder.build().must().isEmpty());
    }

    @Test
    void testBuildFilterQuery_withRangeQuery() throws Exception {
        Map<String, Object> rangeMap = new HashMap<>();
        rangeMap.put("gte", JsonData.of(10));
        rangeMap.put("lte", JsonData.of(100));

        Map<String, Object> map = new HashMap<>();
        map.put("createdDate", rangeMap);

        BoolQuery.Builder builder = invokeBuildFilterQuery(map);
        assertNotNull(builder);
    }

    @Test
    void testAddSortToSearchSourceBuilder_withNullCriteria() throws Exception {
        SearchRequest.Builder builder = new SearchRequest.Builder();
        invokeAddSortToSearchSourceBuilder(null, builder);
    }

    @Test
    void testAddSortToSearchSourceBuilder_withBlankOrderBy() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setOrderBy(" ");
        criteria.setOrderDirection("asc");

        SearchRequest.Builder builder = new SearchRequest.Builder();
        invokeAddSortToSearchSourceBuilder(criteria, builder);
    }

    @Test
    void testAddSortToSearchSourceBuilder_withBlankOrderDirection() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setOrderBy("someField");
        criteria.setOrderDirection(" ");

        SearchRequest.Builder builder = new SearchRequest.Builder();
        invokeAddSortToSearchSourceBuilder(criteria, builder);
    }

    @Test
    void testAddSortToSearchSourceBuilder_withNumericSortField() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setOrderBy("countOfPeopleJoined");
        criteria.setOrderDirection("ASC");

        SearchRequest.Builder builder = new SearchRequest.Builder();
        invokeAddSortToSearchSourceBuilder(criteria, builder);

    }

    @Test
    void testAddSortToSearchSourceBuilder_withTextField() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setOrderBy("name");
        criteria.setOrderDirection("DESC");

        SearchRequest.Builder builder = new SearchRequest.Builder();
        invokeAddSortToSearchSourceBuilder(criteria, builder);

    }

    @Test
    void testBuildTermQuery() throws Exception {
        Map<String, Object> termMap = new HashMap<>();
        termMap.put("status", FieldValue.of("active"));

        Query query = (Query) invokePrivate("buildTermQuery", termMap);

        assertNotNull(query);
        assertTrue(query.bool().must().stream().anyMatch(q -> q.term().field().equals("status")));
    }


    @Test
    void testBuildMatchQuery() throws Exception {
        Map<String, Object> matchMap = new HashMap<>();
        matchMap.put("name", FieldValue.of("elon"));

        Query query = (Query) invokePrivate("buildMatchQuery", matchMap);

        assertNotNull(query);
        assertTrue(query.bool().must().stream().anyMatch(q -> q.match().field().equals("name")));
    }

    @Test
    void testAddQueryStringToFilter_withValidSearchString_addsBoolQuery() throws Exception {
        String searchString = "TestCommunity";

        // Create a BoolQuery.Builder
        BoolQuery.Builder builder = new BoolQuery.Builder();

        // Use reflection to invoke private method
        Method method = EsUtilServiceImpl.class.getDeclaredMethod("addQueryStringToFilter", String.class, BoolQuery.Builder.class);
        method.setAccessible(true);
        method.invoke(esUtilService, searchString, builder);

        // Build final BoolQuery and verify content
        BoolQuery boolQuery = builder.build();
        assertFalse(boolQuery.must().isEmpty(), "Must clause should not be empty");

        BoolQuery innerBool = boolQuery.must().get(0).bool();
        assertEquals(2, innerBool.should().size(), "Inner BoolQuery should contain 2 should clauses");

        List<String> fields = innerBool.should().stream().map(q -> q.wildcard().field()).collect(Collectors.toList());
        assertTrue(fields.contains("communityName.keyword"));
        assertTrue(fields.contains("orgName.keyword"));
    }

    @Test
    void testAddQueryStringToFilter_withBlankSearchString_addsNothing() throws Exception {
        // Empty search string
        String searchString = " ";

        BoolQuery.Builder builder = new BoolQuery.Builder();

        Method method = EsUtilServiceImpl.class.getDeclaredMethod("addQueryStringToFilter", String.class, BoolQuery.Builder.class);
        method.setAccessible(true);
        method.invoke(esUtilService, searchString, builder);

        BoolQuery boolQuery = builder.build();
        assertTrue(boolQuery.must().isEmpty(), "Must clause should be empty for blank input");
    }

    @Test
    void testBuildQueryPart_withNullMap_returnsMatchAll() throws Exception {
        Query result = (Query) invokePrivateMethod("buildQueryPart", new Class[]{Map.class}, (Object) null);
        assertNotNull(result);
        assertTrue(result.isMatchAll());
    }

    @Test
    void testBuildQueryPart_withUnsupportedQuery_throwsException() {
        Map<String, Object> queryMap = Map.of("unknown", Map.of("field", "value"));
        Executable exec = () -> invokePrivateMethod("buildQueryPart", new Class[]{Map.class}, queryMap);
        assertThrows(InvocationTargetException.class, exec);
    }

    @Test
    void testBuildRangeQuery_withGteLte() throws Exception {
        Map<String, Object> conditions = Map.of(
                "gte", 5,
                "lte", 10
        );
        Map<String, Object> rangeMap = Map.of("age", conditions);

        Query result = (Query) invokePrivateMethod("buildRangeQuery", new Class[]{Map.class}, rangeMap);
        assertNotNull(result);
        assertTrue(result.isBool());
    }

    @Test
    void testAddFacetsToSearchSourceBuilder_usingReflection() throws Exception {
        // Arrange

        // Prepare input
        List<String> facets = List.of("category", "topic");

        // Create a SearchRequest.Builder instance
        SearchRequest.Builder builder = new SearchRequest.Builder();

        // Use reflection to access the private method
        Method method = EsUtilServiceImpl.class.getDeclaredMethod(
                "addFacetsToSearchSourceBuilder", List.class, SearchRequest.Builder.class);
        method.setAccessible(true);

        // Act
        method.invoke(esUtilService, facets, builder);

        // Assert
        Map<String, Aggregation> aggregations = builder.build().aggregations();
        assertNotNull(aggregations);
        assertEquals(2, aggregations.size());
        assertTrue(aggregations.containsKey("category_agg"));
        assertTrue(aggregations.containsKey("topic_agg"));
    }

    @Test
    void testUpdateUserIndex_appendFalse_success() throws Exception {
        // Invoke private method using reflection
        Method method = EsUtilServiceImpl.class.getDeclaredMethod("updateUserIndex", String.class, String.class, Boolean.class);
        method.setAccessible(true);

        // Act
       method.invoke(esUtilService, "user123", "communityABC", true);

}

    @Test
    void testUpdateUserIndex_appendFalse_Failed() throws Exception {
        // Arrange

        Method method = EsUtilServiceImpl.class.getDeclaredMethod("updateUserIndex", String.class, String.class, Boolean.class);
        method.setAccessible(true);

        // Act
        Boolean result = (Boolean) method.invoke(esUtilService, "user123", "communityXYZ", false);

        // Assert
        assertFalse(result);
    }

    // Reflection helper
    private Object invokePrivate(String methodName, Map<String, Object> map) throws Exception {
        Method method = EsUtilServiceImpl.class.getDeclaredMethod(methodName, Map.class);
        method.setAccessible(true);
        return method.invoke(esUtilService, map);
    }

    // Reflection helper
    private void invokeAddSortToSearchSourceBuilder(SearchCriteria criteria, SearchRequest.Builder builder) throws Exception {
        Method method = EsUtilServiceImpl.class.getDeclaredMethod(
                "addSortToSearchSourceBuilder", SearchCriteria.class, SearchRequest.Builder.class);
        method.setAccessible(true);
        method.invoke(esUtilService, criteria, builder);
    }

    private Object invokePrivateMethod(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = EsUtilServiceImpl.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(esUtilService, args);
    }
}


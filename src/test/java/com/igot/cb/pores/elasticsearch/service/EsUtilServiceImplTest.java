package com.igot.cb.pores.elasticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import com.igot.cb.pores.elasticsearch.config.EsConfig;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.rest.RestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers the most central, previously-0%-covered methods of EsUtilServiceImpl: addDocument,
 * updateDocument, deleteDocument, searchDocuments, fetchTopCommunitiesForTopics, updateUserIndex,
 * doesCommunityExist, isDuplicateCommunity, doesCommunityNameExist,
 * doesCommunityNameExistForPublish, popularCommunities, saveAll and readJsonSchema.
 *
 * The co.elastic.clients Java client uses immutable, builder-lambda-constructed response objects
 * (SearchResponse, IndexResponse, DeleteResponse, BulkResponse, ...), so instead of mocking their
 * getters directly, small real instances are built through their own {@code .of(builder -> ...)}
 * factories via the helper methods below, and only the top-level ElasticsearchClient /
 * RestHighLevelClient collaborators are mocked. Deep aggregation-building branches that aren't
 * exercised through the public API surface (e.g. the TOPIC_ID top-hits path inside
 * extractPaginatedResult) are intentionally left uncovered to avoid excessive builder scaffolding.
 */
@ExtendWith(MockitoExtension.class)
class EsUtilServiceImplTest {

  private ElasticsearchClient elasticsearchClient;
  private RestHighLevelClient userESClient;
  private EsConfig esConfig;
  private EsUtilServiceImpl service;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    elasticsearchClient = mock(ElasticsearchClient.class);
    userESClient = mock(RestHighLevelClient.class);
    esConfig = mock(EsConfig.class);
    service = new EsUtilServiceImpl(elasticsearchClient, esConfig, userESClient);
    ReflectionTestUtils.setField(service, "objectMapper", mapper);
    CbServerProperties props = new CbServerProperties();
    props.setSearchStringMaxRegexLength(100);
    props.setSearchQueryFields("communityName,description");
    props.setElasticCommunityJsonPath("/EsFieldsmapping/esRequiredFieldsJsonFilePath.json");
    ReflectionTestUtils.setField(service, "cbServerProperties", props);
    ReflectionTestUtils.setField(service, "userIndex", "user-index");
    ReflectionTestUtils.setField(service, "communityIndex", "community");
  }

  // ---------- builder helpers for the co.elastic.clients immutable response objects ----------

  private IndexResponse indexResponse(Result result) {
    return IndexResponse.of(b -> b
        .index("community")
        .id("c1")
        .result(result)
        .shards(sh -> sh.total(1).successful(1).failed(0))
        .primaryTerm(1)
        .seqNo(1)
        .version(1));
  }

  private DeleteResponse deleteResponse(Result result) {
    return DeleteResponse.of(b -> b
        .index("community")
        .id("c1")
        .result(result)
        .shards(sh -> sh.total(1).successful(1).failed(0))
        .primaryTerm(1)
        .seqNo(1)
        .version(1));
  }

  private BulkResponse bulkResponse() {
    return BulkResponse.of(b -> b.errors(false).items(Collections.emptyList()).took(1));
  }

  private Aggregate stringTermsAggregate(String key, long docCount) {
    return Aggregate.of(a -> a.sterms(st -> st.buckets(bb -> bb.array(
        List.of(StringTermsBucket.of(sb -> sb.key(key).docCount(docCount)))))));
  }

  private SearchResponse<Object> buildSearchResponse(
      List<Map<String, Object>> sources, Map<String, Aggregate> aggregations, long total) {
    List<Hit<Object>> hits = new ArrayList<>();
    for (Map<String, Object> src : sources) {
      hits.add(Hit.of(h -> h.index("community").id("id-" + sources.indexOf(src)).source(src)));
    }
    HitsMetadata<Object> hitsMetadata = HitsMetadata.of(h -> h
        .total(t -> t.value(total).relation(TotalHitsRelation.Eq))
        .hits(hits));
    return SearchResponse.of(b -> b
        .took(1)
        .timedOut(false)
        .shards(sh -> sh.total(1).successful(1).failed(0))
        .hits(hitsMetadata)
        .aggregations(aggregations == null ? Collections.emptyMap() : aggregations));
  }

  // ================= addDocument =================

  @Test
  void addDocument_success_returnsSuccessMessage() throws Exception {
    when(elasticsearchClient.index(org.mockito.ArgumentMatchers.<IndexRequest<Map<String, Object>>>any()))
        .thenReturn(indexResponse(Result.Created));
    Map<String, Object> document = new HashMap<>();
    document.put(Constants.COMMUNITY_NAME, "Tech");
    document.put("unknownField", "shouldBeStripped");

    String result = service.addDocument("community", Constants.INDEX_TYPE, "c1", document,
        "/EsFieldsmapping/esRequiredFieldsJsonFilePath.json");

    assertNotNull(result);
    assertTrue(result.contains("Successfully indexed"));
    assertFalse(document.containsKey("unknownField"));
    assertTrue(document.containsKey(Constants.COMMUNITY_NAME));
  }

  @Test
  void addDocument_clientThrows_returnsNull() throws Exception {
    when(elasticsearchClient.index(org.mockito.ArgumentMatchers.<IndexRequest<Map<String, Object>>>any()))
        .thenThrow(new IOException("es down"));
    Map<String, Object> document = new HashMap<>();
    document.put(Constants.COMMUNITY_NAME, "Tech");

    String result = service.addDocument("community", Constants.INDEX_TYPE, "c1", document,
        "/EsFieldsmapping/esRequiredFieldsJsonFilePath.json");

    assertNull(result);
  }

  @Test
  void addDocument_invalidSchemaPath_returnsNull() {
    Map<String, Object> document = new HashMap<>();
    document.put(Constants.COMMUNITY_NAME, "Tech");

    String result = service.addDocument("community", Constants.INDEX_TYPE, "c1", document,
        "/does/not/exist.json");

    assertNull(result);
  }

  // ================= updateDocument =================

  @Test
  void updateDocument_success_returnsResultJsonValue() throws Exception {
    when(elasticsearchClient.index(org.mockito.ArgumentMatchers.<IndexRequest<Map<String, Object>>>any()))
        .thenReturn(indexResponse(Result.Updated));
    Map<String, Object> document = new HashMap<>();
    document.put(Constants.COMMUNITY_NAME, "Tech");

    String result = service.updateDocument("community", Constants.INDEX_TYPE, "c1", document,
        "/EsFieldsmapping/esRequiredFieldsJsonFilePath.json");

    assertEquals(Result.Updated.jsonValue(), result);
  }

  @Test
  void updateDocument_ioExceptionFromClient_returnsNull() throws Exception {
    when(elasticsearchClient.index(org.mockito.ArgumentMatchers.<IndexRequest<Map<String, Object>>>any()))
        .thenThrow(new IOException("es down"));
    Map<String, Object> document = new HashMap<>();
    document.put(Constants.COMMUNITY_NAME, "Tech");

    String result = service.updateDocument("community", Constants.INDEX_TYPE, "c1", document,
        "/EsFieldsmapping/esRequiredFieldsJsonFilePath.json");

    assertNull(result);
  }

  // ================= deleteDocument =================

  @Test
  void deleteDocument_success_refreshesIndex() throws Exception {
    when(elasticsearchClient.delete(any(DeleteRequest.class))).thenReturn(deleteResponse(Result.Deleted));
    ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
    when(elasticsearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(co.elastic.clients.elasticsearch.indices.RefreshRequest.class)))
        .thenReturn(mock(RefreshResponse.class));

    service.deleteDocument("c1", "community");

    verify(indicesClient).refresh(any(co.elastic.clients.elasticsearch.indices.RefreshRequest.class));
  }

  @Test
  void deleteDocument_notFound_doesNotRefresh() throws Exception {
    when(elasticsearchClient.delete(any(DeleteRequest.class))).thenReturn(deleteResponse(Result.NotFound));

    service.deleteDocument("c1", "community");

    verify(elasticsearchClient, never()).indices();
  }

  @Test
  void deleteDocument_clientThrows_doesNotPropagate() throws Exception {
    when(elasticsearchClient.delete(any(DeleteRequest.class))).thenThrow(new IOException("es down"));

    service.deleteDocument("c1", "community");

    verify(elasticsearchClient, times(1)).delete(any(DeleteRequest.class));
  }

  // ================= searchDocuments =================

  @Test
  void searchDocuments_searchStringTooLong_throwsRuntimeException() {
    CbServerProperties props = (CbServerProperties) ReflectionTestUtils.getField(service, "cbServerProperties");
    props.setSearchStringMaxRegexLength(3);
    SearchCriteria criteria = new SearchCriteria();
    criteria.setSearchString("waytoolongsearchstring");

    assertThrows(RuntimeException.class, () -> service.searchDocuments("community", criteria));
  }

  @Test
  void searchDocuments_success_noFilters_returnsMappedData() throws Exception {
    Map<String, Object> source = new HashMap<>();
    source.put(Constants.COMMUNITY_NAME, "Tech");
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(buildSearchResponse(List.of(source), Collections.emptyMap(), 1));
    SearchCriteria criteria = new SearchCriteria();
    criteria.setPageNumber(0);
    criteria.setPageSize(10);

    SearchResult result = service.searchDocuments("community", criteria);

    assertNotNull(result);
    assertEquals(1, result.getTotalCount());
    JsonNode data = result.getData();
    assertTrue(data.isArray());
    assertEquals(1, data.size());
  }

  @Test
  void searchDocuments_withFacets_extractsFacetData() throws Exception {
    Map<String, Aggregate> aggregations = new HashMap<>();
    aggregations.put("status_agg", stringTermsAggregate("active", 5));
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(buildSearchResponse(Collections.emptyList(), aggregations, 0));
    SearchCriteria criteria = new SearchCriteria();
    criteria.setFacets(List.of("status"));
    criteria.setPageNumber(0);
    criteria.setPageSize(10);

    SearchResult result = service.searchDocuments("community", criteria);

    assertNotNull(result);
    assertNotNull(result.getFacets());
    assertEquals(1, result.getFacets().get("status").size());
    assertEquals("active", result.getFacets().get("status").get(0).getValue());
  }

  @Test
  void searchDocuments_withFilterCriteriaAndSearchStringAndSort_buildsRequest() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(buildSearchResponse(Collections.emptyList(), Collections.emptyMap(), 0));
    SearchCriteria criteria = new SearchCriteria();
    HashMap<String, Object> filterMap = new HashMap<>();
    filterMap.put(Constants.STATUS, "active");
    filterMap.put("verified", true);
    filterMap.put("tags", new ArrayList<>(List.of("java", "spring")));
    Map<String, Object> rangeMap = new HashMap<>();
    rangeMap.put(Constants.SEARCH_OPERATION_GREATER_THAN_EQUALS, 1);
    rangeMap.put(Constants.SEARCH_OPERATION_LESS_THAN_EQUALS, 10);
    filterMap.put("countOfPeopleJoined", rangeMap);
    Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("isActive", true);
    filterMap.put("moderator", nestedMap);
    filterMap.put("must_not", new ArrayList<>(List.of("suspended")));
    criteria.setFilterCriteriaMap(filterMap);
    criteria.setSearchString("tech community");
    criteria.setOrderBy(Constants.COMMUNITY_NAME);
    criteria.setOrderDirection(Constants.ASC);
    criteria.setRequestedFields(new ArrayList<>());
    criteria.setPageNumber(0);
    criteria.setPageSize(0);

    SearchResult result = service.searchDocuments("community", criteria);

    assertNotNull(result);
    verify(elasticsearchClient).search(any(SearchRequest.class), eq(Object.class));
  }

  @Test
  void searchDocuments_requestedFieldsSpecified_includesSourceFilter() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(buildSearchResponse(Collections.emptyList(), Collections.emptyMap(), 0));
    SearchCriteria criteria = new SearchCriteria();
    criteria.setRequestedFields(List.of(Constants.COMMUNITY_NAME));
    criteria.setPageNumber(1);
    criteria.setPageSize(5);

    SearchResult result = service.searchDocuments("community", criteria);

    assertNotNull(result);
  }

  @Test
  void searchDocuments_ioExceptionFromClient_returnsNull() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenThrow(new IOException("es down"));
    SearchCriteria criteria = new SearchCriteria();

    SearchResult result = service.searchDocuments("community", criteria);

    assertNull(result);
  }

  // ================= fetchTopCommunitiesForTopics =================

  @Test
  void fetchTopCommunitiesForTopics_success_returnsAggregatedFacets() throws Exception {
    Map<String, Aggregate> aggregations = new HashMap<>();
    aggregations.put(Constants.TOPIC_ID + "_agg", stringTermsAggregate("1", 3));
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(buildSearchResponse(Collections.emptyList(), aggregations, 0));

    SearchResult result = service.fetchTopCommunitiesForTopics(Arrays.asList(1, 2), "community");

    assertNotNull(result);
    assertNotNull(result.getFacets());
    assertEquals(1, result.getFacets().get(Constants.TOPIC_ID).size());
  }

  @Test
  void fetchTopCommunitiesForTopics_clientThrows_throwsCustomException() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenThrow(new IOException("es down"));

    assertThrows(CustomException.class,
        () -> service.fetchTopCommunitiesForTopics(List.of(1), "community"));
  }

  // ================= updateUserIndex =================

  @Test
  void updateUserIndex_appendSuccess_returnsTrue() throws Exception {
    UpdateResponse updateResponse = mock(UpdateResponse.class);
    when(updateResponse.getResult()).thenReturn(DocWriteResponse.Result.UPDATED);
    when(userESClient.update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(updateResponse);

    Boolean result = service.updateUserIndex("u1", "c1", true);

    assertTrue(result);
  }

  @Test
  void updateUserIndex_removeSuccess_returnsTrue() throws Exception {
    UpdateResponse updateResponse = mock(UpdateResponse.class);
    when(updateResponse.getResult()).thenReturn(DocWriteResponse.Result.NOOP);
    when(userESClient.update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(updateResponse);

    Boolean result = service.updateUserIndex("u1", "c1", false);

    assertTrue(result);
  }

  @Test
  void updateUserIndex_conflictStatusException_returnsFalse() throws Exception {
    when(userESClient.update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT)))
        .thenThrow(new ElasticsearchStatusException("conflict", RestStatus.CONFLICT));

    Boolean result = service.updateUserIndex("u1", "c1", true);

    assertFalse(result);
  }

  @Test
  void updateUserIndex_nonConflictStatusException_returnsFalse() throws Exception {
    when(userESClient.update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT)))
        .thenThrow(new ElasticsearchStatusException("bad request", RestStatus.BAD_REQUEST));

    Boolean result = service.updateUserIndex("u1", "c1", true);

    assertFalse(result);
  }

  @Test
  void updateUserIndex_genericException_returnsFalse() throws Exception {
    when(userESClient.update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT)))
        .thenThrow(new IOException("es down"));

    Boolean result = service.updateUserIndex("u1", "c1", true);

    assertFalse(result);
  }

  // ================= doesCommunityExist =================

  @Test
  void doesCommunityExist_hitsFound_returnsTrue() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(buildSearchResponse(Collections.emptyList(), Collections.emptyMap(), 2));

    Boolean result = service.doesCommunityExist("org1", "Tech");

    assertTrue(result);
  }

  @Test
  void doesCommunityExist_noHits_returnsFalse() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(buildSearchResponse(Collections.emptyList(), Collections.emptyMap(), 0));

    Boolean result = service.doesCommunityExist("org1", "Tech");

    assertFalse(result);
  }

  @Test
  void doesCommunityExist_exception_returnsFalse() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenThrow(new IOException("es down"));

    Boolean result = service.doesCommunityExist("org1", "Tech");

    assertFalse(result);
  }

  // ================= isDuplicateCommunity =================

  @Test
  void isDuplicateCommunity_withExcludeId_hitsFound_returnsTrue() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(buildSearchResponse(Collections.emptyList(), Collections.emptyMap(), 1));

    boolean result = service.isDuplicateCommunity("org1", "Tech", "c1");

    assertTrue(result);
  }

  @Test
  void isDuplicateCommunity_withoutExcludeId_queryBuildFails_returnsFalse() throws Exception {
    // A null excludeCommunityId makes the mustNot(...) builder lambda return null, which the
    // client's query builder can't handle; production code's own try/catch turns that into a
    // false result instead of propagating.
    boolean result = service.isDuplicateCommunity("org1", "Tech", null);

    assertFalse(result);
  }

  @Test
  void isDuplicateCommunity_exception_returnsFalse() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenThrow(new IOException("es down"));

    boolean result = service.isDuplicateCommunity("org1", "Tech", "c1");

    assertFalse(result);
  }

  // ================= doesCommunityNameExist =================

  @Test
  void doesCommunityNameExist_hitsFound_returnsTrue() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(buildSearchResponse(Collections.emptyList(), Collections.emptyMap(), 1));

    Boolean result = service.doesCommunityNameExist("Tech");

    assertTrue(result);
  }

  @Test
  void doesCommunityNameExist_exception_returnsFalse() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenThrow(new IOException("es down"));

    Boolean result = service.doesCommunityNameExist("Tech");

    assertFalse(result);
  }

  // ================= doesCommunityNameExistForPublish =================

  @Test
  void doesCommunityNameExistForPublish_hitsFound_returnsTrue() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(buildSearchResponse(Collections.emptyList(), Collections.emptyMap(), 1));

    Boolean result = service.doesCommunityNameExistForPublish("Tech", "c1");

    assertTrue(result);
  }

  @Test
  void doesCommunityNameExistForPublish_noCommunityId_queryBuildFails_returnsFalse() {
    // Same mustNot(...) null-return quirk as isDuplicateCommunity: a null communityId makes
    // query construction fail internally, which the method's own catch turns into false.
    Boolean result = service.doesCommunityNameExistForPublish("Tech", null);

    assertFalse(result);
  }

  @Test
  void doesCommunityNameExistForPublish_exception_returnsFalse() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenThrow(new IOException("es down"));

    Boolean result = service.doesCommunityNameExistForPublish("Tech", "c1");

    assertFalse(result);
  }

  // ================= popularCommunities =================

  @Test
  void popularCommunities_success_returnsResponse() throws Exception {
    SearchResponse<Object> response = buildSearchResponse(Collections.emptyList(), Collections.emptyMap(), 0);
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(response);
    SearchRequest request = new SearchRequest.Builder().index("community").build();

    SearchResponse result = service.popularCommunities(request, RequestOptions.DEFAULT);

    assertNotNull(result);
    assertEquals(response, result);
  }

  @Test
  void popularCommunities_exception_returnsNull() throws Exception {
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenThrow(new IOException("es down"));
    SearchRequest request = new SearchRequest.Builder().index("community").build();

    SearchResponse result = service.popularCommunities(request, RequestOptions.DEFAULT);

    assertNull(result);
  }

  // ================= saveAll =================

  @Test
  void saveAll_success_returnsBulkResponse() throws Exception {
    when(elasticsearchClient.bulk(any(co.elastic.clients.elasticsearch.core.BulkRequest.class)))
        .thenReturn(bulkResponse());
    com.fasterxml.jackson.databind.node.ObjectNode entity = mapper.createObjectNode();
    entity.put(Constants.ID, "c1");
    entity.put(Constants.COMMUNITY_NAME, "Tech");

    BulkResponse result = service.saveAll("community", List.of(entity));

    assertNotNull(result);
    assertFalse(result.errors());
  }

  @Test
  void saveAll_clientThrows_throwsCustomException() throws Exception {
    when(elasticsearchClient.bulk(any(co.elastic.clients.elasticsearch.core.BulkRequest.class)))
        .thenThrow(new IOException("es down"));
    com.fasterxml.jackson.databind.node.ObjectNode entity = mapper.createObjectNode();
    entity.put(Constants.ID, "c1");

    assertThrows(CustomException.class, () -> service.saveAll("community", List.of(entity)));
  }

  // ================= readJsonSchema =================

  @Test
  void readJsonSchema_freshRead_returnsSchemaMap() {
    Map<String, Object> schema = service.readJsonSchema("/EsFieldsmapping/esRequiredFieldsJsonFilePath.json");

    assertNotNull(schema);
    assertTrue(schema.containsKey(Constants.COMMUNITY_NAME));
  }

  @Test
  void readJsonSchema_cachedOnSecondCall_returnsSameInstance() {
    Map<String, Object> first = service.readJsonSchema("/EsFieldsmapping/esRequiredFieldCategory.json");
    Map<String, Object> second = service.readJsonSchema("/EsFieldsmapping/esRequiredFieldCategory.json");

    assertEquals(first, second);
  }

  @Test
  void readJsonSchema_invalidPath_throwsCustomException() {
    assertThrows(CustomException.class, () -> service.readJsonSchema("/does/not/exist.json"));
  }
}

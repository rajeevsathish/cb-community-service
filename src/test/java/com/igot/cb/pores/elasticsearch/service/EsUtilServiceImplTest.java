package com.igot.cb.pores.elasticsearch.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;
import static org.yaml.snakeyaml.tokens.Token.ID.Key;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.igot.cb.pores.elasticsearch.config.EsConfig;
import com.igot.cb.pores.elasticsearch.dto.FacetDTO;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.Constants;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class EsUtilServiceImplTest {

    private final ElasticsearchIndicesClient indicesClient = Mockito.mock(ElasticsearchIndicesClient.class);
    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EsConfig esConfig;

    @InjectMocks
    private EsUtilServiceImpl esUtilService;
    @Mock
    private SearchRequest searchRequest;

    @Mock
    private RequestOptions requestOptions;

    @Mock
    private SearchResponse<Object> mockSearchResponse;

    @Mock
    private HitsMetadata<Object> mockHits;

    @Mock
    private TotalHits totalHits;

    @Mock
    private BulkResponse bulkResponse;

    @BeforeEach
    void setUp() {
        // Inject the mocked ObjectMapper into esUtilService
        ReflectionTestUtils.setField(esUtilService, "objectMapper", objectMapper);
    }

    @Test
    void testAddDocument_success() throws Exception {
        // Prepare input document with one valid and one invalid field
        Map<String, Object> document = new HashMap<>();
        document.put("validField", "value1");
        document.put("invalidField", "value2");
        // Schema only allows 'validField'
        Map<String, Object> schemaMap = new HashMap<>();
        schemaMap.put("validField", "type");

        // Stub objectMapper.readValue for nullable InputStream
        when(objectMapper.readValue(nullable(InputStream.class), any(TypeReference.class)))
                .thenReturn(schemaMap);
        esUtilService.addDocument("test_index", "_doc", "123", document, "/schema.json");
        assertFalse(document.containsKey("invalidField"), "Invalid field should be removed");
        assertTrue(document.containsKey("validField"), "Valid field should remain");
    }

    @Test
    void testAddDocument_readSchemaFailure() throws Exception {
        // Prepare a document
        Map<String, Object> document = new HashMap<>();
        document.put("anyField", "value");

        // Stub objectMapper.readValue to throw IOException
        when(objectMapper.readValue(nullable(InputStream.class), any(TypeReference.class)))
                .thenThrow(new IOException("schema error"));

        // Execute
        String result = esUtilService.addDocument("idx", "_doc", "1", document, "/schema.json");

        // On exception, method should return null
        assertNull(result, "Should return null on schema read error");

        // Verify readValue was invoked
        verify(objectMapper).readValue(nullable(InputStream.class), any(TypeReference.class));
        // Ensure index was never called due to exception
        verify(elasticsearchClient, never()).index((IndexRequest<Object>) any());
    }

    @Test
    void testAddDocument_indexFailure() throws Exception {
        // Prepare a valid document
        Map<String, Object> document = new HashMap<>();
        document.put("field1", "v1");

        Map<String, Object> schemaMap = Map.of("field1", "type");
        when(objectMapper.readValue(nullable(InputStream.class), any(TypeReference.class)))
                .thenReturn(schemaMap);

        // Execute
        String result = esUtilService.addDocument("idx", "_doc", "42", document, "/schema.json");

        // On exception, method should return null
        assertNull(result, "Should return null on index exception");

        // Verify interactions
        verify(objectMapper).readValue(nullable(InputStream.class), any(TypeReference.class));
    }

    @Test
    void testUpdated_successfullyUpdated() throws Exception {
        // Given
        String indexName = "test-index";
        String documentId = "123";
        String jsonFilePath = "/test-schema.json";

        Map<String, Object> schemaMap = Map.of(
                "field1", "string",
                "field2", "integer"
        );

        Map<String, Object> inputDocument = new HashMap<>();
        inputDocument.put("field1", "value1");
        inputDocument.put("field2", 123);
        inputDocument.put("extraField", "shouldBeRemoved");

        // Mock schema validation
        when(objectMapper.readValue(
                any(InputStream.class),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(schemaMap);

        // Mock Elasticsearch response
        IndexResponse mockResponse = mock(IndexResponse.class);
        when(mockResponse.result()).thenReturn(Result.Updated); // or Result.Created
        when(elasticsearchClient.index(any(IndexRequest.class))).thenReturn(mockResponse);

        // When
        String result = esUtilService.updateDocument(indexName, "type", documentId, inputDocument, jsonFilePath);

        // Then
        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("updated") || result.toLowerCase().contains("created"));
        verify(elasticsearchClient, times(1)).index(any(IndexRequest.class));
    }


    @Test
    void deleteDocument_VerifyRefreshRequestParameters() throws IOException {
        String documentId = "test-doc-id";
        String esIndexName = "test-index";
        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        Result mockResult = mock(Result.class);
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(mockResult.jsonValue()).thenReturn("DELETED");
        when(mockDeleteResponse.result()).thenReturn(mockResult);
        when(elasticsearchClient.delete(any(DeleteRequest.class))).thenReturn(mockDeleteResponse);
        ArgumentCaptor<RefreshRequest> refreshRequestCaptor = ArgumentCaptor.forClass(RefreshRequest.class);
        when(indicesClient.refresh(refreshRequestCaptor.capture())).thenReturn(null);
        esUtilService.deleteDocument(documentId, esIndexName);
        RefreshRequest capturedRequest = refreshRequestCaptor.getValue();
        assertEquals(esIndexName, capturedRequest.index().get(0));
    }


    @Test
    void searchDocuments_Success() throws IOException {
        // Test data setup
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        // Mock SearchResponse
        SearchResponse<Object> mockSearchResponse1 = mock(SearchResponse.class);
        HitsMetadata<Object> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits1 = mock(TotalHits.class);
        List<Hit<Object>> hits = new ArrayList<>();

        // Configure mock responses
        when(totalHits1.value()).thenReturn(1L);
        when(hitsMetadata.total()).thenReturn(totalHits1);
        when(hitsMetadata.hits()).thenReturn(hits);
        when(mockSearchResponse1.hits()).thenReturn(hitsMetadata);

        // Mock elasticsearch client search method
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse1);

        // Execute
        SearchResult result = esUtilService.searchDocuments(esIndexName, searchCriteria);

        // Verify
        assertNotNull(result);
        assertEquals(1L, result.getTotalCount());
    }

    @Test
    void searchDocuments_shouldReturnNull_onIOException() throws IOException {
        // Arrange
        String indexName = "test-index";
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPageNumber(0);
        criteria.setPageSize(10);

        // Force the Elasticsearch client to throw an IOException
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new IOException("ES failed"));

        // Act
        SearchResult result = esUtilService.searchDocuments(indexName, criteria);

        // Assert
        assertNull(result, "Expected result to be null due to IOException");
    }

    @Test
    void testSearchDocuments_success() throws IOException {
        String index = "test_index";

        // Mock hit
        Hit<Object> hit1 = new Hit.Builder<>().id("doc1").index("index").source(Map.of("field", "value")).build();
        List<Hit<Object>> hitList = Arrays.asList(hit1);

        // Mock total hits
        TotalHits totalHits1 = new TotalHits.Builder().value(1L).relation(TotalHitsRelation.Eq).build();

        // Mock hits metadata
        HitsMetadata<Object> mockHitsMetadata = Mockito.mock(HitsMetadata.class);
        when(mockHitsMetadata.total()).thenReturn(totalHits1);
        when(mockHitsMetadata.hits()).thenReturn(hitList);

        // Mock response
        SearchResponse<Object> mockResponse = Mockito.mock(SearchResponse.class);
        when(mockResponse.hits()).thenReturn(mockHitsMetadata);

        // Mock client
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(mockResponse);

        // Build searchCriteria
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setRequestedFields(List.of("field"));
        searchCriteria.setFilterCriteriaMap(new HashMap<>());

        SearchResult result = esUtilService.searchDocuments(index, searchCriteria);

        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
    }

    @Test
    void testExtractFacetDataForList_withReflection_success() throws Exception {
        // Arrange
        SearchResponse<Object> mockSearchResponse = mock(SearchResponse.class);
        SearchCriteria mockSearchCriteria1 = mock(SearchCriteria.class);
        String facetField = "testField";
        String aggField = facetField + "_agg";

        // Set facet field
        when(mockSearchCriteria1.getFacets()).thenReturn(List.of(facetField));

        // Mock FieldValue returned from bucket.key()
        FieldValue mockFieldValue = mock(FieldValue.class);
        when(mockFieldValue.stringValue()).thenReturn("testKey");

        // Mock bucket
        StringTermsBucket mockBucket = mock(StringTermsBucket.class);
        when(mockBucket.key()).thenReturn(mockFieldValue);
        when(mockBucket.docCount()).thenReturn(5L);

        // Mock top hits nested aggregation
        Aggregate topHitsAgg = mock(Aggregate.class);
        when(topHitsAgg.isTopHits()).thenReturn(true);

        // Mock Hit with source map
        Hit<JsonData> mockHit = mock(Hit.class);
        JsonData mockJsonData = mock(JsonData.class);
        Map<String, Object> sourceMap = Map.of("topicId", "topic-1");
        when(mockHit.source()).thenReturn(mockJsonData);
        when(mockJsonData.to(Map.class)).thenReturn(sourceMap);

        HitsMetadata<JsonData> mockHitsMetadata = mock(HitsMetadata.class);
        when(mockHitsMetadata.hits()).thenReturn(List.of(mockHit));

        TopHitsAggregate mockTopHits = mock(TopHitsAggregate.class);
        when(mockTopHits.hits()).thenReturn(mockHitsMetadata);
        when(topHitsAgg.topHits()).thenReturn(mockTopHits);

        when(mockBucket.aggregations()).thenReturn(Map.of("top_hits#topNames", topHitsAgg));

        // Mock Buckets
        Buckets<StringTermsBucket> mockBuckets = mock(Buckets.class);
        when(mockBuckets.array()).thenReturn(List.of(mockBucket));

        // Mock string terms aggregate
        StringTermsAggregate mockStringTermsAgg = mock(StringTermsAggregate.class);
        when(mockStringTermsAgg.buckets()).thenReturn(mockBuckets);

        // Mock parent aggregate
        Aggregate mockAggregate = mock(Aggregate.class);
        when(mockAggregate.isSterms()).thenReturn(true);
        when(mockAggregate.sterms()).thenReturn(mockStringTermsAgg);

        // Mock search response aggregations
        when(mockSearchResponse.aggregations()).thenReturn(Map.of(aggField, mockAggregate));

        Map<String, List<FacetDTO>> result = (Map<String, List<FacetDTO>>) ReflectionTestUtils.invokeMethod(
                esUtilService, "extractFacetDataForList", mockSearchResponse, mockSearchCriteria1);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey(facetField));
        List<FacetDTO> facets = result.get(facetField);
        assertEquals(1, facets.size());
        assertEquals(5L, facets.get(0).getCount());
    }

    @Test
    void testDoesCommunityNameExistForPublish_true() throws Exception {
        // Given
        String communityName = "Test Community";
        String communityId = "123";

        TotalHits totalHits1 = mock(TotalHits.class);
        when(totalHits1.value()).thenReturn(1L);

        HitsMetadata<Object> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.total()).thenReturn(totalHits1);

        SearchResponse<Object> mockResponse = mock(SearchResponse.class);
        when(mockResponse.hits()).thenReturn(hitsMetadata);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockResponse);

        // When
        Boolean exists = esUtilService.doesCommunityNameExistForPublish(communityName, communityId);

        // Then
        assertTrue(exists);
    }

    @Test
    void testDoesCommunityNameExistForPublish_false() throws Exception {
        // Given
        String communityName = "No Match";
        String communityId = "456";

        TotalHits totalHits1 = mock(TotalHits.class);
        when(totalHits1.value()).thenReturn(0L);

        HitsMetadata<Object> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.total()).thenReturn(totalHits1);

        SearchResponse<Object> mockResponse = mock(SearchResponse.class);
        when(mockResponse.hits()).thenReturn(hitsMetadata);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockResponse);

        // When
        Boolean exists = esUtilService.doesCommunityNameExistForPublish(communityName, communityId);

        // Then
        assertFalse(exists);
    }

    @Test
    void testDoesCommunityNameExistForPublish_exception() throws Exception {
        // Given
        String communityName = "Exception Name";
        String communityId = "999";

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new RuntimeException("ES down"));

        // When
        Boolean exists = esUtilService.doesCommunityNameExistForPublish(communityName, communityId);

        // Then
        assertFalse(exists); // fallback behavior on exception
    }

    @Test
    void testPopularCommunities_success() throws Exception {
        SearchResponse<Object> mockResponse = mock(SearchResponse.class);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(mockResponse);

        SearchResponse result = esUtilService.popularCommunities(searchRequest, requestOptions);

        assertNotNull(result);
        assertEquals(mockResponse, result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testPopularCommunities_exception() throws Exception {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new RuntimeException("Elasticsearch error"));

        SearchResponse result = esUtilService.popularCommunities(searchRequest, requestOptions);

        assertNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testDoesCommunityNameExist_whenExists_returnsTrue() throws Exception {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        when(mockSearchResponse.hits()).thenReturn(mockHits);
        when(mockHits.total()).thenReturn(totalHits);
        when(totalHits.value()).thenReturn(5L); // documents exist

        Boolean result = esUtilService.doesCommunityNameExist("TestCommunity");

        assertTrue(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testDoesCommunityNameExist_whenNotExists_returnsFalse() throws Exception {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        when(mockSearchResponse.hits()).thenReturn(mockHits);
        when(mockHits.total()).thenReturn(totalHits);
        when(totalHits.value()).thenReturn(0L); // no documents

        Boolean result = esUtilService.doesCommunityNameExist("UnknownCommunity");

        assertFalse(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testDoesCommunityNameExist_whenExceptionThrown_returnsFalse() throws Exception {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new RuntimeException("Elasticsearch error"));

        Boolean result = esUtilService.doesCommunityNameExist("ErrorCommunity");

        assertFalse(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }


    @Test
    void testIsDuplicateCommunity_whenDocumentExists_shouldReturnTrue() throws Exception {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(mockSearchResponse.hits()).thenReturn(mockHits);
        when(mockHits.total()).thenReturn(totalHits);
        when(totalHits.value()).thenReturn(3L);

        boolean result = esUtilService.isDuplicateCommunity("org1", "CommunityX", "comm123");
        assertTrue(result);

        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testIsDuplicateCommunity_whenNoDocumentExists_shouldReturnFalse() throws Exception {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(mockSearchResponse.hits()).thenReturn(mockHits);
        when(mockHits.total()).thenReturn(totalHits);
        when(totalHits.value()).thenReturn(0L);

        boolean result = esUtilService.isDuplicateCommunity("org1", "CommunityX", "comm123");
        assertFalse(result);

        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testIsDuplicateCommunity_whenExceptionThrown_shouldReturnFalse() throws Exception {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new RuntimeException("ES failure"));

        boolean result = esUtilService.isDuplicateCommunity("org1", "CommunityX", "comm123");
        assertFalse(result);

        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }


    @Test
    void testDoesCommunityExist_shouldReturnTrue_whenHitsFound() throws IOException {
        // Arrange
        TotalHits totalHits1 = new TotalHits.Builder().value(1L).relation(TotalHitsRelation.Eq).build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(mockSearchResponse.hits()).thenReturn(mockHits);
        when(mockHits.total()).thenReturn(totalHits1);

        // Act
        Boolean result = esUtilService.doesCommunityExist("org123", "Test Community");

        // Assert
        assertTrue(result);
    }

    @Test
    void testDoesCommunityExist_shouldReturnFalse_whenExceptionOccurs() throws IOException {
        // Arrange
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new IOException("Simulated exception"));

        // Act
        Boolean result = esUtilService.doesCommunityExist("org123", "Test Community");

        // Assert
        assertFalse(result);
    }

    @Test
    void testSaveAll_shouldReturnBulkResponse_whenSuccessful() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        JsonNode mockJsonNode = mock(JsonNode.class);
        JsonNode idNode = mock(JsonNode.class);
        when(mockJsonNode.get(Constants.ID)).thenReturn(idNode);
        when(idNode.asText()).thenReturn("123");

        Map<String, Object> mockEntityMap = new HashMap<>();
        when(objectMapper.convertValue(eq(mockJsonNode), eq(Map.class))).thenReturn(mockEntityMap);

        List<JsonNode> entities = List.of(mockJsonNode);

        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        // Act
        BulkResponse result = esUtilService.saveAll(esIndexName, entities);

        // Assert
        assertNotNull(result);
        assertEquals(bulkResponse, result);
    }

    @Test
    void testSaveAll_shouldThrowCustomException_whenExceptionOccurs() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        JsonNode mockJsonNode = mock(JsonNode.class);
        JsonNode idNode = mock(JsonNode.class);
        when(mockJsonNode.get(Constants.ID)).thenReturn(idNode);
        when(idNode.asText()).thenReturn("123");

        when(objectMapper.convertValue(eq(mockJsonNode), eq(Map.class))).thenThrow(new RuntimeException("Mapping failed"));

        List<JsonNode> entities = List.of(mockJsonNode);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            esUtilService.saveAll(esIndexName, entities);
        });

        assertEquals("error bulk uploading", exception.getCode());
        assertEquals("Mapping failed", exception.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatusCode());
    }

    @Test
    void testFetchTopCommunitiesForTopics_throwsCustomException() throws IOException {
        List<Integer> parentTopics = List.of(1, 2);
        String indexName = "test_index";

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new IOException("Elasticsearch error"));

        CustomException exception = assertThrows(CustomException.class,
                () -> esUtilService.fetchTopCommunitiesForTopics(parentTopics, indexName));

        assertEquals("Error while processing", exception.getCode());
    }

}

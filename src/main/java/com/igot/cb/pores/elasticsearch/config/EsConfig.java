package com.igot.cb.pores.elasticsearch.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@Slf4j
public class EsConfig  {
    @Value("${elasticsearch.host}")
    private String elasticsearchHost;

    @Value("${elasticsearch.port}")
    private int elasticsearchPort;

    @Value("${elasticsearch.username}")
    private String elasticsearchUsername;

    @Value("${elasticsearch.password}")
    private String elasticsearchPassword;

    @Value("${elasticsearch.sbESClient.host}")
    private String sbESClientHost;

    @Value("${elasticsearch.sbESClient.port}")
    private String sbESClientPort;

    @Value("${elasticsearch.sbESClient.username}")
    private String sbESClientUsername;

    @Value("${elasticsearch.sbESClient.password}")
    private String sbESClientPassword;

//    @Override
    @Bean(name = "elasticsearchClient")
    public ElasticsearchClient elasticsearchClient() {
        return createClient(elasticsearchHost, elasticsearchPort, elasticsearchUsername, elasticsearchPassword);
    }

    @Bean(name = "sbESClient")
    public ElasticsearchClient sbESClient() {
        String[] splitedHost = sbESClientHost.split(",");
        String[] splitedPort = sbESClientPort.split(",");

        HttpHost[] httpHosts = new HttpHost[splitedHost.length];
        for (int i = 0; i < splitedHost.length; i++) {
            httpHosts[i] = new HttpHost(splitedHost[i], Integer.parseInt(splitedPort[i]), "http");
        }

        RestClientBuilder builder = RestClient.builder(httpHosts)
            .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(5000) // 5 seconds connect timeout
                .setSocketTimeout(60000)) // 60 seconds socket timeout
            .setDefaultHeaders(new org.apache.http.Header[]{
                new org.apache.http.message.BasicHeader("Content-Type", "application/json"),
                new org.apache.http.message.BasicHeader("X-Elastic-Product", "Elasticsearch")});

        RestClient restClient = builder.build();
        ElasticsearchTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        ElasticsearchClient client = new ElasticsearchClient(elasticsearchTransport);
        log.info("ElasticsearchConfig:: sbESClient initialisation done.");
        return client;
    }

    private ElasticsearchClient createClient(String host, int port, String username, String password) {
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "http"))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider).addInterceptorLast((HttpResponseInterceptor) (response, context) ->
                response.addHeader("X-Elastic-Product", "Elasticsearch")))
            .setDefaultHeaders(new org.apache.http.Header[]{
                new org.apache.http.message.BasicHeader("Content-Type", "application/json"),
                new org.apache.http.message.BasicHeader("X-Elastic-Product", "Elasticsearch")});
        RestClient restClient = builder.build();
        ElasticsearchTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        ElasticsearchClient client = new ElasticsearchClient(elasticsearchTransport);
        return client;
    }
}

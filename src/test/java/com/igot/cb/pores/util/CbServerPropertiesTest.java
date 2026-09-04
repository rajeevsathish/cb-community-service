package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CbServerPropertiesTest {

  @Test
  void gettersAndSetters_roundTripAllFields() {
    CbServerProperties props = new CbServerProperties();

    props.setSearchResultRedisTtl(3600L);
    props.setSearchStringMaxRegexLength(100);
    props.setElasticCommunityJsonPath("/es/community.json");
    props.setElasticCommunityCategoryJsonPath("/es/category.json");
    props.setReporCommunityUserLimit(500);
    props.setDiscussionCloudFolderName("discussion");
    props.setDiscussionContainerName("container1");
    props.setCloudStorageTypeName("azure");
    props.setCloudStorageKey("key1");
    props.setCloudStorageSecret("secret1");
    props.setCloudStorageEndpoint("https://storage.example.org");
    props.setCommunityModeratorTemplate("moderator-template");
    props.setSupportEmail("support@example.org");
    props.setNotifyServiceHost("https://notify.example.org");
    props.setNotifyServicePathAsync("/v1/notify");
    props.setDomainUrl("https://example.org");
    props.setFixedCommunityUrl("/community/");
    props.setSearchQueryFields("name,description");
    props.setRedisScanCountSize(200);
    props.setRedisCommunityUserDataTtlSeconds(600L);
    props.setCommunityAdminJoinMaxUser(1000);

    assertEquals(3600L, props.getSearchResultRedisTtl());
    assertEquals(100, props.getSearchStringMaxRegexLength());
    assertEquals("/es/community.json", props.getElasticCommunityJsonPath());
    assertEquals("/es/category.json", props.getElasticCommunityCategoryJsonPath());
    assertEquals(500, props.getReporCommunityUserLimit());
    assertEquals("discussion", props.getDiscussionCloudFolderName());
    assertEquals("container1", props.getDiscussionContainerName());
    assertEquals("azure", props.getCloudStorageTypeName());
    assertEquals("key1", props.getCloudStorageKey());
    assertEquals("secret1", props.getCloudStorageSecret());
    assertEquals("https://storage.example.org", props.getCloudStorageEndpoint());
    assertEquals("moderator-template", props.getCommunityModeratorTemplate());
    assertEquals("support@example.org", props.getSupportEmail());
    assertEquals("https://notify.example.org", props.getNotifyServiceHost());
    assertEquals("/v1/notify", props.getNotifyServicePathAsync());
    assertEquals("https://example.org", props.getDomainUrl());
    assertEquals("/community/", props.getFixedCommunityUrl());
    assertEquals("name,description", props.getSearchQueryFields());
    assertEquals(200, props.getRedisScanCountSize());
    assertEquals(600L, props.getRedisCommunityUserDataTtlSeconds());
    assertEquals(1000, props.getCommunityAdminJoinMaxUser());
  }
}

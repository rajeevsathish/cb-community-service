package com.igot.cb.pores.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CbServerPropertiesTest {

    @Test
    void testAllGettersAndSetters() {
        CbServerProperties props = new CbServerProperties();

        props.setSearchResultRedisTtl(3600L);
        props.setSearchStringMaxRegexLength(100);
        props.setElasticCommunityJsonPath("path/community.json");
        props.setElasticCommunityCategoryJsonPath("path/category.json");
        props.setReporCommunityUserLimit(10);
        props.setDiscussionCloudFolderName("folder");
        props.setDiscussionContainerName("container");
        props.setCloudStorageTypeName("s3");
        props.setCloudStorageKey("key123");
        props.setCloudStorageSecret("secret456");
        props.setCloudStorageEndpoint("https://s3.amazonaws.com");
        props.setCommunityModeratorTemplate("moderator@email.com");
        props.setSupportEmail("support@domain.com");
        props.setNotifyServiceHost("http://notify-service");
        props.setNotifyServicePathAsync("/notify/async");
        props.setDomainUrl("https://domain.com");
        props.setFixedCommunityUrl("https://domain.com/community");

        assertEquals(3600L, props.getSearchResultRedisTtl());
        assertEquals(100, props.getSearchStringMaxRegexLength());
        assertEquals("path/community.json", props.getElasticCommunityJsonPath());
        assertEquals("path/category.json", props.getElasticCommunityCategoryJsonPath());
        assertEquals(10, props.getReporCommunityUserLimit());
        assertEquals("folder", props.getDiscussionCloudFolderName());
        assertEquals("container", props.getDiscussionContainerName());
        assertEquals("s3", props.getCloudStorageTypeName());
        assertEquals("key123", props.getCloudStorageKey());
        assertEquals("secret456", props.getCloudStorageSecret());
        assertEquals("https://s3.amazonaws.com", props.getCloudStorageEndpoint());
        assertEquals("moderator@email.com", props.getCommunityModeratorTemplate());
        assertEquals("support@domain.com", props.getSupportEmail());
        assertEquals("http://notify-service", props.getNotifyServiceHost());
        assertEquals("/notify/async", props.getNotifyServicePathAsync());
        assertEquals("https://domain.com", props.getDomainUrl());
        assertEquals("https://domain.com/community", props.getFixedCommunityUrl());
    }
}

package com.igot.cb.community.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;


public class CommunityEntityTest {

    @Test
    void testAllArgsConstructorAndGettersSetters() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree("{\"name\":\"testCommunity\"}");
        Timestamp now = new Timestamp(System.currentTimeMillis());

        CommunityEntity entity = new CommunityEntity(
                "cid1",
                data,
                now,
                now,
                "user1",
                true
        );

        assertEquals("cid1", entity.getCommunityId());
        assertEquals(data, entity.getData());
        assertEquals(now, entity.getCreatedOn());
        assertEquals(now, entity.getUpdatedOn());
        assertEquals("user1", entity.getCreated_by());
        assertTrue(entity.isActive());

        JsonNode newData = mapper.readTree("{\"name\":\"updated\"}");
        entity.setCommunityId("cid2");
        entity.setData(newData);
        entity.setCreatedOn(new Timestamp(0));
        entity.setUpdatedOn(new Timestamp(0));
        entity.setCreated_by("user2");
        entity.setActive(false);

        assertEquals("cid2", entity.getCommunityId());
        assertEquals(newData, entity.getData());
        assertEquals("user2", entity.getCreated_by());
        assertFalse(entity.isActive());
    }

    @Test
    void testNoArgsConstructor() {
        CommunityEntity entity = new CommunityEntity();
        assertNotNull(entity);
        entity.setCommunityId("cid");
        assertEquals("cid", entity.getCommunityId());
    }

    @Test
    void testEqualsAndHashCode() {
        CommunityEntity e1 = new CommunityEntity();
        CommunityEntity e2 = new CommunityEntity();
        e1.setCommunityId("same");
        e2.setCommunityId("same");
        assertNotEquals(e1, e2);
        assertNotEquals(e1.hashCode(), e2.hashCode());
    }


    @Test
    void testToStringNotNull() {
        CommunityEntity entity = new CommunityEntity();
        assertNotNull(entity.toString());
    }
}

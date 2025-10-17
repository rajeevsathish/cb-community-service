package com.igot.cb.pores.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NotificationAsyncRequestTest {

    @Test
    void testGettersAndSetters() {
        NotificationAsyncRequest request = new NotificationAsyncRequest();

        String type = "EMAIL";
        int priority = 5;
        Map<String, Object> action = new HashMap<>();
        action.put("actionType", "SEND");
        List<String> ids = Arrays.asList("id1", "id2");
        List<String> copyEmails = Arrays.asList("cc1@example.com", "cc2@example.com");

        request.setType(type);
        request.setPriority(priority);
        request.setAction(action);
        request.setIds(ids);
        request.setCopyEmail(copyEmails);

        assertEquals(type, request.getType());
        assertEquals(priority, request.getPriority());
        assertEquals(action, request.getAction());
        assertEquals(ids, request.getIds());
        assertEquals(copyEmails, request.getCopyEmail());
    }
}


package com.igot.cb.pores.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void testGettersAndSetters() {
        Config config = new Config();

        String sender = "noreply@example.com";
        String subject = "OTP Notification";
        Object topic = "topic-name";
        Object otp = 123456;

        config.setSender(sender);
        config.setSubject(subject);
        config.setTopic(topic);
        config.setOtp(otp);

        assertEquals(sender, config.getSender());
        assertEquals(subject, config.getSubject());
        assertEquals(topic, config.getTopic());
        assertEquals(otp, config.getOtp());
    }
}


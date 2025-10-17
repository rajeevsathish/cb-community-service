package com.igot.cb.pores.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RespParamTest {

    @Test
    void testAllArgsConstructorAndGetters() {
        RespParam param = new RespParam("res123", "msg123", "ERR001", "FAILED", "Something went wrong");

        assertEquals("res123", param.getResmsgid());
        assertEquals("msg123", param.getMsgid());
        assertEquals("ERR001", param.getErr());
        assertEquals("FAILED", param.getStatus());
        assertEquals("Something went wrong", param.getErrmsg());
    }

    @Test
    void testSettersAndNoArgsConstructor() {
        RespParam param = new RespParam();

        param.setResmsgid("res456");
        param.setMsgid("msg456");
        param.setErr("ERR002");
        param.setStatus("SUCCESS");
        param.setErrmsg("No issues");

        assertEquals("res456", param.getResmsgid());
        assertEquals("msg456", param.getMsgid());
        assertEquals("ERR002", param.getErr());
        assertEquals("SUCCESS", param.getStatus());
        assertEquals("No issues", param.getErrmsg());
    }
}

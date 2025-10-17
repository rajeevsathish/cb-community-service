package com.igot.cb.pores.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileProcessServiceTest {

    private FileProcessService fileProcessService;

    @BeforeEach
    void setUp() {
        fileProcessService = new FileProcessService();
    }

    @Test
    void testProcessCsvAndSendMessage_success() throws IOException {
        String csvContent = "Name,Age,Location\nJohn,30,USA\nAlice,25,UK";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        List<Map<String, String>> result = fileProcessService.processCsvAndSendMessage(inputStream);

        assertEquals(2, result.size());
        assertEquals("John", result.get(0).get("Name"));
        assertEquals("25", result.get(1).get("Age"));
    }

    @Test
    void testProcessCsvAndSendMessage_stopsOnBlankRow() throws IOException {
        String csvContent = "Name,Age,Location\nJohn,30,USA\n,,\nAlice,25,UK";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        List<Map<String, String>> result = fileProcessService.processCsvAndSendMessage(inputStream);

        // Should stop processing at the blank row (after 1st row)
        assertEquals(1, result.size());
        assertEquals("John", result.get(0).get("Name"));
    }

    @Test
    void testProcessCsvAndSendMessage_exceptionThrown() {
        // Pass a broken stream to trigger exception
        InputStream errorStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("forced IO error");
            }
        };

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                fileProcessService.processCsvAndSendMessage(errorStream)
        );

        assertEquals("forced IO error", exception.getMessage());
    }
}


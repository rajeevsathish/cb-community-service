package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileProcessServiceTest {

  private final FileProcessService fileProcessService = new FileProcessService();

  private InputStream csvStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void processCsvAndSendMessage_parsesAllDataRows() throws Exception {
    String csv = "userId,email\nu1,u1@example.org\nu2,u2@example.org\n";

    List<Map<String, String>> rows = fileProcessService.processCsvAndSendMessage(csvStream(csv));

    assertEquals(2, rows.size());
    assertEquals("u1", rows.get(0).get("userId"));
    assertEquals("u2@example.org", rows.get(1).get("email"));
  }

  @Test
  void processCsvAndSendMessage_stopsAtFirstFullyBlankRow() throws Exception {
    String csv = "userId,email\nu1,u1@example.org\n,\nu3,u3@example.org\n";

    List<Map<String, String>> rows = fileProcessService.processCsvAndSendMessage(csvStream(csv));

    assertEquals(1, rows.size());
    assertEquals("u1", rows.get(0).get("userId"));
  }

  @Test
  void processCsvAndSendMessage_replacesEmbeddedNewlinesInCellValues() throws Exception {
    String csv = "userId,notes\nu1,\"line1\nline2\"\n";

    List<Map<String, String>> rows = fileProcessService.processCsvAndSendMessage(csvStream(csv));

    assertEquals("line1,line2", rows.get(0).get("notes"));
  }

  @Test
  void processCsvAndSendMessage_returnsEmptyList_whenOnlyHeaderPresent() throws Exception {
    String csv = "userId,email\n";

    List<Map<String, String>> rows = fileProcessService.processCsvAndSendMessage(csvStream(csv));

    assertEquals(0, rows.size());
  }

  @Test
  void processCsvAndSendMessage_throwsRuntimeException_forMalformedCsv() {
    String malformed = "userId,email\n\"unterminated";

    assertThrows(RuntimeException.class,
        () -> fileProcessService.processCsvAndSendMessage(csvStream(malformed)));
  }
}

package com.igot.cb.authentication.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Base64UtilTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 57, 58, 59, 60, 200})
  void encodeDecode_roundTrips_forDefaultFlags(int length) {
    byte[] input = randomBytes(length);

    String encoded = Base64Util.encodeToString(input, Base64Util.DEFAULT);
    byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);

    assertArrayEquals(input, decoded);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4, 5, 16, 57, 58})
  void encodeDecode_roundTrips_withNoPaddingNoWrapUrlSafe(int length) {
    int flags = Base64Util.URL_SAFE | Base64Util.NO_PADDING | Base64Util.NO_WRAP;
    byte[] input = randomBytes(length);

    String encoded = Base64Util.encodeToString(input, flags);
    byte[] decoded = Base64Util.decode(encoded, flags);

    assertArrayEquals(input, decoded);
    assertFalse(encoded.contains("="));
    assertFalse(encoded.contains("\n"));
  }

  @Test
  void encodeToString_omitsPadding_whenNoPaddingFlagSet() {
    byte[] input = "ab".getBytes(StandardCharsets.UTF_8);

    String encoded = Base64Util.encodeToString(input, Base64Util.NO_PADDING | Base64Util.NO_WRAP);

    assertFalse(encoded.endsWith("="));
  }

  @Test
  void encodeToString_includesPadding_byDefault() {
    byte[] input = "ab".getBytes(StandardCharsets.UTF_8);

    String encoded = Base64Util.encodeToString(input, Base64Util.NO_WRAP);

    assertTrue(encoded.endsWith("="));
  }

  @Test
  void encodeToString_usesUrlSafeAlphabet() {
    // Bytes chosen so that standard base64 output would contain '+' and '/'.
    byte[] input = {(byte) 0xFB, (byte) 0xFF, (byte) 0xBF};

    String standard = Base64Util.encodeToString(input, Base64Util.NO_WRAP);
    String urlSafe = Base64Util.encodeToString(input, Base64Util.URL_SAFE | Base64Util.NO_WRAP);

    assertTrue(standard.contains("+") || standard.contains("/"));
    assertFalse(urlSafe.contains("+"));
    assertFalse(urlSafe.contains("/"));
  }

  @Test
  void encodeToString_insertsNewline_whenWrappingLongInput() {
    byte[] input = randomBytes(19 * 3 + 10);

    String encodedWrapped = Base64Util.encodeToString(input, Base64Util.DEFAULT);
    String encodedNoWrap = Base64Util.encodeToString(input, Base64Util.NO_WRAP);

    assertTrue(encodedWrapped.contains("\n"));
    assertFalse(encodedNoWrap.contains("\n"));
  }

  @Test
  void encodeToString_usesCrlf_whenCrlfFlagSet() {
    byte[] input = randomBytes(19 * 3 + 10);

    String encoded = Base64Util.encodeToString(input, Base64Util.CRLF);

    assertTrue(encoded.contains("\r\n"));
  }

  @Test
  void decode_ignoresEmbeddedWhitespaceAndNewlines() {
    byte[] input = randomBytes(6);
    String encoded = Base64Util.encodeToString(input, Base64Util.NO_WRAP);
    String withNewlines = encoded.substring(0, 4) + "\n" + encoded.substring(4);

    byte[] decoded = Base64Util.decode(withNewlines, Base64Util.DEFAULT);

    assertArrayEquals(input, decoded);
  }

  @Test
  void decode_throwsIllegalArgumentException_forInvalidCharacter() {
    assertThrows(IllegalArgumentException.class,
        () -> Base64Util.decode("!!!!not-base64!!!!", Base64Util.DEFAULT));
  }

  @Test
  void decode_throwsIllegalArgumentException_forIncorrectPadding() {
    assertThrows(IllegalArgumentException.class,
        () -> Base64Util.decode("QQ=", Base64Util.DEFAULT));
  }

  @Test
  void decode_throwsIllegalArgumentException_forSingleDanglingCharacter() {
    assertThrows(IllegalArgumentException.class,
        () -> Base64Util.decode("Q", Base64Util.DEFAULT));
  }

  @Test
  void encode_withOffsetAndLength_encodesOnlyRequestedSlice() {
    byte[] input = "prefix-hello-suffix".getBytes(StandardCharsets.UTF_8);
    int offset = "prefix-".length();
    int len = "hello".length();

    String encoded = Base64Util.encodeToString(input, offset, len, Base64Util.NO_WRAP);
    byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);

    assertEquals("hello", new String(decoded, StandardCharsets.UTF_8));
  }

  @Test
  void decode_emptyString_returnsEmptyArray() {
    byte[] decoded = Base64Util.decode("", Base64Util.DEFAULT);

    assertEquals(0, decoded.length);
  }

  private byte[] randomBytes(int length) {
    byte[] bytes = new byte[length];
    new Random(42).nextBytes(bytes);
    return bytes;
  }
}

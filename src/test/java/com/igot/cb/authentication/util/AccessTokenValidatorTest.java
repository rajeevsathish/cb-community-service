package com.igot.cb.authentication.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.model.KeyData;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccessTokenValidatorTest {

  private static final String KEY_ID = "unit-test-kid";
  private static final int JWT_BASE64_FLAGS = Base64Util.URL_SAFE | Base64Util.NO_PADDING | Base64Util.NO_WRAP;

  private static KeyPair keyPair;
  private static String realmUrl;
  private final ObjectMapper mapper = new ObjectMapper();

  @Mock
  private KeyManager keyManager;

  private AccessTokenValidator accessTokenValidator;

  @BeforeAll
  static void setUpKeysAndRealm() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keyPair = generator.generateKeyPair();

    Field realmUrlField = AccessTokenValidator.class.getDeclaredField("REALM_URL");
    realmUrlField.setAccessible(true);
    realmUrl = (String) realmUrlField.get(null);
  }

  @BeforeEach
  void setUp() {
    accessTokenValidator = new AccessTokenValidator();
    ReflectionTestUtils.setField(accessTokenValidator, "keyManager", keyManager);
  }

  private String base64Segment(Map<String, Object> claims) throws Exception {
    return Base64Util.encodeToString(mapper.writeValueAsBytes(claims), JWT_BASE64_FLAGS);
  }

  private String buildToken(Map<String, Object> body, boolean tamperSignature) throws Exception {
    Map<String, Object> header = new HashMap<>();
    header.put("alg", "RS256");
    header.put("kid", KEY_ID);

    String headerSegment = base64Segment(header);
    String bodySegment = base64Segment(body);
    String payload = headerSegment + "." + bodySegment;

    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(keyPair.getPrivate());
    signature.update(payload.getBytes(StandardCharsets.US_ASCII));
    byte[] signatureBytes = signature.sign();
    if (tamperSignature) {
      signatureBytes[0] ^= 0xFF;
    }
    String signatureSegment = Base64Util.encodeToString(signatureBytes, JWT_BASE64_FLAGS);

    return payload + "." + signatureSegment;
  }

  private Map<String, Object> validBody(String subject) {
    Map<String, Object> body = new HashMap<>();
    body.put("sub", subject);
    body.put("iss", realmUrl);
    body.put("exp", (int) (System.currentTimeMillis() / 1000) + 3600);
    return body;
  }

  @Test
  void verifyUserToken_returnsUserId_forValidToken() throws Exception {
    when(keyManager.getPublicKey(KEY_ID)).thenReturn(new KeyData(KEY_ID, keyPair.getPublic()));
    String token = buildToken(validBody("realm:user123"), false);

    String userId = accessTokenValidator.verifyUserToken(token);

    assertEquals("user123", userId);
  }

  @Test
  void verifyUserToken_returnsNull_forExpiredToken() throws Exception {
    when(keyManager.getPublicKey(KEY_ID)).thenReturn(new KeyData(KEY_ID, keyPair.getPublic()));
    Map<String, Object> body = new HashMap<>();
    body.put("sub", "realm:user123");
    body.put("iss", realmUrl);
    body.put("exp", (int) (System.currentTimeMillis() / 1000) - 3600);
    String token = buildToken(body, false);

    String userId = accessTokenValidator.verifyUserToken(token);

    assertNull(userId);
  }

  @Test
  void verifyUserToken_returnsNull_forInvalidIssuer() throws Exception {
    when(keyManager.getPublicKey(KEY_ID)).thenReturn(new KeyData(KEY_ID, keyPair.getPublic()));
    Map<String, Object> body = new HashMap<>();
    body.put("sub", "realm:user123");
    body.put("iss", "https://not-the-real-issuer.example.org");
    body.put("exp", (int) (System.currentTimeMillis() / 1000) + 3600);
    String token = buildToken(body, false);

    String userId = accessTokenValidator.verifyUserToken(token);

    assertNull(userId);
  }

  @Test
  void verifyUserToken_returnsNull_forTamperedSignature() throws Exception {
    when(keyManager.getPublicKey(KEY_ID)).thenReturn(new KeyData(KEY_ID, keyPair.getPublic()));
    String token = buildToken(validBody("realm:user123"), true);

    String userId = accessTokenValidator.verifyUserToken(token);

    assertNull(userId);
  }

  @Test
  void verifyUserToken_returnsNull_forMalformedToken() {
    String userId = accessTokenValidator.verifyUserToken("not-a-valid-jwt");

    assertNull(userId);
  }

  @Test
  void fetchUserIdFromAccessToken_returnsNull_forNullToken() {
    String userId = accessTokenValidator.fetchUserIdFromAccessToken(null);

    assertNull(userId);
  }

  @Test
  void fetchUserIdFromAccessToken_returnsUserId_forValidToken() throws Exception {
    when(keyManager.getPublicKey(KEY_ID)).thenReturn(new KeyData(KEY_ID, keyPair.getPublic()));
    String token = buildToken(validBody("realm:user456"), false);

    String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);

    assertEquals("user456", userId);
  }
}

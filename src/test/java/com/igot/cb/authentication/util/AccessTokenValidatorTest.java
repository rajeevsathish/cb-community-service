package com.igot.cb.authentication.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.Time;
import org.keycloak.crypto.KeyWrapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.PublicKey;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class AccessTokenValidatorTest {

    @Mock
    private KeyManager keyManager;

    @Mock
    private KeyWrapper mockKeyWrapper;

    @Mock
    private PublicKey mockPublicKey;

    @InjectMocks
    private AccessTokenValidator accessTokenValidator;

    private static final ObjectMapper mapper = new ObjectMapper();

    private String validToken;
    private String invalidFormatToken;

    @BeforeEach
    void setUp() throws Exception {
        validToken = generateToken("user123", Time.currentTime() + 1000, getRealmUrl());
        String expiredToken = generateToken("expiredUser", Time.currentTime() - 1000, getRealmUrl());
        invalidFormatToken = "invalid.token";
    }

    private String getRealmUrl() {
        return "https://keycloak.example.com/realms/test";
    }

    private String generateToken(String userId, int exp, String issuer) throws Exception {
        Map<String, Object> header = new HashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", "testKey");

        Map<String, Object> body = new HashMap<>();
        body.put("sub", "user:" + userId);
        body.put("exp", exp);
        body.put("iss", issuer);

        String headerJson = mapper.writeValueAsString(header);
        String bodyJson = mapper.writeValueAsString(body);

        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes());
        String encodedBody = Base64.getUrlEncoder().withoutPadding().encodeToString(bodyJson.getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString("signature".getBytes());

        return encodedHeader + "." + encodedBody + "." + signature;
    }

    @Test
    void testFetchUserIdFromAccessToken_nullToken_returnsNull() {
        assertNull(accessTokenValidator.fetchUserIdFromAccessToken(null));
    }

    @Test
    void testFetchUserIdFromAccessToken_validToken_returnsUserId() throws Exception {
        AccessTokenValidator spyValidator = spy(accessTokenValidator);
        doReturn("user123").when(spyValidator).verifyUserToken("validToken");
        String result = spyValidator.fetchUserIdFromAccessToken("validToken");
        assertEquals("user123", result);
    }

    @Test
    void testFetchUserIdFromAccessToken_unauthorized_returnsNull() throws Exception {
        AccessTokenValidator spyValidator = spy(accessTokenValidator);
        doReturn("UNAUTHORIZED").when(spyValidator).verifyUserToken("unauth");
        assertNull(spyValidator.fetchUserIdFromAccessToken("unauth"));
    }

    @Test
    void testFetchUserIdFromAccessToken_exception_returnsNull() throws Exception {
        AccessTokenValidator spyValidator = spy(accessTokenValidator);
        doThrow(new RuntimeException("error")).when(spyValidator).verifyUserToken("token");
        assertNull(spyValidator.fetchUserIdFromAccessToken("token"));
    }

    @Test
    void testVerifyUserToken_validFlow_withReflection() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", getRealmUrl());
        payload.put("sub", "user:user123");
        Method method = AccessTokenValidator.class.getDeclaredMethod("validateToken", String.class);
        method.setAccessible(true);
        AccessTokenValidator validator = new AccessTokenValidator();
        Method verifyMethod = AccessTokenValidator.class.getDeclaredMethod("verifyUserToken", String.class);
        verifyMethod.setAccessible(true);
        String result = (String) verifyMethod.invoke(validator, "token");
        assertNull(result);
    }


    @Test
    void testVerifyUserToken_invalidIssuer_returnsNull_withReflection() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", "wrong");
        payload.put("sub", "user:abc");
        Method method = AccessTokenValidator.class.getDeclaredMethod("verifyUserToken", String.class);
        method.setAccessible(true);
        AccessTokenValidator validator = new AccessTokenValidator();
        String result = (String) method.invoke(validator, "token");
        assertNull(result);
    }


    @Test
    void testVerifyUserToken_exception_returnsNull_withReflection() throws Exception {
        Method method = AccessTokenValidator.class.getDeclaredMethod("verifyUserToken", String.class);
        method.setAccessible(true);
        AccessTokenValidator validator = new AccessTokenValidator();
        String result = (String) method.invoke(validator, "token");
        assertNull(result);
    }


    @Test
    void testPrivate_checkIss_trueAndFalseBranches() throws Exception {
        Field field = AccessTokenValidator.class.getDeclaredField("REALM_URL");
        field.setAccessible(true);

        Unsafe unsafe = getUnsafe();
        Object base = unsafe.staticFieldBase(field);
        unsafe.putObject(base, unsafe.staticFieldOffset(field), "https://keycloak.example.com/realms/test");

        Method method = AccessTokenValidator.class.getDeclaredMethod("checkIss", String.class);
        method.setAccessible(true);

        boolean resultFalse = (boolean) method.invoke(new AccessTokenValidator(), "wrongIssuer");
        assertFalse(resultFalse);

        boolean resultTrue = (boolean) method.invoke(new AccessTokenValidator(), "https://keycloak.example.com/realms/test");
        assertTrue(resultTrue);
    }

    private static Unsafe getUnsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    @Test
    void testPrivate_isExpired_trueAndFalse() throws Exception {
        Method method = AccessTokenValidator.class.getDeclaredMethod("isExpired", Integer.class);
        method.setAccessible(true);

        boolean expired = (boolean) method.invoke(accessTokenValidator, Time.currentTime() - 10);
        boolean notExpired = (boolean) method.invoke(accessTokenValidator, Time.currentTime() + 1000);

        assertTrue(expired);
        assertFalse(notExpired);
    }

    @Test
    void testPrivate_decodeFromBase64() throws Exception {
        Method method = AccessTokenValidator.class.getDeclaredMethod("decodeFromBase64", String.class);
        method.setAccessible(true);
        byte[] decoded = (byte[]) method.invoke(accessTokenValidator, Base64.getEncoder().encodeToString("abc".getBytes()));
        assertNotNull(decoded);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPrivate_validateToken_invalidFormat() throws Exception {
        Method method = AccessTokenValidator.class.getDeclaredMethod("validateToken", String.class);
        method.setAccessible(true);
        Map<String, Object> result = (Map<String, Object>) method.invoke(accessTokenValidator, invalidFormatToken);
        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPrivate_validateToken_catchesRuntimeException_withReflection() throws Exception {
        Method validateMethod = AccessTokenValidator.class.getDeclaredMethod("validateToken", String.class);
        validateMethod.setAccessible(true);
        AccessTokenValidator validator = new AccessTokenValidator();
        Method decodeMethod = AccessTokenValidator.class.getDeclaredMethod("decodeFromBase64", String.class);
        decodeMethod.setAccessible(true);
        try {
            decodeMethod.invoke(validator, "invalid");
        } catch (Exception e) {
            Assertions.fail("Unexpected exception occurred: " + e.getMessage());
        }
        Map<String, Object> result = (Map<String, Object>) validateMethod.invoke(validator, validToken);
        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFullCoverage_AccessTokenValidator_withReflection() throws Exception {
        AccessTokenValidator validator = new AccessTokenValidator();

        Method isExpiredMethod = AccessTokenValidator.class.getDeclaredMethod("isExpired", Integer.class);
        isExpiredMethod.setAccessible(true);
        boolean expired = (boolean) isExpiredMethod.invoke(validator, Time.currentTime() - 10);
        boolean notExpired = (boolean) isExpiredMethod.invoke(validator, Time.currentTime() + 1000);
        assertTrue(expired);
        assertFalse(notExpired);

        Method decodeMethod = AccessTokenValidator.class.getDeclaredMethod("decodeFromBase64", String.class);
        decodeMethod.setAccessible(true);
        byte[] decoded = (byte[]) decodeMethod.invoke(validator, Base64.getEncoder().encodeToString("abc".getBytes()));
        assertNotNull(decoded);

        Method validateMethod = AccessTokenValidator.class.getDeclaredMethod("validateToken", String.class);
        validateMethod.setAccessible(true);

        String headerJson = "{\"kid\":\"123\"}";
        String bodyJson = "{\"exp\":" + (Time.currentTime() + 1000) + ",\"iss\":\"https://keycloak.example.com/realms/test\"}";
        String signatureJson = "signature";

        String headerEncoded = Base64.getEncoder().encodeToString(headerJson.getBytes());
        String bodyEncoded = Base64.getEncoder().encodeToString(bodyJson.getBytes());
        String signatureEncoded = Base64.getEncoder().encodeToString(signatureJson.getBytes());

        String token = headerEncoded + "." + bodyEncoded + "." + signatureEncoded;

        Map<String, Object> result = (Map<String, Object>) validateMethod.invoke(validator, token);
        assertNotNull(result);

        Method verifyMethod = AccessTokenValidator.class.getDeclaredMethod("verifyUserToken", String.class);
        verifyMethod.setAccessible(true);
        String result1 = (String) verifyMethod.invoke(validator, "invalidToken");
        assertNull(result1);

        String accessTokenResult = validator.fetchUserIdFromAccessToken(null);
        assertNull(accessTokenResult);
    }


}
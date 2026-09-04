package com.igot.cb.authentication.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.igot.cb.authentication.model.KeyData;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeyManagerTest {

  @Test
  void loadPublicKey_returnsPublicKey_forValidPemString() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    String encoded = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    String pem = "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";

    PublicKey result = KeyManager.loadPublicKey(pem);

    assertNotNull(result);
    assertArrayEquals(keyPair.getPublic().getEncoded(), result.getEncoded());
  }

  @Test
  void loadPublicKey_ignoresEmbeddedNewlines() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    String encoded = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    StringBuilder wrapped = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
    for (int i = 0; i < encoded.length(); i += 64) {
      wrapped.append(encoded, i, Math.min(i + 64, encoded.length())).append("\n");
    }
    wrapped.append("-----END PUBLIC KEY-----");

    PublicKey result = KeyManager.loadPublicKey(wrapped.toString());

    assertArrayEquals(keyPair.getPublic().getEncoded(), result.getEncoded());
  }

  @SuppressWarnings("unchecked")
  @Test
  void getPublicKey_returnsStoredKeyData_afterManualInsertion() throws Exception {
    Field keyMapField = KeyManager.class.getDeclaredField("keyMap");
    keyMapField.setAccessible(true);
    Map<String, KeyData> keyMap = (Map<String, KeyData>) keyMapField.get(null);

    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    PublicKey publicKey = generator.generateKeyPair().getPublic();
    KeyData keyData = new KeyData("unit-test-key", publicKey);
    keyMap.put("unit-test-key", keyData);
    try {
      KeyManager keyManager = new KeyManager();

      KeyData result = keyManager.getPublicKey("unit-test-key");

      assertNotNull(result);
      assertArrayEquals(publicKey.getEncoded(), result.getPublicKey().getEncoded());
    } finally {
      keyMap.remove("unit-test-key");
    }
  }

  @Test
  void getPublicKey_returnsNull_forUnknownKeyId() {
    KeyManager keyManager = new KeyManager();

    KeyData result = keyManager.getPublicKey("does-not-exist");

    assertNull(result);
  }
}

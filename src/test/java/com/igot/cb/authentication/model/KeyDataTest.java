package com.igot.cb.authentication.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import org.junit.jupiter.api.Test;

class KeyDataTest {

  @Test
  void constructor_setsKeyIdAndPublicKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();

    KeyData keyData = new KeyData("kid-1", keyPair.getPublic());

    assertEquals("kid-1", keyData.getKeyId());
    assertSame(keyPair.getPublic(), keyData.getPublicKey());
  }

  @Test
  void setters_updateFieldsIndependently() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    PublicKey publicKey = generator.generateKeyPair().getPublic();
    KeyData keyData = new KeyData("kid-1", null);

    keyData.setKeyId("kid-2");
    keyData.setPublicKey(publicKey);

    assertEquals("kid-2", keyData.getKeyId());
    assertSame(publicKey, keyData.getPublicKey());
  }
}

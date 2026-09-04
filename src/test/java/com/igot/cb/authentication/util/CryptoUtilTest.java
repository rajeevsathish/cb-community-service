package com.igot.cb.authentication.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CryptoUtilTest {

  private static KeyPair keyPair;

  @BeforeAll
  static void generateKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keyPair = generator.generateKeyPair();
  }

  private byte[] sign(String payload, PrivateKey privateKey) throws Exception {
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(payload.getBytes(StandardCharsets.US_ASCII));
    return signature.sign();
  }

  @Test
  void verifyRSASign_returnsTrue_forValidSignature() throws Exception {
    String payload = "header.body";
    byte[] signatureBytes = sign(payload, keyPair.getPrivate());

    boolean result = CryptoUtil.verifyRSASign(payload, signatureBytes, keyPair.getPublic(), "SHA256withRSA");

    assertTrue(result);
  }

  @Test
  void verifyRSASign_returnsFalse_forTamperedSignature() throws Exception {
    String payload = "header.body";
    byte[] signatureBytes = sign(payload, keyPair.getPrivate());
    signatureBytes[0] ^= 0xFF;

    boolean result = CryptoUtil.verifyRSASign(payload, signatureBytes, keyPair.getPublic(), "SHA256withRSA");

    assertFalse(result);
  }

  @Test
  void verifyRSASign_returnsFalse_forMismatchedPayload() throws Exception {
    byte[] signatureBytes = sign("header.body", keyPair.getPrivate());

    boolean result = CryptoUtil.verifyRSASign("different.payload", signatureBytes, keyPair.getPublic(), "SHA256withRSA");

    assertFalse(result);
  }

  @Test
  void verifyRSASign_returnsFalse_forUnknownAlgorithm() throws Exception {
    byte[] signatureBytes = sign("header.body", keyPair.getPrivate());

    boolean result = CryptoUtil.verifyRSASign("header.body", signatureBytes, keyPair.getPublic(), "NOT_AN_ALGORITHM");

    assertFalse(result);
  }

  @Test
  void verifyRSASign_returnsFalse_forWrongPublicKey() throws Exception {
    String payload = "header.body";
    byte[] signatureBytes = sign(payload, keyPair.getPrivate());
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    PublicKey otherPublicKey = generator.generateKeyPair().getPublic();

    boolean result = CryptoUtil.verifyRSASign(payload, signatureBytes, otherPublicKey, "SHA256withRSA");

    assertFalse(result);
  }
}

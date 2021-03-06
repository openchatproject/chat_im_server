package com.openchat.protocal.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import com.openchat.protocal.IdentityKey;
import com.openchat.protocal.InvalidKeyException;
import com.openchat.protocal.InvalidMessageException;
import com.openchat.protocal.LegacyMessageException;
import com.openchat.protocal.ecc.Curve;
import com.openchat.protocal.ecc.ECPublicKey;
import com.openchat.protocal.util.ByteUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class OpenchatMessage implements CiphertextMessage {

  private static final int MAC_LENGTH = 8;

  private final int         messageVersion;
  private final ECPublicKey senderRatchetKey;
  private final int         counter;
  private final int         previousCounter;
  private final byte[]      ciphertext;
  private final byte[]      serialized;

  public OpenchatMessage(byte[] serialized) throws InvalidMessageException, LegacyMessageException {
    try {
      byte[][] messageParts = ByteUtil.split(serialized, 1, serialized.length - 1 - MAC_LENGTH, MAC_LENGTH);
      byte     version      = messageParts[0][0];
      byte[]   message      = messageParts[1];
      byte[]   mac          = messageParts[2];

      if (ByteUtil.highBitsToInt(version) < CURRENT_VERSION) {
        throw new LegacyMessageException("Legacy message: " + ByteUtil.highBitsToInt(version));
      }

      if (ByteUtil.highBitsToInt(version) > CURRENT_VERSION) {
        throw new InvalidMessageException("Unknown version: " + ByteUtil.highBitsToInt(version));
      }

      OpenchatProtos.OpenchatMessage openchatMessage = OpenchatProtos.OpenchatMessage.parseFrom(message);

      if (!openchatMessage.hasCiphertext() ||
          !openchatMessage.hasCounter() ||
          !openchatMessage.hasRatchetKey())
      {
        throw new InvalidMessageException("Incomplete message.");
      }

      this.serialized       = serialized;
      this.senderRatchetKey = Curve.decodePoint(openchatMessage.getRatchetKey().toByteArray(), 0);
      this.messageVersion   = ByteUtil.highBitsToInt(version);
      this.counter          = openchatMessage.getCounter();
      this.previousCounter  = openchatMessage.getPreviousCounter();
      this.ciphertext       = openchatMessage.getCiphertext().toByteArray();
    } catch (InvalidProtocolBufferException | InvalidKeyException | ParseException e) {
      throw new InvalidMessageException(e);
    }
  }

  public OpenchatMessage(int messageVersion, SecretKeySpec macKey, ECPublicKey senderRatchetKey,
                       int counter, int previousCounter, byte[] ciphertext,
                       IdentityKey senderIdentityKey,
                       IdentityKey receiverIdentityKey)
  {
    byte[] version = {ByteUtil.intsToByteHighAndLow(messageVersion, CURRENT_VERSION)};
    byte[] message = OpenchatProtos.OpenchatMessage.newBuilder()
                                               .setRatchetKey(ByteString.copyFrom(senderRatchetKey.serialize()))
                                               .setCounter(counter)
                                               .setPreviousCounter(previousCounter)
                                               .setCiphertext(ByteString.copyFrom(ciphertext))
                                               .build().toByteArray();

    byte[] mac     = getMac(senderIdentityKey, receiverIdentityKey, macKey, ByteUtil.combine(version, message));

    this.serialized       = ByteUtil.combine(version, message, mac);
    this.senderRatchetKey = senderRatchetKey;
    this.counter          = counter;
    this.previousCounter  = previousCounter;
    this.ciphertext       = ciphertext;
    this.messageVersion   = messageVersion;
  }

  public ECPublicKey getSenderRatchetKey()  {
    return senderRatchetKey;
  }

  public int getMessageVersion() {
    return messageVersion;
  }

  public int getCounter() {
    return counter;
  }

  public byte[] getBody() {
    return ciphertext;
  }

  public void verifyMac(IdentityKey senderIdentityKey, IdentityKey receiverIdentityKey, SecretKeySpec macKey)
      throws InvalidMessageException
  {
    byte[][] parts    = ByteUtil.split(serialized, serialized.length - MAC_LENGTH, MAC_LENGTH);
    byte[]   ourMac   = getMac(senderIdentityKey, receiverIdentityKey, macKey, parts[0]);
    byte[]   theirMac = parts[1];

    if (!MessageDigest.isEqual(ourMac, theirMac)) {
      throw new InvalidMessageException("Bad Mac!");
    }
  }

  private byte[] getMac(IdentityKey senderIdentityKey,
                        IdentityKey receiverIdentityKey,
                        SecretKeySpec macKey, byte[] serialized)
  {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(macKey);

      mac.update(senderIdentityKey.getPublicKey().serialize());
      mac.update(receiverIdentityKey.getPublicKey().serialize());

      byte[] fullMac = mac.doFinal(serialized);
      return ByteUtil.trim(fullMac, MAC_LENGTH);
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public byte[] serialize() {
    return serialized;
  }

  @Override
  public int getType() {
    return CiphertextMessage.OPENCHAT_TYPE;
  }

  public static boolean isLegacy(byte[] message) {
    return message != null && message.length >= 1 &&
        ByteUtil.highBitsToInt(message[0]) != CiphertextMessage.CURRENT_VERSION;
  }

}

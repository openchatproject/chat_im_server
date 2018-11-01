package com.openchat.protocal.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import com.openchat.protocal.InvalidKeyException;
import com.openchat.protocal.InvalidMessageException;
import com.openchat.protocal.LegacyMessageException;
import com.openchat.protocal.ecc.Curve;
import com.openchat.protocal.ecc.ECPublicKey;
import com.openchat.protocal.util.ByteUtil;

public class SenderKeyDistributionMessage implements CiphertextMessage {

  private final int         id;
  private final int         iteration;
  private final byte[]      chainKey;
  private final ECPublicKey signatureKey;
  private final byte[]      serialized;

  public SenderKeyDistributionMessage(int id, int iteration, byte[] chainKey, ECPublicKey signatureKey) {
    byte[] version = {ByteUtil.intsToByteHighAndLow(CURRENT_VERSION, CURRENT_VERSION)};
    byte[] protobuf = OpenchatProtos.SenderKeyDistributionMessage.newBuilder()
                                                               .setId(id)
                                                               .setIteration(iteration)
                                                               .setChainKey(ByteString.copyFrom(chainKey))
                                                               .setSigningKey(ByteString.copyFrom(signatureKey.serialize()))
                                                               .build().toByteArray();

    this.id           = id;
    this.iteration    = iteration;
    this.chainKey     = chainKey;
    this.signatureKey = signatureKey;
    this.serialized   = ByteUtil.combine(version, protobuf);
  }

  public SenderKeyDistributionMessage(byte[] serialized) throws LegacyMessageException, InvalidMessageException {
    try {
      byte[][] messageParts = ByteUtil.split(serialized, 1, serialized.length - 1);
      byte     version      = messageParts[0][0];
      byte[]   message      = messageParts[1];

      if (ByteUtil.highBitsToInt(version) < CiphertextMessage.CURRENT_VERSION) {
        throw new LegacyMessageException("Legacy message: " + ByteUtil.highBitsToInt(version));
      }

      if (ByteUtil.highBitsToInt(version) > CURRENT_VERSION) {
        throw new InvalidMessageException("Unknown version: " + ByteUtil.highBitsToInt(version));
      }

      OpenchatProtos.SenderKeyDistributionMessage distributionMessage = OpenchatProtos.SenderKeyDistributionMessage.parseFrom(message);

      if (!distributionMessage.hasId()        ||
          !distributionMessage.hasIteration() ||
          !distributionMessage.hasChainKey()  ||
          !distributionMessage.hasSigningKey())
      {
        throw new InvalidMessageException("Incomplete message.");
      }

      this.serialized   = serialized;
      this.id           = distributionMessage.getId();
      this.iteration    = distributionMessage.getIteration();
      this.chainKey     = distributionMessage.getChainKey().toByteArray();
      this.signatureKey = Curve.decodePoint(distributionMessage.getSigningKey().toByteArray(), 0);
    } catch (InvalidProtocolBufferException | InvalidKeyException e) {
      throw new InvalidMessageException(e);
    }
  }

  @Override
  public byte[] serialize() {
    return serialized;
  }

  @Override
  public int getType() {
    return SENDERKEY_DISTRIBUTION_TYPE;
  }

  public int getIteration() {
    return iteration;
  }

  public byte[] getChainKey() {
    return chainKey;
  }

  public ECPublicKey getSignatureKey() {
    return signatureKey;
  }

  public int getId() {
    return id;
  }
}

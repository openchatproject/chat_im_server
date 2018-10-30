package com.openchat.protocal.groups.ratchet;

import com.openchat.protocal.kdf.HKDFv3;
import com.openchat.protocal.util.ByteUtil;


public class SenderMessageKey {

  private final int    iteration;
  private final byte[] iv;
  private final byte[] cipherKey;
  private final byte[] seed;

  public SenderMessageKey(int iteration, byte[] seed) {
    byte[] derivative = new HKDFv3().deriveSecrets(seed, "OpenchatGroup".getBytes(), 48);
    byte[][] parts    = ByteUtil.split(derivative, 16, 32);

    this.iteration = iteration;
    this.seed      = seed;
    this.iv        = parts[0];
    this.cipherKey = parts[1];
  }

  public int getIteration() {
    return iteration;
  }

  public byte[] getIv() {
    return iv;
  }

  public byte[] getCipherKey() {
    return cipherKey;
  }

  public byte[] getSeed() {
    return seed;
  }
}

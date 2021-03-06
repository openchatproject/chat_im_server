package com.openchat.secureim.crypto;

import com.openchat.protocal.ecc.ECPrivateKey;
import com.openchat.protocal.ecc.ECPublicKey;

public class AsymmetricMasterSecret {

  private final ECPublicKey djbPublicKey;
  private final ECPrivateKey djbPrivateKey;

  public AsymmetricMasterSecret(ECPublicKey djbPublicKey, ECPrivateKey djbPrivateKey)
  {
    this.djbPublicKey   = djbPublicKey;
    this.djbPrivateKey  = djbPrivateKey;
  }

  public ECPublicKey getDjbPublicKey() {
    return djbPublicKey;
  }

  public ECPrivateKey getPrivateKey() {
    return djbPrivateKey;
  }

}

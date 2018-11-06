package com.openchat.imservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.openchat.protocal.IdentityKey;
import com.openchat.imservice.api.push.SignedPreKeyEntity;
import com.openchat.imservice.internal.util.JsonUtil;

import java.util.List;

public class PreKeyState {

  @JsonProperty
  @JsonSerialize(using = JsonUtil.IdentityKeySerializer.class)
  @JsonDeserialize(using = JsonUtil.IdentityKeyDeserializer.class)
  private IdentityKey        identityKey;

  @JsonProperty
  private List<PreKeyEntity> preKeys;

  @JsonProperty
  private PreKeyEntity       lastResortKey;

  @JsonProperty
  private SignedPreKeyEntity signedPreKey;

  public PreKeyState(List<PreKeyEntity> preKeys, PreKeyEntity lastResortKey,
                     SignedPreKeyEntity signedPreKey, IdentityKey identityKey)
  {
    this.preKeys       = preKeys;
    this.lastResortKey = lastResortKey;
    this.signedPreKey  = signedPreKey;
    this.identityKey   = identityKey;
  }

}

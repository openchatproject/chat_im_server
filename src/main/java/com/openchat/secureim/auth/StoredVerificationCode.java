package com.openchat.secureim.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

public class StoredVerificationCode {

  @JsonProperty
  private final String code;

  @JsonProperty
  private final long   timestamp;

  public StoredVerificationCode(String code, long timestamp) {
    this.code      = code;
    this.timestamp = timestamp;
  }

  public String getCode() {
    return code;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public boolean isValid(String theirCodeString) {
    if (timestamp + TimeUnit.MINUTES.toMillis(30) < System.currentTimeMillis()) {
      return false;
    }

    byte[] ourCode   = code.getBytes();
    byte[] theirCode = theirCodeString.getBytes();

    return MessageDigest.isEqual(ourCode, theirCode);
  }
}
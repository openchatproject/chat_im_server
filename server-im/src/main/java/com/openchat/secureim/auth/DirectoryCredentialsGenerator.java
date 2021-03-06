package com.openchat.secureim.auth;

import com.google.common.base.Optional;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.util.Util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

public class DirectoryCredentialsGenerator {

  private final Logger logger = LoggerFactory.getLogger(DirectoryCredentialsGenerator.class);

  private final byte[] key;
  private final byte[] userIdKey;

  public DirectoryCredentialsGenerator(byte[] key, byte[] userIdKey) {
    this.key       = key;
    this.userIdKey = userIdKey;
  }

  public DirectoryCredentials generateFor(String number) {
    Mac    mac                = getMacInstance();
    String username           = getUserId(number, mac);
    long   currentTimeSeconds = System.currentTimeMillis() / 1000;
    String prefix             = username + ":"  + currentTimeSeconds;
    String output             = Hex.encodeHexString(Util.truncate(getHmac(key, prefix.getBytes(), mac), 10));
    String token              = prefix + ":" + output;

    return new DirectoryCredentials(username, token);
  }


  public boolean isValid(String token, String number, long currentTimeMillis) {
    String[] parts = token.split(":");
    Mac      mac   = getMacInstance();

    if (parts.length != 3) {
      return false;
    }

    if (!getUserId(number, mac).equals(parts[0])) {
      return false;
    }

    if (!isValidTime(parts[1], currentTimeMillis)) {
      return false;
    }

    return isValidSignature(parts[0] + ":" + parts[1], parts[2], mac);
  }

  private String getUserId(String number, Mac mac) {
      return Hex.encodeHexString(Util.truncate(getHmac(userIdKey, number.getBytes(), mac), 10));
  }

  private boolean isValidTime(String timeString, long currentTimeMillis) {
    try {
      long tokenTime = Long.parseLong(timeString);
      long ourTime   = TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis);

      return TimeUnit.SECONDS.toHours(Math.abs(ourTime - tokenTime)) < 24;
    } catch (NumberFormatException e) {
      logger.warn("Number Format", e);
      return false;
    }
  }

  private boolean isValidSignature(String prefix, String suffix, Mac mac) {
    try {
      byte[] ourSuffix   = Util.truncate(getHmac(key, prefix.getBytes(), mac), 10);
      byte[] theirSuffix = Hex.decodeHex(suffix.toCharArray());

      return MessageDigest.isEqual(ourSuffix, theirSuffix);
    } catch (DecoderException e) {
      logger.warn("DirectoryCredentials", e);
      return false;
    }
  }

  private Mac getMacInstance() {
    try {
      return Mac.getInstance("HmacSHA256");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] getHmac(byte[] key, byte[] input, Mac mac) {
    try {
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(input);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

}

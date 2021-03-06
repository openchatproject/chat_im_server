package com.openchat.imservice.api.messages.multidevice;

import com.openchat.protocal.util.guava.Optional;
import com.openchat.imservice.api.messages.OpenchatServiceAttachmentStream;

import java.util.List;

public class DeviceGroup {

  private final byte[]                                  id;
  private final Optional<String>                        name;
  private final List<String>                            members;
  private final Optional<OpenchatServiceAttachmentStream> avatar;
  private final boolean                                 active;
  private final Optional<Integer>                       expirationTimer;
  private final Optional<String>                        color;
  private final boolean                                 blocked;

  public DeviceGroup(byte[] id, Optional<String> name, List<String> members,
                     Optional<OpenchatServiceAttachmentStream> avatar,
                     boolean active, Optional<Integer> expirationTimer,
                     Optional<String> color, boolean blocked)
  {
    this.id              = id;
    this.name            = name;
    this.members         = members;
    this.avatar          = avatar;
    this.active          = active;
    this.expirationTimer = expirationTimer;
    this.color           = color;
    this.blocked         = blocked;
  }

  public Optional<OpenchatServiceAttachmentStream> getAvatar() {
    return avatar;
  }

  public Optional<String> getName() {
    return name;
  }

  public byte[] getId() {
    return id;
  }

  public List<String> getMembers() {
    return members;
  }

  public boolean isActive() {
    return active;
  }

  public Optional<Integer> getExpirationTimer() {
    return expirationTimer;
  }

  public Optional<String> getColor() {
    return color;
  }

  public boolean isBlocked() {
    return blocked;
  }
}

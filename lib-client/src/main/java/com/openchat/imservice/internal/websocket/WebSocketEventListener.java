package com.openchat.imservice.internal.websocket;

public interface WebSocketEventListener {

  public void onMessage(byte[] payload);
  public void onClose();
  public void onConnected();

}

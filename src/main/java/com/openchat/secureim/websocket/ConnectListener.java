package com.openchat.secureim.websocket;

import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.push.PushSender;
import com.openchat.secureim.storage.Account;
import com.openchat.secureim.storage.AccountsManager;
import com.openchat.secureim.storage.Device;
import com.openchat.secureim.storage.PubSubManager;
import com.openchat.secureim.storage.StoredMessages;
import com.openchat.websocket.session.WebSocketSessionContext;
import com.openchat.websocket.setup.WebSocketConnectListener;

public class ConnectListener implements WebSocketConnectListener {

  private static final Logger logger = LoggerFactory.getLogger(WebSocketConnection.class);

  private final AccountsManager accountsManager;
  private final PushSender      pushSender;
  private final StoredMessages  storedMessages;
  private final PubSubManager   pubSubManager;

  public ConnectListener(AccountsManager accountsManager, PushSender pushSender,
                         StoredMessages storedMessages, PubSubManager pubSubManager)
  {
    this.accountsManager = accountsManager;
    this.pushSender      = pushSender;
    this.storedMessages  = storedMessages;
    this.pubSubManager   = pubSubManager;
  }

  @Override
  public void onWebSocketConnect(WebSocketSessionContext context) {
    Optional<Account> account = context.getAuthenticated(Account.class);

    if (!account.isPresent()) {
      logger.debug("WS Connection with no authentication...");
      context.getClient().close(4001, "Authentication failed");
      return;
    }

    Optional<Device> device = account.get().getAuthenticatedDevice();

    if (!device.isPresent()) {
      logger.debug("WS Connection with no authenticated device...");
      context.getClient().close(4001, "Device authentication failed");
      return;
    }

    final WebSocketConnection connection = new WebSocketConnection(accountsManager, pushSender,
                                                                   storedMessages, pubSubManager,
                                                                   account.get(), device.get(),
                                                                   context.getClient());

    connection.onConnected();

    context.addListener(new WebSocketSessionContext.WebSocketEventListener() {
      @Override
      public void onWebSocketClose(WebSocketSessionContext context, int statusCode, String reason) {
        connection.onConnectionLost();
      }
    });
  }
}
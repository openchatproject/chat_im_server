package com.openchat.secureim.websocket;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.dispatch.DispatchChannel;
import com.openchat.secureim.controllers.MessageController;
import com.openchat.secureim.controllers.NoSuchUserException;
import com.openchat.secureim.entities.CryptoEncodingException;
import com.openchat.secureim.entities.EncryptedOutgoingMessage;
import com.openchat.secureim.entities.OutgoingMessageEntity;
import com.openchat.secureim.entities.OutgoingMessageEntityList;
import com.openchat.secureim.push.NotPushRegisteredException;
import com.openchat.secureim.push.PushSender;
import com.openchat.secureim.push.ReceiptSender;
import com.openchat.secureim.push.TransientPushFailureException;
import com.openchat.secureim.storage.Account;
import com.openchat.secureim.storage.Device;
import com.openchat.secureim.storage.MessagesManager;
import com.openchat.secureim.util.Constants;
import com.openchat.websocket.WebSocketClient;
import com.openchat.websocket.messages.WebSocketResponseMessage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.util.Iterator;

import static com.codahale.metrics.MetricRegistry.name;
import static com.openchat.secureim.entities.MessageProtos.Envelope;
import static com.openchat.secureim.storage.PubSubProtos.PubSubMessage;

public class WebSocketConnection implements DispatchChannel {

  private static final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  public  static final Histogram      messageTime    = metricRegistry.histogram(name(MessageController.class, "message_delivery_duration"));

  private static final Logger logger = LoggerFactory.getLogger(WebSocketConnection.class);

  private final ReceiptSender    receiptSender;
  private final PushSender       pushSender;
  private final MessagesManager  messagesManager;

  private final Account          account;
  private final Device           device;
  private final WebSocketClient  client;
  private final String           connectionId;

  public WebSocketConnection(PushSender pushSender,
                             ReceiptSender receiptSender,
                             MessagesManager messagesManager,
                             Account account,
                             Device device,
                             WebSocketClient client,
                             String connectionId)
  {
    this.pushSender      = pushSender;
    this.receiptSender   = receiptSender;
    this.messagesManager = messagesManager;
    this.account         = account;
    this.device          = device;
    this.client          = client;
    this.connectionId    = connectionId;
  }

  @Override
  public void onDispatchMessage(String channel, byte[] message) {
    try {
      PubSubMessage pubSubMessage = PubSubMessage.parseFrom(message);

      switch (pubSubMessage.getType().getNumber()) {
        case PubSubMessage.Type.QUERY_DB_VALUE:
          processStoredMessages();
          break;
        case PubSubMessage.Type.DELIVER_VALUE:
          sendMessage(Envelope.parseFrom(pubSubMessage.getContent()), Optional.absent(), false);
          break;
        case PubSubMessage.Type.CONNECTED_VALUE:
          if (pubSubMessage.hasContent() && !new String(pubSubMessage.getContent().toByteArray()).equals(connectionId)) {
            client.hardDisconnectQuietly();
          }
          break;
        default:
          logger.warn("Unknown pubsub message: " + pubSubMessage.getType().getNumber());
      }
    } catch (InvalidProtocolBufferException e) {
      logger.warn("Protobuf parse error", e);
    }
  }

  @Override
  public void onDispatchUnsubscribed(String channel) {
    client.close(1000, "OK");
  }

  public void onDispatchSubscribed(String channel) {
    processStoredMessages();
  }

  private void sendMessage(final Envelope                    message,
                           final Optional<StoredMessageInfo> storedMessageInfo,
                           final boolean                     requery)
  {
    try {
      EncryptedOutgoingMessage                   encryptedMessage = new EncryptedOutgoingMessage(message, device.getSignalingKey());
      Optional<byte[]>                           body             = Optional.fromNullable(encryptedMessage.toByteArray());
      ListenableFuture<WebSocketResponseMessage> response         = client.sendRequest("PUT", "/api/v1/message", null, body);

      Futures.addCallback(response, new FutureCallback<WebSocketResponseMessage>() {
        @Override
        public void onSuccess(@Nullable WebSocketResponseMessage response) {
          boolean isReceipt = message.getType() == Envelope.Type.RECEIPT;

          if (isSuccessResponse(response) && !isReceipt) {
            messageTime.update(System.currentTimeMillis() - message.getTimestamp());
          }

          if (isSuccessResponse(response)) {
            if (storedMessageInfo.isPresent()) messagesManager.delete(account.getNumber(), device.getId(), storedMessageInfo.get().id, storedMessageInfo.get().cached);
            if (!isReceipt)                    sendDeliveryReceiptFor(message);
            if (requery)                       processStoredMessages();
          } else if (!isSuccessResponse(response) && !storedMessageInfo.isPresent()) {
            requeueMessage(message);
          }
        }

        @Override
        public void onFailure(@Nonnull Throwable throwable) {
          if (!storedMessageInfo.isPresent()) requeueMessage(message);
        }

        private boolean isSuccessResponse(WebSocketResponseMessage response) {
          return response != null && response.getStatus() >= 200 && response.getStatus() < 300;
        }
      });
    } catch (CryptoEncodingException e) {
      logger.warn("Bad signaling key", e);
    }
  }

  private void requeueMessage(Envelope message) {
    pushSender.getWebSocketSender().queueMessage(account, device, message);

    try {
      pushSender.sendQueuedNotification(account, device);
    } catch (NotPushRegisteredException e) {
      logger.warn("requeueMessage", e);
    }
  }

  private void sendDeliveryReceiptFor(Envelope message) {
    try {
      receiptSender.sendReceipt(account, message.getSource(), message.getTimestamp(),
                                message.hasRelay() ? Optional.of(message.getRelay()) :
                                                     Optional.absent());
    } catch (NoSuchUserException | NotPushRegisteredException  e) {
      logger.info("No longer registered " + e.getMessage());
    } catch(IOException | TransientPushFailureException e) {
      logger.warn("Something wrong while sending receipt", e);
    } catch (WebApplicationException e) {
      logger.warn("Bad federated response for receipt: " + e.getResponse().getStatus());
    }
  }

  private void processStoredMessages() {
    OutgoingMessageEntityList       messages = messagesManager.getMessagesForDevice(account.getNumber(), device.getId());
    Iterator<OutgoingMessageEntity> iterator = messages.getMessages().iterator();

    while (iterator.hasNext()) {
      OutgoingMessageEntity message = iterator.next();
      Envelope.Builder      builder = Envelope.newBuilder()
                                              .setType(Envelope.Type.valueOf(message.getType()))
                                              .setSourceDevice(message.getSourceDevice())
                                              .setSource(message.getSource())
                                              .setTimestamp(message.getTimestamp());

      if (message.getMessage() != null) {
        builder.setLegacyMessage(ByteString.copyFrom(message.getMessage()));
      }

      if (message.getContent() != null) {
        builder.setContent(ByteString.copyFrom(message.getContent()));
      }

      if (message.getRelay() != null && !message.getRelay().isEmpty()) {
        builder.setRelay(message.getRelay());
      }

      sendMessage(builder.build(), Optional.of(new StoredMessageInfo(message.getId(), message.isCached())), !iterator.hasNext() && messages.hasMore());
    }

    if (!messages.hasMore()) {
      client.sendRequest("PUT", "/api/v1/queue/empty", null, Optional.<byte[]>absent());
    }
  }

  private static class StoredMessageInfo {
    private final long    id;
    private final boolean cached;

    private StoredMessageInfo(long id, boolean cached) {
      this.id     = id;
      this.cached = cached;
    }
  }
}

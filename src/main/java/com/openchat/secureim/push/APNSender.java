package com.openchat.secureim.push;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.notnoop.apns.APNS;
import com.notnoop.apns.ApnsService;
import com.notnoop.exceptions.NetworkIOException;
import net.spy.memcached.MemcachedClient;
import org.bouncycastle.openssl.PEMReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.entities.PendingMessage;
import com.openchat.secureim.storage.Account;
import com.openchat.secureim.storage.AccountsManager;
import com.openchat.secureim.storage.Device;
import com.openchat.secureim.storage.PubSubManager;
import com.openchat.secureim.storage.PubSubMessage;
import com.openchat.secureim.storage.StoredMessages;
import com.openchat.secureim.util.Constants;
import com.openchat.secureim.util.SystemMapper;
import com.openchat.secureim.util.Util;
import com.openchat.secureim.websocket.WebsocketAddress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;
import io.dropwizard.lifecycle.Managed;

public class APNSender implements Managed {

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          websocketMeter = metricRegistry.meter(name(getClass(), "websocket"));
  private final Meter          pushMeter      = metricRegistry.meter(name(getClass(), "push"));
  private final Meter          failureMeter   = metricRegistry.meter(name(getClass(), "failure"));
  private final Logger         logger         = LoggerFactory.getLogger(APNSender.class);

  private static final String PAYLOAD = "{\"aps\":{\"sound\":\"default\",\"alert\":{\"loc-key\":\"APN_Message\"},\"content-available\":1,\"category\":\"Signal_Message\"}}";

  private static final ObjectMapper mapper = SystemMapper.getMapper();

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  private final AccountsManager accounts;
  private final PubSubManager   pubSubManager;
  private final StoredMessages  storedMessages;
  private final MemcachedClient memcachedClient;

  private final String apnCertificate;
  private final String apnKey;

  private Optional<ApnsService> apnService;

  public APNSender(AccountsManager accounts,
                   PubSubManager pubSubManager,
                   StoredMessages storedMessages,
                   MemcachedClient memcachedClient,
                   String apnCertificate, String apnKey)
  {
    this.accounts        = accounts;
    this.pubSubManager   = pubSubManager;
    this.storedMessages  = storedMessages;
    this.apnCertificate  = apnCertificate;
    this.apnKey          = apnKey;
    this.memcachedClient = memcachedClient;
  }

  public void sendMessage(Account account, Device device,
                          String registrationId, PendingMessage message)
      throws TransientPushFailureException
  {
    try {
      String           serializedPendingMessage = mapper.writeValueAsString(message);
      WebsocketAddress websocketAddress         = new WebsocketAddress(account.getNumber(), device.getId());

      if (pubSubManager.publish(websocketAddress, new PubSubMessage(PubSubMessage.TYPE_DELIVER,
                                                                    serializedPendingMessage)))
      {
        websocketMeter.mark();
      } else {
        memcacheSet(registrationId, account.getNumber());
        storedMessages.insert(websocketAddress, message);

        if (!message.isReceipt()) {
          sendPush(registrationId);
        }
      }
    } catch (IOException e) {
      throw new TransientPushFailureException(e);
    }
  }

  private void sendPush(String registrationId)
      throws TransientPushFailureException
  {
    try {
      if (!apnService.isPresent()) {
        failureMeter.mark();
        throw new TransientPushFailureException("APN access not configured!");
      }

      apnService.get().push(registrationId, PAYLOAD);
      pushMeter.mark();
    } catch (NetworkIOException nioe) {
      logger.warn("Network Error", nioe);
      failureMeter.mark();
      throw new TransientPushFailureException(nioe);
    }

  }

  private static byte[] initializeKeyStore(String pemCertificate, String pemKey)
      throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException
  {
    PEMReader       reader           = new PEMReader(new InputStreamReader(new ByteArrayInputStream(pemCertificate.getBytes())));
    X509Certificate certificate      = (X509Certificate) reader.readObject();
    Certificate[]   certificateChain = {certificate};

    reader = new PEMReader(new InputStreamReader(new ByteArrayInputStream(pemKey.getBytes())));
    KeyPair keyPair = (KeyPair) reader.readObject();

    KeyStore keyStore = KeyStore.getInstance("pkcs12");
    keyStore.load(null);
    keyStore.setEntry("apn",
                      new KeyStore.PrivateKeyEntry(keyPair.getPrivate(), certificateChain),
                      new KeyStore.PasswordProtection("insecure".toCharArray()));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    keyStore.store(baos, "insecure".toCharArray());

    return baos.toByteArray();
  }

  @Override
  public void start() throws Exception {
    if (!Util.isEmpty(apnCertificate) && !Util.isEmpty(apnKey)) {
      byte[] keyStore = initializeKeyStore(apnCertificate, apnKey);

      this.apnService = Optional.of(APNS.newService()
                                        .withCert(new ByteArrayInputStream(keyStore), "insecure")
                                        .asQueued()
                                        .withProductionDestination().build());

      this.executor.scheduleAtFixedRate(new FeedbackRunnable(), 0, 1, TimeUnit.HOURS);
    } else {
      this.apnService = Optional.absent();
    }
  }

  @Override
  public void stop() throws Exception {
    if (apnService.isPresent()) {
      apnService.get().stop();
    }
  }

  private void memcacheSet(String registrationId, String number) {
    if (memcachedClient != null) {
      memcachedClient.set("APN-" + registrationId, 60 * 60 * 24, number);
    }
  }

  private Optional<String> memcacheGet(String registrationId) {
    if (memcachedClient != null) {
      return Optional.fromNullable((String)memcachedClient.get("APN-" + registrationId));
    } else {
      return Optional.absent();
    }
  }

  private class FeedbackRunnable implements Runnable {
    private void updateAccount(Account account, String registrationId) {
      boolean needsUpdate = false;

      for (Device device : account.getDevices()) {
        if (registrationId.equals(device.getApnId())) {
          needsUpdate = true;
          device.setApnId(null);
        }
      }

      if (needsUpdate) {
        accounts.update(account);
      }
    }

    @Override
    public void run() {
      if (apnService.isPresent()) {
        Map<String, Date> inactiveDevices = apnService.get().getInactiveDevices();

        for (String registrationId : inactiveDevices.keySet()) {
          Optional<String> number = memcacheGet(registrationId);

          if (number.isPresent()) {
            Optional<Account> account = accounts.get(number.get());

            if (account.isPresent()) {
              updateAccount(account.get(), registrationId);
            }
          } else {
            logger.warn("APN unregister event received for uncached ID: " + registrationId);
          }
        }
      }
    }
  }
}

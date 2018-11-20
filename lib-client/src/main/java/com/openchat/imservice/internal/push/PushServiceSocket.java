package com.openchat.imservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.openchat.protocal.IdentityKey;
import com.openchat.protocal.ecc.ECPublicKey;
import com.openchat.protocal.logging.Log;
import com.openchat.protocal.state.PreKeyBundle;
import com.openchat.protocal.state.PreKeyRecord;
import com.openchat.protocal.state.SignedPreKeyRecord;
import com.openchat.protocal.util.Pair;
import com.openchat.protocal.util.guava.Optional;
import com.openchat.imservice.api.crypto.DigestingOutputStream;
import com.openchat.imservice.api.messages.OpenchatServiceAttachment.ProgressListener;
import com.openchat.imservice.api.messages.calls.TurnServerInfo;
import com.openchat.imservice.api.messages.multidevice.DeviceInfo;
import com.openchat.imservice.api.profiles.OpenchatServiceProfile;
import com.openchat.imservice.api.push.ContactTokenDetails;
import com.openchat.imservice.api.push.OpenchatServiceAddress;
import com.openchat.imservice.api.push.SignedPreKeyEntity;
import com.openchat.imservice.api.push.exceptions.AuthorizationFailedException;
import com.openchat.imservice.api.push.exceptions.ExpectationFailedException;
import com.openchat.imservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import com.openchat.imservice.api.push.exceptions.NotFoundException;
import com.openchat.imservice.api.push.exceptions.PushNetworkException;
import com.openchat.imservice.api.push.exceptions.RateLimitException;
import com.openchat.imservice.api.push.exceptions.UnregisteredUserException;
import com.openchat.imservice.api.util.CredentialsProvider;
import com.openchat.imservice.internal.configuration.OpenchatServiceConfiguration;
import com.openchat.imservice.internal.configuration.OpenchatUrl;
import com.openchat.imservice.internal.push.exceptions.MismatchedDevicesException;
import com.openchat.imservice.internal.push.exceptions.StaleDevicesException;
import com.openchat.imservice.internal.push.http.DigestingRequestBody;
import com.openchat.imservice.internal.push.http.OutputStreamFactory;
import com.openchat.imservice.internal.util.Base64;
import com.openchat.imservice.internal.util.JsonUtil;
import com.openchat.imservice.internal.util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PushServiceSocket {

  private static final String TAG = PushServiceSocket.class.getSimpleName();

  private static final String CREATE_ACCOUNT_SMS_PATH   = "/v1/accounts/sms/code/%s";
  private static final String CREATE_ACCOUNT_VOICE_PATH = "/v1/accounts/voice/code/%s";
  private static final String VERIFY_ACCOUNT_CODE_PATH  = "/v1/accounts/code/%s";
  private static final String REGISTER_GCM_PATH         = "/v1/accounts/gcm/";
  private static final String REQUEST_TOKEN_PATH        = "/v1/accounts/token";
  private static final String TURN_SERVER_INFO          = "/v1/accounts/turn";
  private static final String SET_ACCOUNT_ATTRIBUTES    = "/v1/accounts/attributes/";

  private static final String PREKEY_METADATA_PATH      = "/v2/keys/";
  private static final String PREKEY_PATH               = "/v2/keys/%s";
  private static final String PREKEY_DEVICE_PATH        = "/v2/keys/%s/%s";
  private static final String SIGNED_PREKEY_PATH        = "/v2/keys/signed";

  private static final String PROVISIONING_CODE_PATH    = "/v1/devices/provisioning/code";
  private static final String PROVISIONING_MESSAGE_PATH = "/v1/provisioning/%s";
  private static final String DEVICE_PATH               = "/v1/devices/%s";

  private static final String DIRECTORY_TOKENS_PATH     = "/v1/directory/tokens";
  private static final String DIRECTORY_VERIFY_PATH     = "/v1/directory/%s";
  private static final String MESSAGE_PATH              = "/v1/messages/%s";
  private static final String ACKNOWLEDGE_MESSAGE_PATH  = "/v1/messages/%s/%d";
  private static final String RECEIPT_PATH              = "/v1/receipt/%s/%d";
  private static final String ATTACHMENT_PATH           = "/v1/attachments/%s";

  private static final String PROFILE_PATH              = "/v1/profile/%s";

  private       long      soTimeoutMillis = TimeUnit.SECONDS.toMillis(30);
  private final Set<Call> connections     = new HashSet<>();

  private final OpenchatServiceConfiguration openchatServiceConfiguration;
  private final CredentialsProvider        credentialsProvider;
  private final String                     userAgent;
  private final SecureRandom               random;

  public PushServiceSocket(OpenchatServiceConfiguration openchatServiceConfiguration, CredentialsProvider credentialsProvider, String userAgent) {
    this.credentialsProvider         = credentialsProvider;
    this.userAgent                   = userAgent;
    this.openchatServiceConfiguration  = openchatServiceConfiguration;
    this.random                      = new SecureRandom();
  }

  public void createAccount(boolean voice) throws IOException {
    String path = voice ? CREATE_ACCOUNT_VOICE_PATH : CREATE_ACCOUNT_SMS_PATH;
    makeServiceRequest(String.format(path, credentialsProvider.getUser()), "GET", null);
  }

  public void verifyAccountCode(String verificationCode, String openchatingKey, int registrationId, boolean fetchesMessages)
      throws IOException
  {
    AccountAttributes openchatingKeyEntity = new AccountAttributes(openchatingKey, registrationId, fetchesMessages);
    makeServiceRequest(String.format(VERIFY_ACCOUNT_CODE_PATH, verificationCode),
                       "PUT", JsonUtil.toJson(openchatingKeyEntity));
  }

  public void setAccountAttributes(String openchatingKey, int registrationId, boolean fetchesMessages) throws IOException {
    AccountAttributes accountAttributes = new AccountAttributes(openchatingKey, registrationId, fetchesMessages);
    makeServiceRequest(SET_ACCOUNT_ATTRIBUTES, "PUT", JsonUtil.toJson(accountAttributes));
  }

  public String getAccountVerificationToken() throws IOException {
    String responseText = makeServiceRequest(REQUEST_TOKEN_PATH, "GET", null);
    return JsonUtil.fromJson(responseText, AuthorizationToken.class).getToken();
  }

  public String getNewDeviceVerificationCode() throws IOException {
    String responseText = makeServiceRequest(PROVISIONING_CODE_PATH, "GET", null);
    return JsonUtil.fromJson(responseText, DeviceCode.class).getVerificationCode();
  }

  public List<DeviceInfo> getDevices() throws IOException {
    String responseText = makeServiceRequest(String.format(DEVICE_PATH, ""), "GET", null);
    return JsonUtil.fromJson(responseText, DeviceInfoList.class).getDevices();
  }

  public void removeDevice(long deviceId) throws IOException {
    makeServiceRequest(String.format(DEVICE_PATH, String.valueOf(deviceId)), "DELETE", null);
  }

  public void sendProvisioningMessage(String destination, byte[] body) throws IOException {
    makeServiceRequest(String.format(PROVISIONING_MESSAGE_PATH, destination), "PUT",
                       JsonUtil.toJson(new ProvisioningMessage(Base64.encodeBytes(body))));
  }

  public void sendReceipt(String destination, long messageId, Optional<String> relay) throws IOException {
    String path = String.format(RECEIPT_PATH, destination, messageId);

    if (relay.isPresent()) {
      path += "?relay=" + relay.get();
    }

    makeServiceRequest(path, "PUT", null);
  }

  public void registerGcmId(String gcmRegistrationId) throws IOException {
    GcmRegistrationId registration = new GcmRegistrationId(gcmRegistrationId, true);
    makeServiceRequest(REGISTER_GCM_PATH, "PUT", JsonUtil.toJson(registration));
  }

  public void unregisterGcmId() throws IOException {
    makeServiceRequest(REGISTER_GCM_PATH, "DELETE", null);
  }

  public SendMessageResponse sendMessage(OutgoingPushMessageList bundle)
      throws IOException
  {
    try {
      String responseText = makeServiceRequest(String.format(MESSAGE_PATH, bundle.getDestination()), "PUT", JsonUtil.toJson(bundle));

      if (responseText == null) return new SendMessageResponse(false);
      else                      return JsonUtil.fromJson(responseText, SendMessageResponse.class);
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(bundle.getDestination(), nfe);
    }
  }

  public List<OpenchatServiceEnvelopeEntity> getMessages() throws IOException {
    String responseText = makeServiceRequest(String.format(MESSAGE_PATH, ""), "GET", null);
    return JsonUtil.fromJson(responseText, OpenchatServiceEnvelopeEntityList.class).getMessages();
  }

  public void acknowledgeMessage(String sender, long timestamp) throws IOException {
    makeServiceRequest(String.format(ACKNOWLEDGE_MESSAGE_PATH, sender, timestamp), "DELETE", null);
  }

  public void registerPreKeys(IdentityKey identityKey,
                              SignedPreKeyRecord signedPreKey,
                              List<PreKeyRecord> records)
      throws IOException
  {
    List<PreKeyEntity> entities = new LinkedList<>();

    for (PreKeyRecord record : records) {
      PreKeyEntity entity = new PreKeyEntity(record.getId(),
                                             record.getKeyPair().getPublicKey());

      entities.add(entity);
    }

    SignedPreKeyEntity signedPreKeyEntity = new SignedPreKeyEntity(signedPreKey.getId(),
                                                                   signedPreKey.getKeyPair().getPublicKey(),
                                                                   signedPreKey.getSignature());

    makeServiceRequest(String.format(PREKEY_PATH, ""), "PUT",
                       JsonUtil.toJson(new PreKeyState(entities, signedPreKeyEntity, identityKey)));
  }

  public int getAvailablePreKeys() throws IOException {
    String       responseText = makeServiceRequest(PREKEY_METADATA_PATH, "GET", null);
    PreKeyStatus preKeyStatus = JsonUtil.fromJson(responseText, PreKeyStatus.class);

    return preKeyStatus.getCount();
  }

  public List<PreKeyBundle> getPreKeys(OpenchatServiceAddress destination, int deviceIdInteger) throws IOException {
    try {
      String deviceId = String.valueOf(deviceIdInteger);

      if (deviceId.equals("1"))
        deviceId = "*";

      String path = String.format(PREKEY_DEVICE_PATH, destination.getNumber(), deviceId);

      if (destination.getRelay().isPresent()) {
        path = path + "?relay=" + destination.getRelay().get();
      }

      String             responseText = makeServiceRequest(path, "GET", null);
      PreKeyResponse     response     = JsonUtil.fromJson(responseText, PreKeyResponse.class);
      List<PreKeyBundle> bundles      = new LinkedList<>();

      for (PreKeyResponseItem device : response.getDevices()) {
        ECPublicKey preKey                = null;
        ECPublicKey signedPreKey          = null;
        byte[]      signedPreKeySignature = null;
        int         preKeyId              = -1;
        int         signedPreKeyId        = -1;

        if (device.getSignedPreKey() != null) {
          signedPreKey          = device.getSignedPreKey().getPublicKey();
          signedPreKeyId        = device.getSignedPreKey().getKeyId();
          signedPreKeySignature = device.getSignedPreKey().getSignature();
        }

        if (device.getPreKey() != null) {
          preKeyId = device.getPreKey().getKeyId();
          preKey   = device.getPreKey().getPublicKey();
        }

        bundles.add(new PreKeyBundle(device.getRegistrationId(), device.getDeviceId(), preKeyId,
                                     preKey, signedPreKeyId, signedPreKey, signedPreKeySignature,
                                     response.getIdentityKey()));
      }

      return bundles;
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(destination.getNumber(), nfe);
    }
  }

  public PreKeyBundle getPreKey(OpenchatServiceAddress destination, int deviceId) throws IOException {
    try {
      String path = String.format(PREKEY_DEVICE_PATH, destination.getNumber(),
                                  String.valueOf(deviceId));

      if (destination.getRelay().isPresent()) {
        path = path + "?relay=" + destination.getRelay().get();
      }

      String         responseText = makeServiceRequest(path, "GET", null);
      PreKeyResponse response     = JsonUtil.fromJson(responseText, PreKeyResponse.class);

      if (response.getDevices() == null || response.getDevices().size() < 1)
        throw new IOException("Empty prekey list");

      PreKeyResponseItem device                = response.getDevices().get(0);
      ECPublicKey        preKey                = null;
      ECPublicKey        signedPreKey          = null;
      byte[]             signedPreKeySignature = null;
      int                preKeyId              = -1;
      int                signedPreKeyId        = -1;

      if (device.getPreKey() != null) {
        preKeyId = device.getPreKey().getKeyId();
        preKey   = device.getPreKey().getPublicKey();
      }

      if (device.getSignedPreKey() != null) {
        signedPreKeyId        = device.getSignedPreKey().getKeyId();
        signedPreKey          = device.getSignedPreKey().getPublicKey();
        signedPreKeySignature = device.getSignedPreKey().getSignature();
      }

      return new PreKeyBundle(device.getRegistrationId(), device.getDeviceId(), preKeyId, preKey,
                              signedPreKeyId, signedPreKey, signedPreKeySignature, response.getIdentityKey());
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(destination.getNumber(), nfe);
    }
  }

  public SignedPreKeyEntity getCurrentSignedPreKey() throws IOException {
    try {
      String responseText = makeServiceRequest(SIGNED_PREKEY_PATH, "GET", null);
      return JsonUtil.fromJson(responseText, SignedPreKeyEntity.class);
    } catch (NotFoundException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  public void setCurrentSignedPreKey(SignedPreKeyRecord signedPreKey) throws IOException {
    SignedPreKeyEntity signedPreKeyEntity = new SignedPreKeyEntity(signedPreKey.getId(),
                                                                   signedPreKey.getKeyPair().getPublicKey(),
                                                                   signedPreKey.getSignature());
    makeServiceRequest(SIGNED_PREKEY_PATH, "PUT", JsonUtil.toJson(signedPreKeyEntity));
  }

  public Pair<Long, byte[]> sendAttachment(PushAttachmentData attachment) throws IOException {
    String               response      = makeServiceRequest(String.format(ATTACHMENT_PATH, ""), "GET", null);
    AttachmentDescriptor attachmentKey = JsonUtil.fromJson(response, AttachmentDescriptor.class);

    if (attachmentKey == null || attachmentKey.getLocation() == null) {
      throw new IOException("Server failed to allocate an attachment key!");
    }

    Log.w(TAG, "Got attachment content location: " + attachmentKey.getLocation());

    byte[] digest = uploadAttachment("PUT", attachmentKey.getLocation(), attachment.getData(),
                                     attachment.getDataSize(), attachment.getOutputStreamFactory(), attachment.getListener());

    return new Pair<>(attachmentKey.getId(), digest);
  }

  public void retrieveAttachment(String relay, long attachmentId, File destination, int maxSizeBytes, ProgressListener listener) throws IOException {
    String path = String.format(ATTACHMENT_PATH, String.valueOf(attachmentId));

    if (!Util.isEmpty(relay)) {
      path = path + "?relay=" + relay;
    }

    String               response   = makeServiceRequest(path, "GET", null);
    AttachmentDescriptor descriptor = JsonUtil.fromJson(response, AttachmentDescriptor.class);

    Log.w(TAG, "Attachment: " + attachmentId + " is at: " + descriptor.getLocation());
    downloadAttachment(descriptor.getLocation(), destination, maxSizeBytes, listener);
  }

  public OpenchatServiceProfile retrieveProfile(OpenchatServiceAddress target) throws
      NonSuccessfulResponseCodeException, PushNetworkException
  {
    try {
      String response = makeServiceRequest(String.format(PROFILE_PATH, target.getNumber()), "GET", null);
      return JsonUtil.fromJson(response, OpenchatServiceProfile.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public void setProfileName(String name) throws NonSuccessfulResponseCodeException, PushNetworkException {
    makeServiceRequest(String.format(PROFILE_PATH, "name/" + (name == null ? "" : URLEncoder.encode(name))), "PUT", "");
  }

  public void setProfileAvatar(ProfileAvatarData profileAvatar)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    String                        response       = makeServiceRequest(String.format(PROFILE_PATH, "form/avatar"), "GET", null);
    ProfileAvatarUploadAttributes formAttributes;

    try {
      formAttributes = JsonUtil.fromJson(response, ProfileAvatarUploadAttributes.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }

    if (profileAvatar != null) {
      uploadToCdn(formAttributes.getAcl(), formAttributes.getKey(),
                  formAttributes.getPolicy(), formAttributes.getAlgorithm(),
                  formAttributes.getCredential(), formAttributes.getDate(),
                  formAttributes.getSignature(), profileAvatar.getData(),
                  profileAvatar.getContentType(), profileAvatar.getDataLength(),
                  profileAvatar.getOutputStreamFactory());
    }
  }

  public List<ContactTokenDetails> retrieveDirectory(Set<String> contactTokens)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    try {
      ContactTokenList        contactTokenList = new ContactTokenList(new LinkedList<>(contactTokens));
      String                  response         = makeServiceRequest(DIRECTORY_TOKENS_PATH, "PUT", JsonUtil.toJson(contactTokenList));
      ContactTokenDetailsList activeTokens     = JsonUtil.fromJson(response, ContactTokenDetailsList.class);

      return activeTokens.getContacts();
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public ContactTokenDetails getContactTokenDetails(String contactToken) throws IOException {
    try {
      String response = makeServiceRequest(String.format(DIRECTORY_VERIFY_PATH, contactToken), "GET", null);
      return JsonUtil.fromJson(response, ContactTokenDetails.class);
    } catch (NotFoundException nfe) {
      return null;
    }
  }

  public TurnServerInfo getTurnServerInfo() throws IOException {
    String response = makeServiceRequest(TURN_SERVER_INFO, "GET", null);
    return JsonUtil.fromJson(response, TurnServerInfo.class);
  }

  public void setSoTimeoutMillis(long soTimeoutMillis) {
    this.soTimeoutMillis = soTimeoutMillis;
  }

  public void cancelInFlightRequests() {
    synchronized (connections) {
      Log.w(TAG, "Canceling: " + connections.size());
      for (Call connection : connections) {
        Log.w(TAG, "Canceling: " + connection);
        connection.cancel();
      }
    }
  }

  private void downloadAttachment(String url, File localDestination, int maxSizeBytes, ProgressListener listener)
      throws IOException
  {
    URL               downloadUrl = new URL(url);
    HttpURLConnection connection  = (HttpURLConnection) downloadUrl.openConnection();
    connection.setRequestProperty("Content-Type", "application/octet-stream");
    connection.setRequestMethod("GET");
    connection.setDoInput(true);

    try {
      if (connection.getResponseCode() != 200) {
        throw new NonSuccessfulResponseCodeException("Bad response: " + connection.getResponseCode());
      }

      OutputStream output        = new FileOutputStream(localDestination);
      InputStream  input         = connection.getInputStream();
      byte[]       buffer        = new byte[4096];
      int          contentLength = connection.getContentLength();
      int         read,totalRead = 0;

      if (contentLength > maxSizeBytes) {
        throw new NonSuccessfulResponseCodeException("File exceeds maximum size.");
      }

      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
        totalRead += read;

        if (totalRead > maxSizeBytes) {
          localDestination.delete();
          throw new NonSuccessfulResponseCodeException("File exceeds maximum size.");
        }

        if (listener != null) {
          listener.onAttachmentProgress(contentLength, totalRead);
        }
      }

      output.close();
      Log.w(TAG, "Downloaded: " + url + " to: " + localDestination.getAbsolutePath());
    } catch (IOException ioe) {
      throw new PushNetworkException(ioe);
    } finally {
      connection.disconnect();
    }
  }

  private byte[] uploadAttachment(String method, String url, InputStream data,
                                  long dataSize, OutputStreamFactory outputStreamFactory, ProgressListener listener)
    throws IOException
  {
    URL                uploadUrl  = new URL(url);
    HttpsURLConnection connection = (HttpsURLConnection) uploadUrl.openConnection();
    connection.setDoOutput(true);

    if (dataSize > 0) {
      connection.setFixedLengthStreamingMode((int) outputStreamFactory.getCiphertextLength(dataSize));
    } else {
      connection.setChunkedStreamingMode(0);
    }

    connection.setRequestMethod(method);
    connection.setRequestProperty("Content-Type", "application/octet-stream");
    connection.setRequestProperty("Connection", "close");
    connection.connect();

    try {
      DigestingOutputStream out    = outputStreamFactory.createFor(connection.getOutputStream());
      byte[]                buffer = new byte[4096];
      int            read, written = 0;

      while ((read = data.read(buffer)) != -1) {
        out.write(buffer, 0, read);
        written += read;

        if (listener != null) {
          listener.onAttachmentProgress(dataSize, written);
        }
      }

      out.flush();
      data.close();
      out.close();

      if (connection.getResponseCode() != 200) {
        throw new IOException("Bad response: " + connection.getResponseCode() + " " + connection.getResponseMessage());
      }

      return out.getTransmittedDigest();
    } finally {
      connection.disconnect();
    }
  }

  private byte[] uploadToCdn(String acl, String key, String policy, String algorithm,
                             String credential, String date, String signature,
                             InputStream data, String contentType, long length,
                             OutputStreamFactory outputStreamFactory)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    try {
      OpenchatUrl        openchatUrl     = getRandom(openchatServiceConfiguration.getOpenchatCdnUrls(), random);
      String           url           = openchatUrl.getUrl();
      Optional<String> hostHeader    = openchatUrl.getHostHeader();
      TrustManager[]   trustManagers = openchatUrl.getTrustManagers();

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers, null);

      OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
          .sslSocketFactory(context.getSocketFactory(), (X509TrustManager)trustManagers[0])
          .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
          .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS);

      DigestingRequestBody file = new DigestingRequestBody(data, outputStreamFactory, contentType, length);

      RequestBody requestBody = new MultipartBody.Builder()
          .setType(MultipartBody.FORM)
          .addFormDataPart("acl", acl)
          .addFormDataPart("key", key)
          .addFormDataPart("policy", policy)
          .addFormDataPart("Content-Type", contentType)
          .addFormDataPart("x-amz-algorithm", algorithm)
          .addFormDataPart("x-amz-credential", credential)
          .addFormDataPart("x-amz-date", date)
          .addFormDataPart("x-amz-signature", signature)
          .addFormDataPart("file", "file", file)
          .build();

      Request.Builder request = new Request.Builder().url(url).post(requestBody);

      if (openchatUrl.getConnectionSpec().isPresent()) {
        okHttpClientBuilder.connectionSpecs(Collections.singletonList(openchatUrl.getConnectionSpec().get()));
      } else {
        okHttpClientBuilder.connectionSpecs(Util.immutableList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS));
      }

      if (hostHeader.isPresent()) {
        request.addHeader("Host", hostHeader.get());
      }

      Call call = okHttpClientBuilder.build().newCall(request.build());

      synchronized (connections) {
        connections.add(call);
      }

      try {
        Response response;

        try {
          response = call.execute();
        } catch (IOException e) {
          throw new PushNetworkException(e);
        }

        if (response.isSuccessful()) return file.getTransmittedDigest();
        else                         throw new NonSuccessfulResponseCodeException("Response: " + response);
      } finally {
        synchronized (connections) {
          connections.remove(call);
        }
      }
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  private String makeServiceRequest(String urlFragment, String method, String body)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    Response response = getServiceConnection(urlFragment, method, body);

    int    responseCode;
    String responseMessage;
    String responseBody;

    try {
      responseCode    = response.code();
      responseMessage = response.message();
      responseBody    = response.body().string();
    } catch (IOException ioe) {
      throw new PushNetworkException(ioe);
    }

    switch (responseCode) {
      case 413:
        throw new RateLimitException("Rate limit exceeded: " + responseCode);
      case 401:
      case 403:
        throw new AuthorizationFailedException("Authorization failed!");
      case 404:
        throw new NotFoundException("Not found");
      case 409:
        MismatchedDevices mismatchedDevices;

        try {
          mismatchedDevices = JsonUtil.fromJson(responseBody, MismatchedDevices.class);
        } catch (JsonProcessingException e) {
          Log.w(TAG, e);
          throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
        } catch (IOException e) {
          throw new PushNetworkException(e);
        }

        throw new MismatchedDevicesException(mismatchedDevices);
      case 410:
        StaleDevices staleDevices;

        try {
          staleDevices = JsonUtil.fromJson(responseBody, StaleDevices.class);
        } catch (JsonProcessingException e) {
          throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
        } catch (IOException e) {
          throw new PushNetworkException(e);
        }

        throw new StaleDevicesException(staleDevices);
      case 411:
        DeviceLimit deviceLimit;

        try {
          deviceLimit = JsonUtil.fromJson(responseBody, DeviceLimit.class);
        } catch (JsonProcessingException e) {
          throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
        } catch (IOException e) {
          throw new PushNetworkException(e);
        }

        throw new DeviceLimitExceededException(deviceLimit);
      case 417:
        throw new ExpectationFailedException();
    }

    if (responseCode != 200 && responseCode != 204) {
        throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " +
                                                     responseMessage);
    }

    return responseBody;
  }

  private Response getServiceConnection(String urlFragment, String method, String body)
      throws PushNetworkException
  {
    try {
      OpenchatUrl        openchatUrl     = getRandom(openchatServiceConfiguration.getOpenchatServiceUrls(), random);
      String           url           = openchatUrl.getUrl();
      Optional<String> hostHeader    = openchatUrl.getHostHeader();
      TrustManager[]   trustManagers = openchatUrl.getTrustManagers();

      Log.w(TAG, "Push service URL: " + url);
      Log.w(TAG, "Opening URL: " + String.format("%s%s", url, urlFragment));

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers, null);

      OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
          .sslSocketFactory(context.getSocketFactory(), (X509TrustManager)trustManagers[0])
          .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
          .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS);

      Request.Builder request = new Request.Builder();
      request.url(String.format("%s%s", url, urlFragment));

      if (body != null) {
        request.method(method, RequestBody.create(MediaType.parse("application/json"), body));
      } else {
        request.method(method, null);
      }

      if (credentialsProvider.getPassword() != null) {
        request.addHeader("Authorization", getAuthorizationHeader(credentialsProvider));
      }

      if (userAgent != null) {
        request.addHeader("X-Openchat-Agent", userAgent);
      }

      if (openchatUrl.getConnectionSpec().isPresent()) {
        okHttpClientBuilder.connectionSpecs(Collections.singletonList(openchatUrl.getConnectionSpec().get()));
      } else {
        okHttpClientBuilder.connectionSpecs(Util.immutableList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS));
      }

      if (hostHeader.isPresent()) {
        request.addHeader("Host", hostHeader.get());
      }

      Call call = okHttpClientBuilder.build().newCall(request.build());

      synchronized (connections) {
        connections.add(call);
      }

      try {
        return call.execute();
      } finally {
        synchronized (connections) {
          connections.remove(call);
        }
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  private String getAuthorizationHeader(CredentialsProvider credentialsProvider) {
    try {
      return "Basic " + Base64.encodeBytes((credentialsProvider.getUser() + ":" + credentialsProvider.getPassword()).getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private OpenchatUrl getRandom(OpenchatUrl[] connections, SecureRandom random) {
    return connections[random.nextInt(connections.length)];
  }

  private static class GcmRegistrationId {

    @JsonProperty
    private String gcmRegistrationId;

    @JsonProperty
    private boolean webSocketChannel;

    public GcmRegistrationId() {}

    public GcmRegistrationId(String gcmRegistrationId, boolean webSocketChannel) {
      this.gcmRegistrationId = gcmRegistrationId;
      this.webSocketChannel  = webSocketChannel;
    }
  }

  private static class AttachmentDescriptor {
    @JsonProperty
    private long id;

    @JsonProperty
    private String location;

    public long getId() {
      return id;
    }

    public String getLocation() {
      return location;
    }
  }
}

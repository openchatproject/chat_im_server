package com.openchat.secureim.auth;

import com.google.common.base.Optional;
import com.yammer.dropwizard.auth.AuthenticationException;
import com.yammer.dropwizard.auth.Authenticator;
import com.yammer.dropwizard.auth.basic.BasicCredentials;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.storage.Account;
import com.openchat.secureim.storage.Device;
import com.openchat.secureim.storage.AccountsManager;

import java.util.concurrent.TimeUnit;

public class AccountAuthenticator implements Authenticator<BasicCredentials, Account> {

  private final Meter authenticationFailedMeter    = Metrics.newMeter(AccountAuthenticator.class,
                                                                      "authentication", "failed",
                                                                      TimeUnit.MINUTES);

  private final Meter authenticationSucceededMeter = Metrics.newMeter(AccountAuthenticator.class,
                                                                      "authentication", "succeeded",
                                                                      TimeUnit.MINUTES);

  private final Logger logger = LoggerFactory.getLogger(AccountAuthenticator.class);

  private final AccountsManager accountsManager;

  public AccountAuthenticator(AccountsManager accountsManager) {
    this.accountsManager = accountsManager;
  }

  @Override
  public Optional<Account> authenticate(BasicCredentials basicCredentials)
      throws AuthenticationException
  {
    try {
      AuthorizationHeader authorizationHeader = AuthorizationHeader.fromUserAndPassword(basicCredentials.getUsername(), basicCredentials.getPassword());
      Optional<Account>   account             = accountsManager.get(authorizationHeader.getNumber());

      if (!account.isPresent()) {
        return Optional.absent();
      }

      Optional<Device> device = account.get().getDevice(authorizationHeader.getDeviceId());

      if (!device.isPresent()) {
        return Optional.absent();
      }

      if (device.get().getAuthenticationCredentials().verify(basicCredentials.getPassword())) {
        authenticationSucceededMeter.mark();
        account.get().setAuthenticatedDevice(device.get());
        return account;
      }

      authenticationFailedMeter.mark();
      return Optional.absent();
    } catch (InvalidAuthorizationHeaderException iahe) {
      return Optional.absent();
    }
  }
}

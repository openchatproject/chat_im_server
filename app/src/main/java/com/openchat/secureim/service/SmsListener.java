package com.openchat.secureim.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.openchat.secureim.ApplicationContext;
import com.openchat.secureim.jobs.SmsReceiveJob;
import com.openchat.secureim.util.OpenchatServicePreferences;
import com.openchat.secureim.util.Util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsListener extends BroadcastReceiver {

  private static final String SMS_RECEIVED_ACTION  = Telephony.Sms.Intents.SMS_RECEIVED_ACTION;
  private static final String SMS_DELIVERED_ACTION = Telephony.Sms.Intents.SMS_DELIVER_ACTION;

  private static final Pattern CHALLENGE_PATTERN = Pattern.compile(".*Your OpenchatService verification code: ([0-9]{3,4})-([0-9]{3,4}).*");

  private boolean isExemption(SmsMessage message, String messageBody) {

    if (message.getMessageClass() == SmsMessage.MessageClass.CLASS_0)
      return true;

    if (messageBody.startsWith("Sparebank1://otp?")) {
      return true;
    }

    return
      message.getOriginatingAddress().length() < 7 &&
      (messageBody.toUpperCase().startsWith("//ANDROID:") || // Sprint Visual Voicemail
       messageBody.startsWith("//BREW:")); //BREW stands for “Binary Runtime Environment for Wireless"
  }

  private SmsMessage getSmsMessageFromIntent(Intent intent) {
    Bundle bundle             = intent.getExtras();
    Object[] pdus             = (Object[])bundle.get("pdus");

    if (pdus == null || pdus.length == 0)
      return null;

    return SmsMessage.createFromPdu((byte[])pdus[0]);
  }

  private String getSmsMessageBodyFromIntent(Intent intent) {
    Bundle bundle             = intent.getExtras();
    Object[] pdus             = (Object[])bundle.get("pdus");
    StringBuilder bodyBuilder = new StringBuilder();

    if (pdus == null)
      return null;

    for (Object pdu : pdus)
      bodyBuilder.append(SmsMessage.createFromPdu((byte[])pdu).getDisplayMessageBody());

    return bodyBuilder.toString();
  }

  private boolean isRelevant(Context context, Intent intent) {
    SmsMessage message = getSmsMessageFromIntent(intent);
    String messageBody = getSmsMessageBodyFromIntent(intent);

    if (message == null && messageBody == null)
      return false;

    if (isExemption(message, messageBody))
      return false;

    if (!ApplicationMigrationService.isDatabaseImported(context))
      return false;

    if (isChallenge(context, intent))
      return false;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
        SMS_RECEIVED_ACTION.equals(intent.getAction()) &&
        Util.isDefaultSmsProvider(context))
    {
      return false;
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT &&
        OpenchatServicePreferences.isInterceptAllSmsEnabled(context))
    {
      return true;
    }

    return false;
  }

  private boolean isChallenge(Context context, Intent intent) {
    String messageBody = getSmsMessageBodyFromIntent(intent);

    if (messageBody == null)
      return false;

    if (CHALLENGE_PATTERN.matcher(messageBody).matches() &&
        OpenchatServicePreferences.isVerifying(context))
    {
      return true;
    }

    return false;
  }

  private String parseChallenge(Context context, Intent intent) {
    String  messageBody      = getSmsMessageBodyFromIntent(intent);
    Matcher challengeMatcher = CHALLENGE_PATTERN.matcher(messageBody);

    if (!challengeMatcher.matches()) {
      throw new AssertionError("Expression should match.");
    }

    return challengeMatcher.group(1) + challengeMatcher.group(2);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w("SMSListener", "Got SMS broadcast...");

    if (SMS_RECEIVED_ACTION.equals(intent.getAction()) && isChallenge(context, intent)) {
      Log.w("SmsListener", "Got challenge!");
      Intent challengeIntent = new Intent(RegistrationService.CHALLENGE_EVENT);
      challengeIntent.putExtra(RegistrationService.CHALLENGE_EXTRA, parseChallenge(context, intent));
      context.sendBroadcast(challengeIntent);

      abortBroadcast();
    } else if ((intent.getAction().equals(SMS_DELIVERED_ACTION)) ||
               (intent.getAction().equals(SMS_RECEIVED_ACTION)) && isRelevant(context, intent))
    {
      Object[] pdus = (Object[])intent.getExtras().get("pdus");
      ApplicationContext.getInstance(context).getJobManager().add(new SmsReceiveJob(context, pdus));

      abortBroadcast();
    }
  }
}

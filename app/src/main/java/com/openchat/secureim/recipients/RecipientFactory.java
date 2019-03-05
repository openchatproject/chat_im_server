package com.openchat.secureim.recipients;

import android.content.Context;
import android.util.Log;

import com.openchat.secureim.contacts.ContactPhotoFactory;
import com.openchat.secureim.database.CanonicalAddressDatabase;
import com.openchat.imservice.push.IncomingPushMessage;
import com.openchat.imservice.util.Util;

import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

public class RecipientFactory {

  private static final RecipientProvider provider = new RecipientProvider();

  public static Recipients getRecipientsForIds(Context context, String recipientIds, boolean asynchronous) {
    if (Util.isEmpty(recipientIds))
      return new Recipients(new LinkedList<Recipient>());

    List<Recipient> results   = new LinkedList<Recipient>();
    StringTokenizer tokenizer = new StringTokenizer(recipientIds.trim(), " ");

    while (tokenizer.hasMoreTokens()) {
      String recipientId  = tokenizer.nextToken();
      Recipient recipient = getRecipientFromProviderId(context, recipientId, asynchronous);

      results.add(recipient);
    }

    return new Recipients(results);
  }

  private static Recipient getRecipientForNumber(Context context, String number, boolean asynchronous) {
    long recipientId = CanonicalAddressDatabase.getInstance(context).getCanonicalAddressId(number);
    return provider.getRecipient(context, recipientId, asynchronous);
  }

  public static Recipients getRecipientsFromString(Context context, String rawText, boolean asynchronous)
      throws RecipientFormattingException
  {
    if (rawText == null) {
      throw new RecipientFormattingException("Null recipient string specified");
    }

    List<Recipient> results   = new LinkedList<Recipient>();
    StringTokenizer tokenizer = new StringTokenizer(rawText, ",");

    while (tokenizer.hasMoreTokens()) {
      Recipient recipient = parseRecipient(context, tokenizer.nextToken(), asynchronous);
      if( recipient != null )
        results.add(recipient);
    }

    return new Recipients(results);
  }

  public static Recipients getRecipientsFromMessage(Context context,
                                                    IncomingPushMessage message,
                                                    boolean asynchronous)
  {
    try {
      return getRecipientsFromString(context, message.getSource(), asynchronous);
    } catch (RecipientFormattingException e) {
      Log.w("RecipientFactory", e);
      return new Recipients(Recipient.getUnknownRecipient(context));
    }
  }

  private static Recipient getRecipientFromProviderId(Context context, String recipientId, boolean asynchronous) {
    try {
      return provider.getRecipient(context, Long.parseLong(recipientId), asynchronous);
    } catch (NumberFormatException e) {
      Log.w("RecipientFactory", e);
      return Recipient.getUnknownRecipient(context);
    }
  }

  private static boolean hasBracketedNumber(String recipient) {
    int openBracketIndex = recipient.indexOf('<');

    return (openBracketIndex != -1) &&
           (recipient.indexOf('>', openBracketIndex) != -1);
  }

  private static String parseBracketedNumber(String recipient) {
    int begin    = recipient.indexOf('<');
    int end      = recipient.indexOf('>', begin);
    String value = recipient.substring(begin + 1, end);

    return value;
  }

  private static Recipient parseRecipient(Context context, String recipient, boolean asynchronous) {
    recipient = recipient.trim();

    if( recipient.length() == 0 )
      return null;

    if (hasBracketedNumber(recipient))
      return getRecipientForNumber(context, parseBracketedNumber(recipient), asynchronous);

    return getRecipientForNumber(context, recipient, asynchronous);
  }

  public static void clearCache() {
    ContactPhotoFactory.clearCache();
    provider.clearCache();
  }

  public static void clearCache(Recipient recipient) {
    ContactPhotoFactory.clearCache(recipient);
    provider.clearCache(recipient);
  }

}
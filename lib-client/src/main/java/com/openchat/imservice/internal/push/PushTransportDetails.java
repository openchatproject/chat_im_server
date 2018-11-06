package com.openchat.imservice.internal.push;

import com.openchat.protocal.logging.Log;

public class PushTransportDetails {

  private static final String TAG = PushTransportDetails.class.getSimpleName();

  private final int messageVersion;

  public PushTransportDetails(int messageVersion) {
    this.messageVersion = messageVersion;
  }

  public byte[] getStrippedPaddingMessageBody(byte[] messageWithPadding) {
    if      (messageVersion < 2) throw new AssertionError("Unknown version: " + messageVersion);
    else if (messageVersion == 2) return messageWithPadding;

    int paddingStart = 0;

    for (int i=messageWithPadding.length-1;i>=0;i--) {
      if (messageWithPadding[i] == (byte)0x80) {
        paddingStart = i;
        break;
      } else if (messageWithPadding[i] != (byte)0x00) {
        Log.w(TAG, "Padding byte is malformed, returning unstripped padding.");
        return messageWithPadding;
      }
    }

    byte[] strippedMessage = new byte[paddingStart];
    System.arraycopy(messageWithPadding, 0, strippedMessage, 0, strippedMessage.length);

    return strippedMessage;
  }

  public byte[] getPaddedMessageBody(byte[] messageBody) {
    if       (messageVersion < 2) throw new AssertionError("Unknown version: " + messageVersion);
    else if (messageVersion == 2) return messageBody;

    byte[] paddedMessage = new byte[getPaddedMessageLength(messageBody.length + 1) - 1];
    System.arraycopy(messageBody, 0, paddedMessage, 0, messageBody.length);
    paddedMessage[messageBody.length] = (byte)0x80;

    return paddedMessage;
  }

  private int getPaddedMessageLength(int messageLength) {
    int messageLengthWithTerminator = messageLength + 1;
    int messagePartCount            = messageLengthWithTerminator / 160;

    if (messageLengthWithTerminator % 160 != 0) {
      messagePartCount++;
    }

    return messagePartCount * 160;
  }
}

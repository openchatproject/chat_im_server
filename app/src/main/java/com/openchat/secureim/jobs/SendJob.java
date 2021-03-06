package com.openchat.secureim.jobs;

import android.content.Context;
import android.util.Log;

import com.openchat.secureim.BuildConfig;
import com.openchat.secureim.OpenchatServiceExpiredException;
import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.database.DatabaseFactory;
import com.openchat.secureim.database.PartDatabase;
import com.openchat.secureim.mms.MediaConstraints;
import com.openchat.secureim.transport.UndeliverableMessageException;
import com.openchat.secureim.util.MediaUtil;
import com.openchat.secureim.util.Util;
import com.openchat.jobqueue.JobParameters;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.SendReq;

public abstract class SendJob extends MasterSecretJob {
  private final static String TAG = SendJob.class.getSimpleName();

  public SendJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  @Override
  public final void onRun(MasterSecret masterSecret) throws Exception {
    if (!Util.isBuildFresh()) {
      throw new OpenchatServiceExpiredException(String.format("OpenchatService expired (build %d, now %d)",
                                                         BuildConfig.BUILD_TIMESTAMP,
                                                         System.currentTimeMillis()));
    }

    onSend(masterSecret);
  }

  protected abstract void onSend(MasterSecret masterSecret) throws Exception;

  protected SendReq getResolvedMessage(MasterSecret masterSecret, SendReq message,
                                       MediaConstraints constraints, boolean toMemory)
      throws IOException, UndeliverableMessageException
  {
    PduBody body = new PduBody();
    try {
      for (int i = 0; i < message.getBody().getPartsNum(); i++) {
        body.addPart(getResolvedPart(masterSecret, constraints, message.getBody().getPart(i), toMemory));
      }
    } catch (MmsException me) {
      throw new UndeliverableMessageException(me);
    }
    return new SendReq(message.getPduHeaders(),
                       body,
                       message.getDatabaseMessageId(),
                       message.getDatabaseMessageBox(),
                       message.getSentTimestamp());
  }

  private PduPart getResolvedPart(MasterSecret masterSecret, MediaConstraints constraints,
                                  PduPart part, boolean toMemory)
      throws IOException, MmsException, UndeliverableMessageException
  {
    byte[] resizedData = null;

    if (!constraints.isSatisfied(context, masterSecret, part)) {
      if (!constraints.canResize(part)) {
        throw new UndeliverableMessageException("Size constraints could not be satisfied.");
      }
      resizedData = getResizedPartData(masterSecret, constraints, part);
    }

    if (toMemory && part.getDataUri() != null) {
      part.setData(resizedData != null ? resizedData : MediaUtil.getPartData(context, masterSecret, part));
    }

    if (resizedData != null) {
      part.setDataSize(resizedData.length);
    }
    return part;
  }

  protected void markPartsUploaded(long messageId, PduBody body) {
    if (body == null) return;
    PartDatabase database = DatabaseFactory.getPartDatabase(context);
    for (int i = 0; i < body.getPartsNum(); i++) {
      database.markPartUploaded(messageId, body.getPart(i));
    }
  }

  private byte[] getResizedPartData(MasterSecret masterSecret, MediaConstraints constraints,
                                    PduPart part)
      throws IOException, MmsException
  {
    Log.w(TAG, "resizing part " + part.getPartId());

    final long   oldSize = part.getDataSize();
    final byte[] data    = constraints.getResizedMedia(context, masterSecret, part);

    DatabaseFactory.getPartDatabase(context).updatePartData(masterSecret, part, new ByteArrayInputStream(data));
    Log.w(TAG, String.format("Resized part %.1fkb => %.1fkb", oldSize / 1024.0, part.getDataSize() / 1024.0));

    return data;
  }
}

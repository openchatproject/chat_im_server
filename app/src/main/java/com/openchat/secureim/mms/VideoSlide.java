package com.openchat.secureim.mms;

import java.io.IOException;

import com.openchat.secureim.R;
import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.util.ListenableFutureTask;
import com.openchat.secureim.util.ResUtil;

import ws.com.google.android.mms.pdu.PduPart;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;

public class VideoSlide extends Slide {

  public VideoSlide(Context context, Uri uri) throws IOException, MediaTooLargeException {
    super(context, constructPartFromUri(context, uri));
  }

  public VideoSlide(Context context, MasterSecret masterSecret, PduPart part) {
    super(context, masterSecret, part);
  }

  @Override
  public ListenableFutureTask<Pair<Drawable,Boolean>> getThumbnail(Context context) {
    return new ListenableFutureTask<>(new Pair<>(ResUtil.getDrawable(context, R.attr.conversation_icon_attach_video), true));
  }

  @Override
  public boolean hasImage() {
    return true;
  }

  @Override
  public boolean hasVideo() {
    return true;
  }

  private static PduPart constructPartFromUri(Context context, Uri uri)
      throws IOException, MediaTooLargeException
  {
    PduPart         part     = new PduPart();
    ContentResolver resolver = context.getContentResolver();
    Cursor          cursor   = null;

    try {
      cursor = resolver.query(uri, new String[] {MediaStore.Video.Media.MIME_TYPE}, null, null, null);
      if (cursor != null && cursor.moveToFirst()) {
        Log.w("VideoSlide", "Setting mime type: " + cursor.getString(0));
        part.setContentType(cursor.getString(0).getBytes());
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    assertMediaSize(context, uri);
    part.setDataUri(uri);
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Video" + System.currentTimeMillis()).getBytes());

    return part;
  }
}

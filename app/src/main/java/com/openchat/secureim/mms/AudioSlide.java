package com.openchat.secureim.mms;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;

import com.openchat.secureim.R;
import com.openchat.secureim.util.ResUtil;

import java.io.IOException;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduPart;

public class AudioSlide extends Slide {

  public AudioSlide(Context context, Uri uri, long dataSize) throws IOException {
    super(context, constructPartFromUri(context, uri, ContentType.AUDIO_UNSPECIFIED, dataSize));
  }

  public AudioSlide(Context context, PduPart part) {
    super(context, part);
  }

  @Override
  public boolean hasImage() {
    return true;
  }

  @Override
  public boolean hasAudio() {
    return true;
  }

  @NonNull @Override public String getContentDescription() {
    return context.getString(R.string.Slide_audio);
  }

  @Override
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return ResUtil.getDrawableRes(theme, R.attr.conversation_icon_attach_audio);
  }
}

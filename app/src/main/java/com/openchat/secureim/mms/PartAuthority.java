package com.openchat.secureim.mms;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;

import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.database.DatabaseFactory;
import com.openchat.secureim.database.PartDatabase;
import com.openchat.secureim.providers.PartProvider;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class PartAuthority {

  private static final String PART_URI_STRING  = "content://com.openchat.secureim/part";
  public  static final Uri    PART_CONTENT_URI = Uri.parse(PART_URI_STRING);

  private static final int PART_ROW  = 1;

  private static final UriMatcher uriMatcher;

  static {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI("com.openchat.secureim", "part/#", PART_ROW);
  }

  public static InputStream getPartStream(Context context, MasterSecret masterSecret, Uri uri)
      throws FileNotFoundException
  {
    PartDatabase partDatabase = DatabaseFactory.getPartDatabase(context);
    int          match        = uriMatcher.match(uri);

    switch (match) {
      case PART_ROW:  return partDatabase.getPartStream(masterSecret, ContentUris.parseId(uri));
      default:        return context.getContentResolver().openInputStream(uri);
    }
  }

  public static Uri getPublicPartUri(Uri uri) {
    return ContentUris.withAppendedId(PartProvider.CONTENT_URI, ContentUris.parseId(uri));
  }
}
package com.openchat.secureim.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.database.model.MessageRecord;
import com.openchat.protocal.util.guava.Optional;

import java.util.HashSet;
import java.util.Set;

public class MmsSmsDatabase extends Database {

  public static final String TRANSPORT     = "transport_type";
  public static final String MMS_TRANSPORT = "mms";
  public static final String SMS_TRANSPORT = "sms";

  public MmsSmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor getConversation(long threadId) {
    String[] projection    = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.TYPE,
                              MmsSmsColumns.THREAD_ID,
                              SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT,
                              MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX,
                              SmsDatabase.STATUS, MmsDatabase.PART_COUNT,
                              MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                              MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY,
                              MmsDatabase.STATUS, MmsSmsColumns.RECEIPT_COUNT,
                              MmsSmsColumns.MISMATCHED_IDENTITIES,
                              MmsDatabase.NETWORK_FAILURE, TRANSPORT};

    String order           = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";

    String selection       = MmsSmsColumns.THREAD_ID + " = " + threadId;

    Cursor cursor = queryTables(projection, selection, selection, order, null, null);
    setNotifyConverationListeners(cursor, threadId);

    return cursor;
  }

  public Cursor getIdentityConflictMessagesForThread(long threadId) {
    String[] projection    = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.TYPE,
                              MmsSmsColumns.THREAD_ID,
                              SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT,
                              MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX,
                              SmsDatabase.STATUS, MmsDatabase.PART_COUNT,
                              MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                              MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY,
                              MmsDatabase.STATUS, MmsSmsColumns.RECEIPT_COUNT,
                              MmsSmsColumns.MISMATCHED_IDENTITIES,
                              MmsDatabase.NETWORK_FAILURE, TRANSPORT};

    String order           = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";

    String selection       = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " + MmsSmsColumns.MISMATCHED_IDENTITIES + " IS NOT NULL";

    Cursor cursor = queryTables(projection, selection, selection, order, null, null);
    setNotifyConverationListeners(cursor, threadId);

    return cursor;
  }

  public Cursor getConversationSnippet(long threadId) {
    String[] projection    = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.TYPE,
                              MmsSmsColumns.THREAD_ID,
                              SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT,
                              MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX,
                              SmsDatabase.STATUS, MmsDatabase.PART_COUNT,
                              MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                              MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY,
                              MmsDatabase.STATUS, MmsSmsColumns.RECEIPT_COUNT,
                              MmsSmsColumns.MISMATCHED_IDENTITIES,
                              MmsDatabase.NETWORK_FAILURE, TRANSPORT};

    String order           = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
    String selection       = MmsSmsColumns.THREAD_ID + " = " + threadId;

    return  queryTables(projection, selection, selection, order, null, "1");
  }

  public Cursor getUnread() {
    String[] projection    = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.READ, SmsDatabase.TYPE,
                              SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT, MmsSmsColumns.THREAD_ID,
                              SmsDatabase.STATUS,
                              MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX,
                              MmsDatabase.PART_COUNT,
                              MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                              MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY,
                              MmsDatabase.STATUS, MmsSmsColumns.RECEIPT_COUNT,
                              MmsSmsColumns.MISMATCHED_IDENTITIES,
                              MmsDatabase.NETWORK_FAILURE, TRANSPORT};

    String order           = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";
    String selection       = MmsSmsColumns.READ + " = 0";

    return queryTables(projection, selection, selection, order, null, null);
  }

  public int getConversationCount(long threadId) {
    int count = DatabaseFactory.getSmsDatabase(context).getMessageCountForThread(threadId);
    count    += DatabaseFactory.getMmsDatabase(context).getMessageCountForThread(threadId);

    return count;
  }

  public void incrementDeliveryReceiptCount(String address, long timestamp) {
    DatabaseFactory.getSmsDatabase(context).incrementDeliveryReceiptCount(address, timestamp);
    DatabaseFactory.getMmsDatabase(context).incrementDeliveryReceiptCount(address, timestamp);
  }

  private Cursor queryTables(String[] projection, String smsSelection, String mmsSelection, String order, String groupBy, String limit) {
    String[] mmsProjection = {MmsDatabase.DATE_SENT + " AS " + MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsSmsColumns.ID, SmsDatabase.BODY, MmsSmsColumns.READ, MmsSmsColumns.THREAD_ID,
                              SmsDatabase.TYPE, SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT, MmsDatabase.MESSAGE_TYPE,
                              MmsDatabase.MESSAGE_BOX, SmsDatabase.STATUS, MmsDatabase.PART_COUNT,
                              MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                              MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY, MmsDatabase.STATUS,
                              MmsSmsColumns.RECEIPT_COUNT, MmsSmsColumns.MISMATCHED_IDENTITIES,
                              MmsDatabase.NETWORK_FAILURE, TRANSPORT};

    String[] smsProjection = {SmsDatabase.DATE_SENT + " AS " + MmsSmsColumns.NORMALIZED_DATE_SENT,
                              SmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsSmsColumns.ID, SmsDatabase.BODY, MmsSmsColumns.READ, MmsSmsColumns.THREAD_ID,
                              SmsDatabase.TYPE, SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT, MmsDatabase.MESSAGE_TYPE,
                              MmsDatabase.MESSAGE_BOX, SmsDatabase.STATUS, MmsDatabase.PART_COUNT,
                              MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                              MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY, MmsDatabase.STATUS,
                              MmsSmsColumns.RECEIPT_COUNT, MmsSmsColumns.MISMATCHED_IDENTITIES,
                              MmsDatabase.NETWORK_FAILURE, TRANSPORT};

    SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();
    SQLiteQueryBuilder smsQueryBuilder = new SQLiteQueryBuilder();

    mmsQueryBuilder.setDistinct(true);
    smsQueryBuilder.setDistinct(true);

    mmsQueryBuilder.setTables(MmsDatabase.TABLE_NAME);
    smsQueryBuilder.setTables(SmsDatabase.TABLE_NAME);

    Set<String> mmsColumnsPresent = new HashSet<String>();
    mmsColumnsPresent.add(MmsSmsColumns.ID);
    mmsColumnsPresent.add(MmsSmsColumns.READ);
    mmsColumnsPresent.add(MmsSmsColumns.THREAD_ID);
    mmsColumnsPresent.add(MmsSmsColumns.BODY);
    mmsColumnsPresent.add(MmsSmsColumns.ADDRESS);
    mmsColumnsPresent.add(MmsSmsColumns.ADDRESS_DEVICE_ID);
    mmsColumnsPresent.add(MmsSmsColumns.RECEIPT_COUNT);
    mmsColumnsPresent.add(MmsSmsColumns.MISMATCHED_IDENTITIES);
    mmsColumnsPresent.add(MmsDatabase.MESSAGE_TYPE);
    mmsColumnsPresent.add(MmsDatabase.MESSAGE_BOX);
    mmsColumnsPresent.add(MmsDatabase.DATE_SENT);
    mmsColumnsPresent.add(MmsDatabase.DATE_RECEIVED);
    mmsColumnsPresent.add(MmsDatabase.PART_COUNT);
    mmsColumnsPresent.add(MmsDatabase.CONTENT_LOCATION);
    mmsColumnsPresent.add(MmsDatabase.TRANSACTION_ID);
    mmsColumnsPresent.add(MmsDatabase.MESSAGE_SIZE);
    mmsColumnsPresent.add(MmsDatabase.EXPIRY);
    mmsColumnsPresent.add(MmsDatabase.STATUS);
    mmsColumnsPresent.add(MmsDatabase.NETWORK_FAILURE);

    Set<String> smsColumnsPresent = new HashSet<String>();
    smsColumnsPresent.add(MmsSmsColumns.ID);
    smsColumnsPresent.add(MmsSmsColumns.BODY);
    smsColumnsPresent.add(MmsSmsColumns.ADDRESS);
    smsColumnsPresent.add(MmsSmsColumns.ADDRESS_DEVICE_ID);
    smsColumnsPresent.add(MmsSmsColumns.READ);
    smsColumnsPresent.add(MmsSmsColumns.THREAD_ID);
    smsColumnsPresent.add(MmsSmsColumns.RECEIPT_COUNT);
    smsColumnsPresent.add(MmsSmsColumns.MISMATCHED_IDENTITIES);
    smsColumnsPresent.add(SmsDatabase.TYPE);
    smsColumnsPresent.add(SmsDatabase.SUBJECT);
    smsColumnsPresent.add(SmsDatabase.DATE_SENT);
    smsColumnsPresent.add(SmsDatabase.DATE_RECEIVED);
    smsColumnsPresent.add(SmsDatabase.STATUS);

    String mmsSubQuery = mmsQueryBuilder.buildUnionSubQuery(TRANSPORT, mmsProjection, mmsColumnsPresent, 2, MMS_TRANSPORT, mmsSelection, null, null, null);
    String smsSubQuery = smsQueryBuilder.buildUnionSubQuery(TRANSPORT, smsProjection, smsColumnsPresent, 2, SMS_TRANSPORT, smsSelection, null, null, null);

    SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();
    String unionQuery = unionQueryBuilder.buildUnionQuery(new String[] {smsSubQuery, mmsSubQuery}, order, null);

    SQLiteQueryBuilder outerQueryBuilder = new SQLiteQueryBuilder();
    outerQueryBuilder.setTables("(" + unionQuery + ")");

    String query      = outerQueryBuilder.buildQuery(projection, null, null, groupBy, null, null, limit);

    Log.w("MmsSmsDatabase", "Executing query: " + query);
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    return db.rawQuery(query, null);
  }

  public Reader readerFor(@NonNull Cursor cursor, @Nullable MasterSecret masterSecret) {
    return new Reader(cursor, masterSecret);
  }

  public Reader readerFor(@NonNull Cursor cursor) {
    return new Reader(cursor);
  }

  public class Reader {

    private final Cursor                       cursor;
    private final Optional<MasterSecret>       masterSecret;
    private       EncryptingSmsDatabase.Reader smsReader;
    private       MmsDatabase.Reader           mmsReader;

    public Reader(Cursor cursor, @Nullable MasterSecret masterSecret) {
      this.cursor       = cursor;
      this.masterSecret = Optional.fromNullable(masterSecret);
    }

    public Reader(Cursor cursor) {
      this(cursor, null);
    }

    private EncryptingSmsDatabase.Reader getSmsReader() {
      if (smsReader == null) {
        if (masterSecret.isPresent()) smsReader = DatabaseFactory.getEncryptingSmsDatabase(context).readerFor(masterSecret.get(), cursor);
        else                          smsReader = DatabaseFactory.getSmsDatabase(context).readerFor(cursor);
      }

      return smsReader;
    }

    private MmsDatabase.Reader getMmsReader() {
      if (mmsReader == null) {
        mmsReader = DatabaseFactory.getMmsDatabase(context).readerFor(masterSecret.orNull(), cursor);
      }

      return mmsReader;
    }

    public MessageRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public MessageRecord getCurrent() {
      String type = cursor.getString(cursor.getColumnIndexOrThrow(TRANSPORT));

      if (MmsSmsDatabase.MMS_TRANSPORT.equals(type)) {
        return getMmsReader().getCurrent();
      } else {
        return getSmsReader().getCurrent();
      }
    }

    public void close() {
      cursor.close();
    }
  }
}

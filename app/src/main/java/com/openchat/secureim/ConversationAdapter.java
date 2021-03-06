package com.openchat.secureim;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;

import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.database.CursorRecyclerViewAdapter;
import com.openchat.secureim.database.DatabaseFactory;
import com.openchat.secureim.database.MmsSmsColumns;
import com.openchat.secureim.database.MmsSmsDatabase;
import com.openchat.secureim.database.SmsDatabase;
import com.openchat.secureim.database.model.MessageRecord;
import com.openchat.secureim.util.LRUCache;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.openchat.secureim.util.ViewUtil;

public class ConversationAdapter <V extends View & BindableConversationItem>
    extends CursorRecyclerViewAdapter<ConversationAdapter.ViewHolder>
{

  private static final int MAX_CACHE_SIZE = 40;
  private final Map<String,SoftReference<MessageRecord>> messageRecordCache =
      Collections.synchronizedMap(new LRUCache<String, SoftReference<MessageRecord>>(MAX_CACHE_SIZE));

  public static final int MESSAGE_TYPE_OUTGOING = 0;
  public static final int MESSAGE_TYPE_INCOMING = 1;
  public static final int MESSAGE_TYPE_UPDATE   = 2;

  private final Set<MessageRecord> batchSelected = Collections.synchronizedSet(new HashSet<MessageRecord>());

  private final ItemClickListener      clickListener;
  private final MasterSecret           masterSecret;
  private final Locale                 locale;
  private final boolean                groupThread;
  private final boolean                pushDestination;
  private final MmsSmsDatabase         db;
  private final LayoutInflater         inflater;

  protected static class ViewHolder extends RecyclerView.ViewHolder {
    public <V extends View & BindableConversationItem> ViewHolder(final @NonNull V itemView) {
      super(itemView);
    }

    @SuppressWarnings("unchecked")
    public <V extends View & BindableConversationItem> V getView() {
      return (V)itemView;
    }
  }

  public interface ItemClickListener {
    void onItemClick(ConversationItem item);
    void onItemLongClick(ConversationItem item);
  }

  public ConversationAdapter(@NonNull Context context,
                             @NonNull MasterSecret masterSecret,
                             @NonNull Locale locale,
                             @Nullable ItemClickListener clickListener,
                             @Nullable Cursor cursor,
                             boolean groupThread,
                             boolean pushDestination)
  {
    super(context, cursor);
    this.masterSecret    = masterSecret;
    this.locale          = locale;
    this.clickListener   = clickListener;
    this.groupThread     = groupThread;
    this.pushDestination = pushDestination;
    this.inflater        = LayoutInflater.from(context);
    this.db              = DatabaseFactory.getMmsSmsDatabase(context);
  }

  @Override
  public void changeCursor(Cursor cursor) {
    messageRecordCache.clear();
    super.changeCursor(cursor);
  }

  @Override public void onBindViewHolder(ViewHolder viewHolder, @NonNull Cursor cursor) {
    long          id            = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
    String        type          = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));
    MessageRecord messageRecord = getMessageRecord(id, cursor, type);

    viewHolder.getView().bind(masterSecret, messageRecord, locale, batchSelected, groupThread, pushDestination);
  }

  @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    final V itemView = ViewUtil.inflate(inflater, parent, getLayoutForViewType(viewType));
    if (viewType == MESSAGE_TYPE_INCOMING || viewType == MESSAGE_TYPE_OUTGOING) {
      itemView.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          if (clickListener != null) clickListener.onItemClick((ConversationItem)itemView);
        }
      });
      itemView.setOnLongClickListener(new OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
          if (clickListener != null) clickListener.onItemLongClick((ConversationItem)itemView);
          return true;
        }
      });
    }

    return new ViewHolder(itemView);
  }

  @Override public void onViewRecycled(ViewHolder holder) {
    holder.getView().unbind();
  }

  private @LayoutRes int getLayoutForViewType(int viewType) {
    switch (viewType) {
    case ConversationAdapter.MESSAGE_TYPE_OUTGOING: return R.layout.conversation_item_sent;
    case ConversationAdapter.MESSAGE_TYPE_INCOMING: return R.layout.conversation_item_received;
    case ConversationAdapter.MESSAGE_TYPE_UPDATE:   return R.layout.conversation_item_update;
    default: throw new IllegalArgumentException("unsupported item view type given to ConversationAdapter");
    }
  }

  @Override
  public int getItemViewType(@NonNull Cursor cursor) {
    long id                     = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.ID));
    String type                 = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));
    MessageRecord messageRecord = getMessageRecord(id, cursor, type);

    if      (messageRecord.isGroupAction()) return MESSAGE_TYPE_UPDATE;
    else if (messageRecord.isOutgoing())    return MESSAGE_TYPE_OUTGOING;
    else                                    return MESSAGE_TYPE_INCOMING;
  }

  private MessageRecord getMessageRecord(long messageId, Cursor cursor, String type) {
    final SoftReference<MessageRecord> reference = messageRecordCache.get(type + messageId);
    if (reference != null) {
      final MessageRecord record = reference.get();
      if (record != null) return record;
    }

    final MessageRecord messageRecord = db.readerFor(cursor, masterSecret).getCurrent();
    messageRecordCache.put(type + messageId, new SoftReference<>(messageRecord));

    return messageRecord;
  }

  public void close() {
    getCursor().close();
  }

  public void toggleSelection(MessageRecord messageRecord) {
    if (!batchSelected.remove(messageRecord)) {
      batchSelected.add(messageRecord);
    }
  }

  public void clearSelection() {
    batchSelected.clear();
  }

  public Set<MessageRecord> getSelectedItems() {
    return Collections.unmodifiableSet(new HashSet<>(batchSelected));
  }
}

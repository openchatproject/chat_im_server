package com.openchat.secureim.contacts;

import com.openchat.secureim.R;
import com.openchat.secureim.recipients.RecipientsFormatter;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.text.Annotation;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class RecipientsAdapter extends ResourceCursorAdapter {

    public static final int CONTACT_ID_INDEX = 1;
    public static final int TYPE_INDEX       = 2;
    public static final int NUMBER_INDEX     = 3;
    public static final int LABEL_INDEX      = 4;
    public static final int NAME_INDEX       = 5;

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private ContactAccessor mContactAccessor;

    public RecipientsAdapter(Context context) {
        super(context, R.layout.recipient_filter_item, null);
        mContext = context;
        mContentResolver = context.getContentResolver();
        mContactAccessor = ContactAccessor.getInstance();
    }

    @Override
    public final CharSequence convertToString(Cursor cursor) {
        String name = cursor.getString(RecipientsAdapter.NAME_INDEX);
        int type = cursor.getInt(RecipientsAdapter.TYPE_INDEX);
        String number = cursor.getString(RecipientsAdapter.NUMBER_INDEX).trim();

        String label = cursor.getString(RecipientsAdapter.LABEL_INDEX);
        CharSequence displayLabel = mContactAccessor.phoneTypeToString(mContext, type, label);

        if (number.length() == 0) {
            return number;
        }

        if (name == null) {
            name = "";
        } else {
            name = name.replace(", ", " ")
                       .replace(",", " ");  // Make sure we leave a space between parts of names.
        }

        String nameAndNumber = RecipientsFormatter.formatNameAndNumber(name, number);

        SpannableString out = new SpannableString(nameAndNumber);
        int len = out.length();

        if (!TextUtils.isEmpty(name)) {
            out.setSpan(new Annotation("name", name), 0, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            out.setSpan(new Annotation("name", number), 0, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        String person_id = cursor.getString(RecipientsAdapter.CONTACT_ID_INDEX);
        out.setSpan(new Annotation("person_id", person_id), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new Annotation("label", displayLabel.toString()), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);              
        out.setSpan(new Annotation("number", number), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return out;
    }

    @Override
    public final void bindView(View view, Context context, Cursor cursor) {
        TextView name = (TextView) view.findViewById(R.id.name);
        name.setText(cursor.getString(NAME_INDEX));

        TextView label = (TextView) view.findViewById(R.id.label);
        int type = cursor.getInt(TYPE_INDEX);
        label.setText(mContactAccessor.phoneTypeToString(mContext, type, cursor.getString(LABEL_INDEX)));

        TextView number = (TextView) view.findViewById(R.id.number);
        number.setText("(" + cursor.getString(NUMBER_INDEX) + ")");
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
       return mContactAccessor.getCursorForRecipientFilter( constraint, mContentResolver );
    }

    
    public static  boolean usefulAsDigits(CharSequence cons) {
        int len = cons.length();

        for (int i = 0; i < len; i++) {
            char c = cons.charAt(i);

            if ((c >= '0') && (c <= '9')) {
                continue;
            }
            if ((c == ' ') || (c == '-') || (c == '(') || (c == ')') || (c == '.') || (c == '+')
                    || (c == '#') || (c == '*')) {
                continue;
            }
            if ((c >= 'A') && (c <= 'Z')) {
                continue;
            }
            if ((c >= 'a') && (c <= 'z')) {
                continue;
            }

            return false;
        }

        return true;
    }
}

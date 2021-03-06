package com.openchat.secureim;

import android.content.Context;
import android.content.res.TypedArray;

import com.openchat.secureim.util.MmsCharacterCalculator;
import com.openchat.secureim.util.PushCharacterCalculator;
import com.openchat.secureim.util.SmsCharacterCalculator;
import com.openchat.protocal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

import static com.openchat.secureim.TransportOption.Type;

public class TransportOptions {

  private static final String TAG = TransportOptions.class.getSimpleName();

  private final List<OnTransportChangedListener> listeners = new LinkedList<>();
  private final Context                          context;
  private final List<TransportOption>            enabledTransports;

  private Type    selectedType;
  private boolean manuallySelected;

  public TransportOptions(Context context, boolean media) {
    this.context           = context;
    this.enabledTransports = initializeAvailableTransports(media);

    setDefaultTransport(Type.SMS);
  }

  public void reset(boolean media) {
    List<TransportOption> transportOptions = initializeAvailableTransports(media);
    this.enabledTransports.clear();
    this.enabledTransports.addAll(transportOptions);

    if (!find(selectedType).isPresent()) {
      this.manuallySelected = false;
      setTransport(Type.SMS);
    } else {
      notifyTransportChangeListeners();
    }
  }

  public void setDefaultTransport(Type type) {
    if (!this.manuallySelected) {
      setTransport(type);
    }
  }

  public void setSelectedTransport(Type type) {
    this.manuallySelected= true;
    setTransport(type);
  }

  public boolean isManualSelection() {
    return manuallySelected;
  }

  public TransportOption getSelectedTransport() {
    Optional<TransportOption> option =  find(selectedType);

    if (option.isPresent()) return option.get();
    else                    throw new AssertionError("Selected type isn't present!");
  }

  public void disableTransport(Type type) {
    Optional<TransportOption> option = find(type);
    if (option.isPresent()) {
      enabledTransports.remove(option.get());
    }
  }

  public List<TransportOption> getEnabledTransports() {
    return enabledTransports;
  }

  public void addOnTransportChangedListener(OnTransportChangedListener listener) {
    this.listeners.add(listener);
  }

  private List<TransportOption> initializeAvailableTransports(boolean isMediaMessage) {
    List<TransportOption> results = new LinkedList<>();

    if (isMediaMessage) {
      results.add(new TransportOption(Type.SMS, R.drawable.ic_send_sms_white_24dp,
                                      context.getResources().getColor(R.color.grey_600),
                                      context.getString(R.string.ConversationActivity_transport_insecure_mms),
                                      context.getString(R.string.conversation_activity__type_message_mms_insecure),
                                      new MmsCharacterCalculator()));
    } else {
      results.add(new TransportOption(Type.SMS, R.drawable.ic_send_sms_white_24dp,
                                      context.getResources().getColor(R.color.grey_600),
                                      context.getString(R.string.ConversationActivity_transport_insecure_sms),
                                      context.getString(R.string.conversation_activity__type_message_sms_insecure),
                                      new SmsCharacterCalculator()));
    }

    results.add(new TransportOption(Type.OPENCHAT, R.drawable.ic_send_push_white_24dp,
                                    context.getResources().getColor(R.color.openchatservice_primary),
                                    context.getString(R.string.ConversationActivity_transport_openchatservice),
                                    context.getString(R.string.conversation_activity__type_message_push),
                                    new PushCharacterCalculator()));

    return results;
  }

  private void setTransport(Type type) {
    this.selectedType = type;

    notifyTransportChangeListeners();
  }

  private void notifyTransportChangeListeners() {
    for (OnTransportChangedListener listener : listeners) {
      listener.onChange(getSelectedTransport());
    }
  }

  private Optional<TransportOption> find(Type type) {
    for (TransportOption option : enabledTransports) {
      if (option.isType(type)) {
        return Optional.of(option);
      }
    }

    return Optional.absent();
  }

  public interface OnTransportChangedListener {
    public void onChange(TransportOption newTransport);
  }
}

package com.openchat.secureim;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.MaterialDialog.Builder;
import com.afollestad.materialdialogs.MaterialDialog.ButtonCallback;

import com.openchat.secureim.crypto.IdentityKeyUtil;
import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.push.OpenchatServiceCommunicationFactory;
import com.openchat.secureim.util.Base64;
import com.openchat.secureim.util.ProgressDialogAsyncTask;
import com.openchat.protocal.IdentityKeyPair;
import com.openchat.protocal.InvalidKeyException;
import com.openchat.protocal.ecc.Curve;
import com.openchat.protocal.ecc.ECPublicKey;
import com.openchat.imservice.api.OpenchatServiceAccountManager;
import com.openchat.imservice.api.push.exceptions.NotFoundException;
import com.openchat.imservice.internal.push.DeviceLimitExceededException;

import java.io.IOException;

import static com.openchat.secureim.util.SpanUtil.small;

public class DeviceProvisioningActivity extends PassphraseRequiredActionBarActivity {

  private static final String TAG = DeviceProvisioningActivity.class.getSimpleName();

  private Uri          uri;
  private MasterSecret masterSecret;

  @Override
  protected void onPreCreate() {
    supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
  }

  @Override
  protected void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    getSupportActionBar().hide();
    initializeResources();

    SpannableStringBuilder content = new SpannableStringBuilder();
    content.append(getString(R.string.DeviceProvisioningActivity_content_intro))
           .append("\n")
           .append(small(getString(R.string.DeviceProvisioningActivity_content_bullets)));

    new Builder(this).title(getString(R.string.DeviceProvisioningActivity_link_this_device))
                     .iconRes(R.drawable.icon_dialog)
                     .content(content)
                     .positiveText(R.string.DeviceProvisioningActivity_continue)
                     .negativeText(R.string.DeviceProvisioningActivity_cancel)
                     .positiveColorRes(R.color.openchatservice_primary)
                     .negativeColorRes(R.color.gray50)
                     .autoDismiss(false)
                     .callback(new ButtonCallback() {
                       @Override
                       public void onPositive(MaterialDialog dialog) {
                         handleProvisioning(dialog);
                       }

                       @Override
                       public void onNegative(MaterialDialog dialog) {
                         dialog.dismiss();
                         finish();
                       }
                     })
                     .dismissListener(new OnDismissListener() {
                       @Override
                       public void onDismiss(DialogInterface dialog) {
                         finish();
                       }
                     })
                     .show();
  }

  private void initializeResources() {
    this.uri = getIntent().getData();
  }

  private void handleProvisioning(final MaterialDialog dialog) {
    new ProgressDialogAsyncTask<Void, Void, Integer>(this,
                                                     R.string.DeviceProvisioningActivity_content_progress_title,
                                                     R.string.DeviceProvisioningActivity_content_progress_content)
    {
      private static final int SUCCESS        = 0;
      private static final int NO_DEVICE      = 1;
      private static final int NETWORK_ERROR  = 2;
      private static final int KEY_ERROR      = 3;
      private static final int LIMIT_EXCEEDED = 4;

      @Override
      protected Integer doInBackground(Void... params) {
        try {
          Context                  context          = DeviceProvisioningActivity.this;
          OpenchatServiceAccountManager accountManager   = OpenchatServiceCommunicationFactory.createManager(context);
          String                   verificationCode = accountManager.getNewDeviceVerificationCode();
          String                   ephemeralId      = uri.getQueryParameter("uuid");
          String                   publicKeyEncoded = uri.getQueryParameter("pub_key");
          ECPublicKey              publicKey        = Curve.decodePoint(Base64.decode(publicKeyEncoded), 0);
          IdentityKeyPair          identityKeyPair  = IdentityKeyUtil.getIdentityKeyPair(context);

          accountManager.addDevice(ephemeralId, publicKey, identityKeyPair, verificationCode);
          return SUCCESS;

        } catch (NotFoundException e) {
          Log.w(TAG, e);
          return NO_DEVICE;
        } catch (DeviceLimitExceededException e) {
          Log.w(TAG, e);
          return LIMIT_EXCEEDED;
        } catch (IOException e) {
          Log.w(TAG, e);
          return NETWORK_ERROR;
        } catch (InvalidKeyException e) {
          Log.w(TAG, e);
          return KEY_ERROR;
        }
      }

      @Override
      protected void onPostExecute(Integer result) {
        super.onPostExecute(result);

        Context context = DeviceProvisioningActivity.this;

        switch (result) {
          case SUCCESS:
            Toast.makeText(context, R.string.DeviceProvisioningActivity_content_progress_success, Toast.LENGTH_SHORT).show();
            break;
          case NO_DEVICE:
            Toast.makeText(context, R.string.DeviceProvisioningActivity_content_progress_no_device, Toast.LENGTH_LONG).show();
            break;
          case NETWORK_ERROR:
            Toast.makeText(context, R.string.DeviceProvisioningActivity_content_progress_network_error, Toast.LENGTH_LONG).show();
            break;
          case KEY_ERROR:
            Toast.makeText(context, R.string.DeviceProvisioningActivity_content_progress_key_error, Toast.LENGTH_LONG).show();
            break;
          case LIMIT_EXCEEDED:
            Toast.makeText(context, R.string.DeviceProvisioningActivity_sorry_you_have_too_many_devices_linked_already, Toast.LENGTH_LONG).show();
            break;
        }
        dialog.dismiss();
      }
    }.execute();
  }
}

package com.openchat.secureim;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.util.Base64;
import com.openchat.secureim.util.Dialogs;
import com.openchat.secureim.util.DynamicLanguage;
import com.openchat.secureim.util.DynamicTheme;
import com.openchat.protocal.IdentityKey;

public abstract class KeyScanningActivity extends PassphraseRequiredActionBarActivity {

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.key_scanning, menu);

    menu.findItem(R.id.menu_scan).setTitle(getScanString());
    menu.findItem(R.id.menu_get_scanned).setTitle(getDisplayString());

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case R.id.menu_scan:        initiateScan();    return true;
    case R.id.menu_get_scanned: initiateDisplay(); return true;
    case android.R.id.home:     finish();          return true;
    }

    return false;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);

    if ((scanResult != null) && (scanResult.getContents() != null)) {
      String data = scanResult.getContents();

      if (data.equals(Base64.encodeBytes(getIdentityKeyToCompare().serialize()))) {
        Dialogs.showInfoDialog(this, getVerifiedTitle(), getVerifiedMessage());
      } else {
        Dialogs.showAlertDialog(this, getNotVerifiedTitle(), getNotVerifiedMessage());
      }
    } else {
      Toast.makeText(this, R.string.KeyScanningActivity_no_scanned_key_found_exclamation,
                     Toast.LENGTH_LONG).show();
    }
  }

  private IntentIntegrator getIntentIntegrator() {
    IntentIntegrator intentIntegrator = new IntentIntegrator(this);
    intentIntegrator.setButtonYesByID(R.string.yes);
    intentIntegrator.setButtonNoByID(R.string.no);
    intentIntegrator.setTitleByID(R.string.KeyScanningActivity_install_barcode_Scanner);
    intentIntegrator.setMessageByID(R.string.KeyScanningActivity_this_application_requires_barcode_scanner_would_you_like_to_install_it);
    return intentIntegrator;
  }

  protected void initiateScan() {
    IntentIntegrator intentIntegrator = getIntentIntegrator();
    intentIntegrator.initiateScan();
  }

  protected void initiateDisplay() {
    IntentIntegrator intentIntegrator = getIntentIntegrator();
    intentIntegrator.shareText(Base64.encodeBytes(getIdentityKeyToDisplay().serialize()));
  }

  protected abstract String getScanString();
  protected abstract String getDisplayString();

  protected abstract String getNotVerifiedTitle();
  protected abstract String getNotVerifiedMessage();

  protected abstract IdentityKey getIdentityKeyToCompare();
  protected abstract IdentityKey getIdentityKeyToDisplay();

  protected abstract String getVerifiedTitle();
  protected abstract String getVerifiedMessage();

}

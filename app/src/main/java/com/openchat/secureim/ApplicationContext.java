package com.openchat.secureim;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.support.multidex.MultiDexApplication;
import android.util.Log;

import com.google.android.gms.security.ProviderInstaller;

import com.openchat.secureim.crypto.PRNGFixes;
import com.openchat.secureim.dependencies.AxolotlStorageModule;
import com.openchat.secureim.dependencies.InjectableType;
import com.openchat.secureim.dependencies.openchatCommunicationModule;
import com.openchat.secureim.jobs.CreateSignedPreKeyJob;
import com.openchat.secureim.jobs.GcmRefreshJob;
import com.openchat.secureim.jobs.persistence.EncryptingJobSerializer;
import com.openchat.secureim.jobs.requirements.MasterSecretRequirementProvider;
import com.openchat.secureim.jobs.requirements.ServiceRequirementProvider;
import com.openchat.secureim.push.openchatServiceNetworkAccess;
import com.openchat.secureim.service.DirectoryRefreshListener;
import com.openchat.secureim.service.ExpiringMessageManager;
import com.openchat.secureim.service.RotateSignedPreKeyListener;
import com.openchat.secureim.service.UpdateApkRefreshListener;
import com.openchat.secureim.util.TextSecurePreferences;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import com.openchat.jobqueue.JobManager;
import com.openchat.jobqueue.dependencies.DependencyInjector;
import com.openchat.jobqueue.requirements.NetworkRequirementProvider;
import com.openchat.libim.logging.openchatProtocolLoggerProvider;
import com.openchat.libim.util.AndroidopenchatProtocolLogger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dagger.ObjectGraph;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 */
public class ApplicationContext extends MultiDexApplication implements DependencyInjector {

  private static final String TAG = ApplicationContext.class.getName();

  private ExpiringMessageManager expiringMessageManager;
  private JobManager             jobManager;
  private ObjectGraph            objectGraph;

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    initializeRandomNumberFix();
    initializeLogging();
    initializeDependencyInjection();
    initializeJobManager();
    initializeExpiringMessageManager();
    initializeGcmCheck();
    initializeSignedPreKeyCheck();
    initializePeriodicTasks();
    initializeCircumvention();
    initializeWebRtc();
  }

  @Override
  public void injectDependencies(Object object) {
    if (object instanceof InjectableType) {
      objectGraph.inject(object);
    }
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  public ExpiringMessageManager getExpiringMessageManager() {
    return expiringMessageManager;
  }

  private void initializeRandomNumberFix() {
    PRNGFixes.apply();
  }

  private void initializeLogging() {
    openchatProtocolLoggerProvider.setProvider(new AndroidopenchatProtocolLogger());
  }

  private void initializeJobManager() {
    this.jobManager = JobManager.newBuilder(this)
                                .withName("TextSecureJobs")
                                .withDependencyInjector(this)
                                .withJobSerializer(new EncryptingJobSerializer())
                                .withRequirementProviders(new MasterSecretRequirementProvider(this),
                                                          new ServiceRequirementProvider(this),
                                                          new NetworkRequirementProvider(this))
                                .withConsumerThreads(5)
                                .build();
  }

  private void initializeDependencyInjection() {
    this.objectGraph = ObjectGraph.create(new openchatCommunicationModule(this, new openchatServiceNetworkAccess(this)),
                                          new AxolotlStorageModule(this));
  }

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this)) {
      long nextSetTime = TextSecurePreferences.getGcmRegistrationIdLastSetTime(this) + TimeUnit.HOURS.toMillis(6);

      if (TextSecurePreferences.getGcmRegistrationId(this) == null || nextSetTime <= System.currentTimeMillis()) {
        this.jobManager.add(new GcmRefreshJob(this));
      }
    }
  }

  private void initializeSignedPreKeyCheck() {
    if (!TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      jobManager.add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);

    if (BuildConfig.PLAY_STORE_DISABLED) {
      UpdateApkRefreshListener.schedule(this);
    }
  }

  private void initializeWebRtc() {
    try {
      Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
        add("Moto G5");
      }};

      Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
      }};

      if (Build.VERSION.SDK_INT >= 11) {
        if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
          WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
        }

        if (!OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
          WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
        }

        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true);
      }
    } catch (UnsatisfiedLinkError e) {
      Log.w(TAG, e);
    }
  }

  private void initializeCircumvention() {
    AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        if (new openchatServiceNetworkAccess(ApplicationContext.this).isCensored(ApplicationContext.this)) {
          try {
            ProviderInstaller.installIfNeeded(ApplicationContext.this);
          } catch (Throwable t) {
            Log.w(TAG, t);
          }
        }
        return null;
      }
    };

    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

}

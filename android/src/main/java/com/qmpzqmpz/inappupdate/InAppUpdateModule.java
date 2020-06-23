package com.qmpzqmpz.inappupdate;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallState;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.ActivityResult;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.Task;


public class InAppUpdateModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
    private static ReactApplicationContext reactContext;

    private static final int UPDATE_REQUEST = 4343;

    private static final String TAG = "InAppUpdate";
    private static final String E_FAILED_TO_UPDATE = "E_FAILED_TO_UPDATE";
    private static final String E_UPDATE_CANCELLED = "E_UPDATE_CANCELLED";

    private AppUpdateManager mAppUpdateManager;
    private InstallStateUpdatedListener mListener;
    private boolean isUpdating = false;

    private Promise mPromise;
    private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {

        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
            if (requestCode == UPDATE_REQUEST) {
                if (mPromise != null) {
                    if (resultCode == Activity.RESULT_CANCELED) {
                        mPromise.reject(E_UPDATE_CANCELLED, "app download canceled");
                    }
                    else if (resultCode == ActivityResult.RESULT_IN_APP_UPDATE_FAILED) {
                        mPromise.reject(E_FAILED_TO_UPDATE, "app download failed");
                    } else {
                        mPromise.resolve("completed");
                    }
                    mPromise = null;
                }
            }
        }
    };

    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    InAppUpdateModule(ReactApplicationContext context) {
        super(context);
        reactContext = context;

        reactContext.addActivityEventListener(mActivityEventListener);
        reactContext.addLifecycleEventListener(this);
    }

    @Override
    public String getName() {
        return "InAppUpdate";
    }

    @ReactMethod
    void check(final Promise promise) {
        Activity currentActivity = getCurrentActivity();
        mPromise = promise;

        assert currentActivity != null;
        mAppUpdateManager = AppUpdateManagerFactory.create(currentActivity);

        Task<AppUpdateInfo> appUpdateInfoTask = mAppUpdateManager.getAppUpdateInfo();

//        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
//            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
//                mPromise.resolve("downloaded");
//            }
//
//            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
//                    // For a flexible update, use AppUpdateType.FLEXIBLE
//                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
//                mPromise.resolve("available");
//            } else {
//                mPromise.resolve("latest");
//            }
//        });

        appUpdateInfoTask.addOnFailureListener(e -> {
           System.out.println(e.toString());
           mPromise.resolve("error");
        });

        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            try {
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        // For a flexible update, use AppUpdateType.FLEXIBLE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    mPromise.resolve("available");
                } else {
                    mPromise.resolve("latest");
                }
            } catch (Exception e) {
                e.printStackTrace();
                mPromise.resolve("error");
            }
        });
    }

    @ReactMethod
    void download() {
        Activity currentActivity = getCurrentActivity();

//        mListener = state -> {
//            WritableMap params = Arguments.createMap();
//
//            if (state.installStatus() == InstallStatus.DOWNLOADED){
//                params.putString("state", "downloaded");
//                sendEvent(reactContext, "InAppUpdateDownload", params);
//            } else if (state.installStatus() == InstallStatus.INSTALLED){
//                if (mAppUpdateManager != null){
//                    mAppUpdateManager.unregisterListener(mListener);
//                }
//            } else if (state.installStatus() == InstallStatus.CANCELED){
//                params.putString("state", "canceled");
//                sendEvent(reactContext, "InAppUpdateDownload", params);
//            } else if (state.installStatus() == InstallStatus.FAILED){
//                params.putString("state", "failed");
//                sendEvent(reactContext, "InAppUpdateDownload", params);
//            } else if (state.installStatus() == InstallStatus.INSTALLED){
//                params.putString("state", "installed");
//                sendEvent(reactContext, "InAppUpdateDownload", params);
//            }
//        };
//
//        mAppUpdateManager.registerListener(mListener);

        isUpdating = true;
        Task<AppUpdateInfo> appUpdateInfoTask = mAppUpdateManager.getAppUpdateInfo();
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            try {
                mAppUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo, AppUpdateType.IMMEDIATE, currentActivity, UPDATE_REQUEST);
            } catch (
                    IntentSender.SendIntentException e) {
                e.printStackTrace();
                mPromise.reject(E_FAILED_TO_UPDATE, e.toString());
            }
        });
    }

    @ReactMethod
    void install(final Promise promise) {
        mPromise = promise;

        if (mAppUpdateManager != null) {
            mAppUpdateManager.completeUpdate();
        }
    }

    @Override
    public void onHostResume() {
        if (isUpdating) {
            Activity currentActivity = getCurrentActivity();

            mAppUpdateManager
                    .getAppUpdateInfo()
                    .addOnSuccessListener(
                            appUpdateInfo -> {
                                if (appUpdateInfo.updateAvailability()
                                        == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                                    // If an in-app update is already running, resume the update.
                                    try {
                                        mAppUpdateManager.startUpdateFlowForResult(
                                                appUpdateInfo,
                                                AppUpdateType.IMMEDIATE,
                                                currentActivity,
                                                UPDATE_REQUEST);
                                    } catch (IntentSender.SendIntentException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    isUpdating = false;
                                }
                            });
        }
    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {

    }
}
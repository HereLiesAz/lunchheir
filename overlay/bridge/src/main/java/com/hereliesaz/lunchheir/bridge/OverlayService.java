package com.hereliesaz.lunchheir.bridge;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.view.WindowManager;

import com.google.android.libraries.launcherclient.ILauncherOverlay;
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback;

/**
 * Bidirectional proxy for the Google Discover ("-1") overlay.
 *
 * The launcher binds to THIS service (the bridge is debuggable, which is what lets the Google
 * App talk to it). The bridge in turn binds to the Google App's own overlay service and forwards
 * every {@link ILauncherOverlay} call to it. Callbacks flow back by forwarding the launcher's
 * {@link ILauncherOverlayCallback} binder straight to Google, so the feed's scroll/status events
 * reach the launcher directly.
 *
 * Increment 2: the forwarding proxy. The connection to Google is asynchronous, so the most
 * important launcher call — {@code windowAttached} — is cached and replayed once Google connects.
 * Exact reconnect/version-handshake tuning needs on-device verification.
 */
public class OverlayService extends Service {

    private static final String TAG = "LunchHeirBridge";
    private static final String OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY";
    private static final String GOOGLE_APP = "com.google.android.googlequicksearchbox";

    private ILauncherOverlay mGoogleOverlay;
    private boolean mGoogleBound;

    // Cached windowAttached so we can replay it once the Google overlay connects.
    private WindowManager.LayoutParams mPendingLp;
    private ILauncherOverlayCallback mPendingCb;
    private int mPendingFlags;
    private boolean mHasPendingAttach;

    private final ServiceConnection mGoogleConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mGoogleOverlay = ILauncherOverlay.Stub.asInterface(service);
            Log.d(TAG, "connected to Google overlay");
            if (mHasPendingAttach && mGoogleOverlay != null) {
                try {
                    mGoogleOverlay.windowAttached(mPendingLp, mPendingCb, mPendingFlags);
                } catch (RemoteException e) {
                    Log.w(TAG, "replay windowAttached failed", e);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mGoogleOverlay = null;
            Log.d(TAG, "Google overlay disconnected");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        bindGoogleOverlay();
    }

    private void bindGoogleOverlay() {
        // Mirror the data URI the launcher's LauncherClient builds, but identify the bridge so
        // the Google App sees a debuggable caller it will serve. v/cv are the overlay protocol
        // version params Lawnchair's FeedBridge also uses.
        Uri uri = Uri.parse("app://" + getPackageName() + ":" + Process.myUid())
                .buildUpon()
                .appendQueryParameter("v", "7")
                .appendQueryParameter("cv", "9")
                .build();
        Intent intent = new Intent(OVERLAY_ACTION)
                .setPackage(GOOGLE_APP)
                .setData(uri);
        try {
            mGoogleBound = bindService(intent, mGoogleConnection, Context.BIND_AUTO_CREATE);
            if (!mGoogleBound) {
                Log.w(TAG, "could not bind Google overlay (is the Google app installed?)");
            }
        } catch (Exception e) {
            Log.w(TAG, "bindGoogleOverlay failed", e);
        }
    }

    @Override
    public void onDestroy() {
        if (mGoogleBound) {
            try {
                unbindService(mGoogleConnection);
            } catch (Exception ignored) {
            }
            mGoogleBound = false;
        }
        super.onDestroy();
    }

    private final ILauncherOverlay.Stub mBinder = new ILauncherOverlay.Stub() {
        @Override
        public void startScroll() throws RemoteException {
            if (mGoogleOverlay != null) mGoogleOverlay.startScroll();
        }

        @Override
        public void onScroll(float progress) throws RemoteException {
            if (mGoogleOverlay != null) mGoogleOverlay.onScroll(progress);
        }

        @Override
        public void endScroll() throws RemoteException {
            if (mGoogleOverlay != null) mGoogleOverlay.endScroll();
        }

        @Override
        public void windowAttached(WindowManager.LayoutParams lp, ILauncherOverlayCallback cb, int flags)
                throws RemoteException {
            // Cache for replay, then forward the launcher's callback straight through to Google.
            mPendingLp = lp;
            mPendingCb = cb;
            mPendingFlags = flags;
            mHasPendingAttach = true;
            if (mGoogleOverlay != null) mGoogleOverlay.windowAttached(lp, cb, flags);
        }

        @Override
        public void windowDetached(boolean isChangingConfigurations) throws RemoteException {
            mHasPendingAttach = false;
            mPendingLp = null;
            mPendingCb = null;
            if (mGoogleOverlay != null) mGoogleOverlay.windowDetached(isChangingConfigurations);
        }

        @Override
        public void closeOverlay(int flags) throws RemoteException {
            if (mGoogleOverlay != null) mGoogleOverlay.closeOverlay(flags);
        }

        @Override
        public void onPause() throws RemoteException {
            if (mGoogleOverlay != null) mGoogleOverlay.onPause();
        }

        @Override
        public void onResume() throws RemoteException {
            if (mGoogleOverlay != null) mGoogleOverlay.onResume();
        }

        @Override
        public void openOverlay(int flags) throws RemoteException {
            if (mGoogleOverlay != null) mGoogleOverlay.openOverlay(flags);
        }

        @Override
        public void requestVoiceDetection(boolean start) throws RemoteException {
            if (mGoogleOverlay != null) mGoogleOverlay.requestVoiceDetection(start);
        }

        @Override
        public String getVoiceSearchLanguage() throws RemoteException {
            return mGoogleOverlay != null ? mGoogleOverlay.getVoiceSearchLanguage() : "";
        }

        @Override
        public boolean isVoiceDetectionRunning() throws RemoteException {
            return mGoogleOverlay != null && mGoogleOverlay.isVoiceDetectionRunning();
        }

        @Override
        public boolean hasOverlayContent() throws RemoteException {
            return mGoogleOverlay != null && mGoogleOverlay.hasOverlayContent();
        }

        @Override
        public void windowAttached2(Bundle bundle, ILauncherOverlayCallback cb) throws RemoteException {
            if (mGoogleOverlay != null) mGoogleOverlay.windowAttached2(bundle, cb);
        }

        @Override
        public void unusedMethod() throws RemoteException {
            if (mGoogleOverlay != null) mGoogleOverlay.unusedMethod();
        }

        @Override
        public void setActivityState(int flags) throws RemoteException {
            if (mGoogleOverlay != null) mGoogleOverlay.setActivityState(flags);
        }

        @Override
        public boolean startSearch(byte[] data, Bundle bundle) throws RemoteException {
            return mGoogleOverlay != null && mGoogleOverlay.startSearch(data, bundle);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}

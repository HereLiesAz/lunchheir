package com.hereliesaz.lunchheir.bridge;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.view.WindowManager;

import com.google.android.libraries.launcherclient.ILauncherOverlay;
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback;

/**
 * Service the launcher binds to for the Google Discover ("-1") overlay.
 *
 * Increment 1 is a stub: it implements the full {@link ILauncherOverlay} contract with no-ops
 * so the module builds and the binding surface is in place. A later increment turns this into a
 * proxy that binds to the Google App's own overlay service and forwards every call/callback,
 * which is what actually relays the feed.
 */
public class OverlayService extends Service {

    private final ILauncherOverlay.Stub mBinder = new ILauncherOverlay.Stub() {
        @Override
        public void startScroll() {
        }

        @Override
        public void onScroll(float progress) {
        }

        @Override
        public void endScroll() {
        }

        @Override
        public void windowAttached(WindowManager.LayoutParams lp, ILauncherOverlayCallback cb, int flags) {
        }

        @Override
        public void windowDetached(boolean isChangingConfigurations) {
        }

        @Override
        public void closeOverlay(int flags) {
        }

        @Override
        public void onPause() {
        }

        @Override
        public void onResume() {
        }

        @Override
        public void openOverlay(int flags) {
        }

        @Override
        public void requestVoiceDetection(boolean start) {
        }

        @Override
        public String getVoiceSearchLanguage() {
            return "";
        }

        @Override
        public boolean isVoiceDetectionRunning() {
            return false;
        }

        @Override
        public boolean hasOverlayContent() {
            return false;
        }

        @Override
        public void windowAttached2(Bundle bundle, ILauncherOverlayCallback cb) {
        }

        @Override
        public void unusedMethod() {
        }

        @Override
        public void setActivityState(int flags) {
        }

        @Override
        public boolean startSearch(byte[] data, Bundle bundle) {
            return false;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}

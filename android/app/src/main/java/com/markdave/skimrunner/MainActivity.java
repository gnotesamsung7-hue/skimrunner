package com.markdave.skimrunner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Full-screen landscape WebView host for SkimRunner.
 * Reads USB OTG joysticks (EdgeTX / OpenTX "USB Joystick" HID) natively and hands
 * the axes to the game through window.AndroidPad.state().
 */
public class MainActivity extends Activity {
    private static final int REQ_MIC = 7, REQ_FILE = 8;
    private WebView web;
    private PermissionRequest pendingPermission;
    private ValueCallback<Uri[]> fileCallback;

    // Latest joystick state (guarded by "this")
    private final float[] axes = new float[16];
    private final boolean[] buttons = new boolean[32];
    private int[] axisIds = new int[0];
    private float[] axisMin = new float[0], axisMax = new float[0];
    private int padDeviceId = -1;
    private String padName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        pendingPermission = request;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new PadBridge(), "AndroidPad");
        setContentView(web);
        hideSystemUi();
        findPad();
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    /** Exposed to JS as window.AndroidPad */
    private class PadBridge {
        @JavascriptInterface
        public String state() {
            synchronized (MainActivity.this) {
                if (padDeviceId == -1 || InputDevice.getDevice(padDeviceId) == null) {
                    padDeviceId = -1;
                    return "";
                }
                StringBuilder sb = new StringBuilder(padName.replace('|', ' ')).append('|');
                for (int i = 0; i < axisIds.length && i < axes.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(Math.round(axes[i] * 1000f) / 1000f);
                }
                sb.append('|');
                for (boolean b : buttons) sb.append(b ? '1' : '0');
                return sb.toString();
            }
        }

        @JavascriptInterface
        public void exit() { runOnUiThread(MainActivity.this::finish); }
    }

    private static boolean isPad(int source) {
        return (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
    }

    /** Look for an already-connected joystick so the game can show it before any stick moves. */
    private void findPad() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && (d.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                bindPad(d);
                return;
            }
        }
    }

    /** Build the axis list (sorted by axis id: X, Y, Z, RX, RY, RZ, THROTTLE, RUDDER...) = EdgeTX CH1..CH8. */
    private synchronized void bindPad(InputDevice d) {
        List<Integer> ids = new ArrayList<>();
        for (InputDevice.MotionRange r : d.getMotionRanges()) {
            if ((r.getSource() & InputDevice.SOURCE_JOYSTICK) != 0 && !ids.contains(r.getAxis())) ids.add(r.getAxis());
        }
        Collections.sort(ids);
        int n = Math.min(ids.size(), axes.length);
        axisIds = new int[n];
        axisMin = new float[n];
        axisMax = new float[n];
        for (int i = 0; i < n; i++) {
            axisIds[i] = ids.get(i);
            InputDevice.MotionRange r = d.getMotionRange(axisIds[i], InputDevice.SOURCE_JOYSTICK);
            axisMin[i] = r != null ? r.getMin() : -1f;
            axisMax[i] = r != null ? r.getMax() : 1f;
            axes[i] = 0f;
        }
        padDeviceId = d.getId();
        padName = d.getName();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (isPad(ev.getSource()) && ev.getAction() == MotionEvent.ACTION_MOVE) {
            InputDevice d = ev.getDevice();
            if (d != null) {
                if (d.getId() != padDeviceId) bindPad(d);
                synchronized (this) {
                    for (int i = 0; i < axisIds.length; i++) {
                        float v = ev.getAxisValue(axisIds[i]);
                        float span = axisMax[i] - axisMin[i];
                        // Normalise every axis to -1..1 (Android reports throttle-type axes as 0..1)
                        axes[i] = span > 0 ? ((v - axisMin[i]) / span) * 2f - 1f : v;
                    }
                }
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        if (isPad(ev.getSource()) && code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) {
            int idx = -1;
            if (code >= KeyEvent.KEYCODE_BUTTON_1 && code <= KeyEvent.KEYCODE_BUTTON_16) idx = code - KeyEvent.KEYCODE_BUTTON_1;
            else if (code >= KeyEvent.KEYCODE_BUTTON_A && code <= KeyEvent.KEYCODE_BUTTON_MODE) idx = 16 + (code - KeyEvent.KEYCODE_BUTTON_A);
            if (idx >= 0 && idx < buttons.length) {
                InputDevice d = ev.getDevice();
                if (d != null && d.getId() != padDeviceId && (d.getSources() & InputDevice.SOURCE_JOYSTICK) != 0) bindPad(d);
                synchronized (this) { buttons[idx] = ev.getAction() == KeyEvent.ACTION_DOWN; }
            }
            return true; // swallow radio buttons so they never act as BACK
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (ev.getAction() == KeyEvent.ACTION_UP) {
                web.evaluateJavascript("(window.onBack&&window.onBack())?'1':'0'", r -> {
                    if (r == null || !r.contains("1")) finish();
                });
            }
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        findPad(); // radio plugged in while running
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQ_MIC && pendingPermission != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) pendingPermission.grant(pendingPermission.getResources());
            else pendingPermission.deny();
            pendingPermission = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        hideSystemUi();
        findPad();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }
}

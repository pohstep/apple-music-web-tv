package dev.pohstep.applemusicweb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOME = "https://music.apple.com/";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private FrameLayout root;
    private WebView main;
    private final ArrayList<WebView> popups = new ArrayList<>();
    private CursorView cursor;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // cursor state
    private float cx, cy;
    private boolean up, down, left, right;
    private long moveStart;
    private boolean mouseDown;
    private long mouseDownTime;
    private long lastBack, lastRootBack;
    private boolean backTracking;
    private float density = 1f;

    // v2 state
    private static final String ZOOM = "1.3";
    private boolean focusMode = false;
    private boolean autoSwitched;      // true when we forced cursor mode for sign-in (auto-return afterwards)
    private boolean playMode;
    private boolean menuTracking;
    private long lastNav;
    private int stuckCount, noScriptCount;
    private final java.util.HashSet<String> stuckDirs = new java.util.HashSet<>();
    private PlayView play;
    private Toast toast;
    private String jsSource;
    private String lyricKey = "";
    private boolean resumed;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!(up || down || left || right)) return;
            long held = SystemClock.uptimeMillis() - moveStart;
            // accelerate from 6px/tick up to ~40px/tick (at 1080p) over ~1.2 seconds
            float base = 6f + Math.min(held, 1200) / 1200f * 34f;
            float step = base * density;
            float dx = (right ? step : 0) - (left ? step : 0);
            float dy = (down ? step : 0) - (up ? step : 0);
            moveCursor(dx, dy);
            handler.postDelayed(this, 16);
        }
    };

    private WebView top() { return popups.isEmpty() ? main : popups.get(popups.size() - 1); }

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        density = Math.max(1f, getResources().getDisplayMetrics().widthPixels / 1920f);
        WebView.setWebContentsDebuggingEnabled(true);

        CookieManager.getInstance().setAcceptCookie(true);

        main = newWebView();
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));
        main.addJavascriptInterface(new Bridge(), "AMWNative");
        cursor = new CursorView(this);
        cursor.setVisibility(View.VISIBLE); // cursor mode is the default
        root.addView(cursor, new FrameLayout.LayoutParams(-1, -1));
        play = new PlayView(this);
        play.setVisibility(View.GONE);
        root.addView(play, new FrameLayout.LayoutParams(-1, -1));
        root.post(() -> {
            cx = root.getWidth() / 2f;
            cy = root.getHeight() / 2f;
            cursor.invalidate();
        });
        if (b != null) main.restoreState(b); else main.loadUrl(HOME);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        main.saveState(out);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView newWebView() {
        WebView w = new WebView(this);
        w.setBackgroundColor(Color.BLACK);
        w.setFocusable(false);
        w.setFocusableInTouchMode(true);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(UA);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);

        w.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sch = r.getUrl().getScheme();
                return !("http".equals(sch) || "https".equals(sch) || "about".equals(sch));
            }
            @Override public void onPageFinished(WebView v, String url) {
                CookieManager.getInstance().flush();
                if (v == main) {
                    injectScripts();
                    handler.postDelayed(() -> injectScripts(), 1500);
                }
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                recreate();
                return true;
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(() -> {
                    ArrayList<String> ok = new ArrayList<>();
                    for (String r : req.getResources()) {
                        if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) ok.add(r);
                    }
                    if (ok.isEmpty()) req.deny();
                    else req.grant(ok.toArray(new String[0]));
                });
            }
            @Override public boolean onCreateWindow(WebView v, boolean dialog, boolean user, Message resultMsg) {
                WebView child = newWebView();
                popups.add(child);
                root.addView(child, root.indexOfChild(cursor), new FrameLayout.LayoutParams(-1, -1));
                if (focusMode) { autoSwitched = true; setFocusMode(false, "Sign-in window: cursor mode"); }
                WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                t.setWebView(child);
                resultMsg.sendToTarget();
                return true;
            }
            @Override public void onCloseWindow(WebView v) { closePopup(v); }
            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customCb = cb;
                root.addView(view, root.indexOfChild(cursor), new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                if (customCb != null) customCb.onCustomViewHidden();
                customCb = null;
            }
        });
        return w;
    }

    private void closePopup(WebView v) {
        if (!popups.remove(v)) return;
        root.removeView(v);
        v.destroy();
        if (popups.isEmpty() && autoSwitched && !focusMode) {
            autoSwitched = false;
            setFocusMode(true, "Focus mode");
        }
    }

    // ---------- cursor / mouse emulation ----------

    private void moveCursor(float dx, float dy) {
        int w = root.getWidth(), h = root.getHeight();
        float nx = Math.max(0, Math.min(w - 1, cx + dx));
        float ny = Math.max(0, Math.min(h - 1, cy + dy));
        // Pushing against an edge scrolls the page (wheel event)
        float scrollY = 0, scrollX = 0;
        if (dy > 0 && cy >= h - 1) scrollY = -1;
        if (dy < 0 && cy <= 0) scrollY = 1;
        if (dx > 0 && cx >= w - 1) scrollX = -1;
        if (dx < 0 && cx <= 0) scrollX = 1;
        cx = nx; cy = ny;
        cursor.invalidate();
        if (scrollX != 0 || scrollY != 0) {
            sendScroll(scrollX * 0.6f, scrollY * 0.6f);
        } else if (mouseDown) {
            sendMouse(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_PRIMARY);
        } else {
            sendMouse(MotionEvent.ACTION_HOVER_MOVE, 0);
        }
    }

    private MotionEvent obtain(int action, int buttons, long downTime) {
        boolean hover = action == MotionEvent.ACTION_HOVER_MOVE;
        MotionEvent.PointerProperties[] pp = { new MotionEvent.PointerProperties() };
        pp[0].id = 0;
        pp[0].toolType = hover ? MotionEvent.TOOL_TYPE_MOUSE : MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords[] pc = { new MotionEvent.PointerCoords() };
        pc[0].x = cx; pc[0].y = cy; pc[0].pressure = 1f; pc[0].size = 1f;
        return MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1, pp, pc,
                0, hover ? buttons : 0, 1f, 1f, 0, 0,
                hover ? InputDevice.SOURCE_MOUSE : InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    private void sendMouse(int action, int buttons) {
        WebView w = top();
        int[] loc = new int[2];
        w.getLocationOnScreen(loc);
        MotionEvent e = obtain(action, buttons, action == MotionEvent.ACTION_DOWN
                ? SystemClock.uptimeMillis() : mouseDownTime);
        if (action == MotionEvent.ACTION_DOWN) { mouseDownTime = e.getDownTime(); w.requestFocus(); }
        if (action == MotionEvent.ACTION_HOVER_MOVE) w.dispatchGenericMotionEvent(e);
        else w.dispatchTouchEvent(e);
        e.recycle();
    }

    private long lastScroll;
    private void sendScroll(float h, float v) {
        long now = SystemClock.uptimeMillis();
        if (now - lastScroll < 40) return;
        lastScroll = now;
        MotionEvent.PointerProperties[] pp = { new MotionEvent.PointerProperties() };
        pp[0].id = 0; pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = { new MotionEvent.PointerCoords() };
        pc[0].x = cx; pc[0].y = cy;
        pc[0].setAxisValue(MotionEvent.AXIS_HSCROLL, h);
        pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, v);
        MotionEvent e = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, pp, pc,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        top().dispatchGenericMotionEvent(e);
        e.recycle();
    }

    private void dirKey(int code, boolean pressed) {
        boolean wasMoving = up || down || left || right;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: up = pressed; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: down = pressed; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: left = pressed; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: right = pressed; break;
        }
        boolean moving = up || down || left || right;
        if (moving && !wasMoving) {
            moveStart = SystemClock.uptimeMillis();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } else if (!moving) {
            handler.removeCallbacks(ticker);
        }
    }

    // ---------- keys ----------

    private static boolean isPlayToggleKey(int code) {
        return code == KeyEvent.KEYCODE_INFO || code == KeyEvent.KEYCODE_PROG_YELLOW
                || code == KeyEvent.KEYCODE_GUIDE || code == KeyEvent.KEYCODE_MEDIA_RECORD;
    }

    /** Media keys behave the same everywhere (also in play mode). Returns true if handled. */
    private boolean mediaKey(int code, boolean isDown, int rc) {
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                if (isDown && rc == 0) media(code);
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (isDown && rc == 0) media(KeyEvent.KEYCODE_MEDIA_NEXT);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (isDown && rc == 0) media(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                return true;
        }
        return false;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        boolean isDown = ev.getAction() == KeyEvent.ACTION_DOWN;
        boolean isUp = ev.getAction() == KeyEvent.ACTION_UP;
        int rc = ev.getRepeatCount();

        if (playMode) {
            if (isPlayToggleKey(code)) { if (isDown && rc == 0) exitPlayMode(); return true; }
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    if (isDown && rc == 0) { backTracking = false; exitPlayMode(); }
                    return true;
            }
            if (mediaKey(code, isDown, rc)) return true;
            return super.dispatchKeyEvent(ev);
        }

        if (isPlayToggleKey(code)) {
            if (isDown && rc == 0) enterPlayMode();
            return true;
        }
        if (mediaKey(code, isDown, rc)) return true;

        // Hardware keyboards/typed text go straight through, except nav keys we own.
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focusMode) {
                    if (isDown) focusNav(code, rc);
                    return true;
                }
                if (isDown && rc == 0) dirKey(code, true);
                else if (isUp) dirKey(code, false);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (ev.getSource() == InputDevice.SOURCE_KEYBOARD && code != KeyEvent.KEYCODE_DPAD_CENTER
                        && ev.getDevice() != null && ev.getDevice().isVirtual() == false
                        && ev.getDevice().getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
                    return super.dispatchKeyEvent(ev); // real keyboard Enter: let the page have it
                }
                if (focusMode) {
                    if (isDown && rc == 0) focusActivate();
                    return true;
                }
                if (isDown && rc == 0 && !mouseDown) {
                    mouseDown = true;
                    sendMouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY);
                } else if (isUp && mouseDown) {
                    mouseDown = false;
                    sendMouse(MotionEvent.ACTION_UP, 0);
                }
                return true;
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                if (isDown) { lastScroll = 0; sendScroll(0, -3f); }
                return true;
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                if (isDown) { lastScroll = 0; sendScroll(0, 3f); }
                return true;
            case KeyEvent.KEYCODE_MENU:
                // short press: focus <-> cursor mode; long press: reload
                if (isDown) {
                    if (rc == 0) menuTracking = true;
                    else if (rc >= 8 && menuTracking) {
                        menuTracking = false;
                        showToast("Reloading");
                        top().reload();
                    }
                } else if (isUp && menuTracking && !ev.isCanceled()) {
                    menuTracking = false;
                    toggleMode();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (isDown) {
                    if (rc == 0) {
                        backTracking = true;
                    } else if (rc >= 8 && backTracking) { // long press
                        backTracking = false;
                        finish();
                    }
                } else if (isUp && backTracking && !ev.isCanceled()) {
                    backTracking = false;
                    onBackTap();
                }
                return true;
        }
        return super.dispatchKeyEvent(ev);
    }

    // ---------- mode handling ----------

    private void showToast(String msg) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    private void toggleMode() {
        autoSwitched = false;
        if (!focusMode) {
            if (!popups.isEmpty()) { showToast("Sign-in window: cursor mode only"); return; }
            setFocusMode(true, "Focus mode: arrows jump between items (Menu = cursor)");
        } else {
            setFocusMode(false, "Cursor mode: arrows move the pointer (Menu = focus)");
        }
    }

    private void setFocusMode(boolean focus, String msg) {
        focusMode = focus;
        up = down = left = right = false;
        handler.removeCallbacks(ticker);
        stuckCount = 0; noScriptCount = 0; stuckDirs.clear();
        if (cursor != null) cursor.setVisibility(focus || playMode ? View.GONE : View.VISIBLE);
        if (main != null) main.evaluateJavascript("window.__amw&&__amw.ring(" + focus + ")", null);
        if (msg != null) showToast(msg);
    }

    private void autoToCursor(String msg, boolean forSignIn) {
        if (!focusMode) return;
        autoSwitched = forSignIn;
        setFocusMode(false, msg);
    }

    // ---------- focus navigation ----------

    private String jsText() {
        if (jsSource == null) {
            try {
                InputStream in = getResources().openRawResource(R.raw.amw);
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                jsSource = bo.toString("UTF-8").replace("%ZOOM%", ZOOM);
            } catch (Exception e) { jsSource = ""; }
        }
        return jsSource;
    }

    private void injectScripts() {
        if (main == null) return;
        String host = null;
        try { host = Uri.parse(main.getUrl()).getHost(); } catch (Exception e) { }
        if (host == null || !host.endsWith("apple.com") || host.startsWith("idmsa.")
                || host.startsWith("appleid.")) return;
        String js = jsText();
        if (js.isEmpty()) return;
        main.evaluateJavascript(js + ";window.__amw&&__amw.ring(" + (focusMode && !playMode) + ")", null);
    }

    private static String unq(String v) {
        if (v == null) return "";
        v = v.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
        return v;
    }

    private void focusNav(int code, int rc) {
        long now = SystemClock.uptimeMillis();
        if (rc > 0 && now - lastNav < 110) return;
        lastNav = now;
        final String dir = code == KeyEvent.KEYCODE_DPAD_UP ? "up" : code == KeyEvent.KEYCODE_DPAD_DOWN ? "down"
                : code == KeyEvent.KEYCODE_DPAD_LEFT ? "left" : "right";
        final boolean fresh = rc == 0;
        top().evaluateJavascript("window.__amw?__amw.nav('" + dir + "'):'noscript'", v -> {
            if (!focusMode) return;
            String r = unq(v);
            switch (r) {
                case "iframe":
                    autoToCursor("Sign-in form: cursor mode", true);
                    break;
                case "noel":
                    autoToCursor("Nothing to focus here: cursor mode", false);
                    break;
                case "stuck":
                    if (fresh) {
                        stuckCount++;
                        stuckDirs.add(dir);
                        if (stuckCount >= 3 && stuckDirs.size() >= 2) {
                            autoToCursor("Focus can't move: cursor mode (Menu = focus)", false);
                        }
                    }
                    break;
                case "noscript":
                case "null":
                case "":
                    injectScripts();
                    if (++noScriptCount >= 4) autoToCursor("Page not ready for focus: cursor mode", false);
                    break;
                default:
                    stuckCount = 0; noScriptCount = 0; stuckDirs.clear();
            }
        });
    }

    private void focusActivate() {
        top().evaluateJavascript("window.__amw?__amw.act():'noscript'", v -> {
            if (!focusMode) return;
            String r = unq(v);
            if (r.startsWith("tap:")) {
                String[] p = r.split(":");
                try {
                    float x = Float.parseFloat(p[1]), y = Float.parseFloat(p[2]), iw = Float.parseFloat(p[3]);
                    boolean inp = p.length > 4 && "1".equals(p[4]);
                    tapAt(x, y, iw, inp);
                } catch (Exception e) { }
            } else if ("iframe".equals(r)) {
                autoToCursor("Sign-in form: cursor mode", true);
            }
        });
    }

    private void tapAt(float xCss, float yCss, float iw, final boolean inp) {
        float scale = iw > 0 ? main.getWidth() / iw : density;
        final float sx = cx, sy = cy;
        cx = xCss * scale; cy = yCss * scale;
        sendMouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY);
        handler.postDelayed(() -> {
            sendMouse(MotionEvent.ACTION_UP, 0);
            cx = sx; cy = sy;
            if (inp) {
                handler.postDelayed(() -> {
                    main.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(main, 0);
                }, 200);
            }
        }, 60);
    }

    // ---------- play mode + now playing ----------

    private class Bridge {
        @JavascriptInterface public void togglePlay() {
            runOnUiThread(() -> { if (playMode) exitPlayMode(); else enterPlayMode(); });
        }
        @JavascriptInterface public void zoomFailed() {
            runOnUiThread(() -> main.getSettings().setTextZoom(120));
        }
    }

    private void enterPlayMode() {
        if (playMode) return;
        playMode = true;
        up = down = left = right = false;
        handler.removeCallbacks(ticker);
        cursor.setVisibility(View.GONE);
        main.evaluateJavascript("window.__amw&&__amw.ring(false)", null);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(main.getWindowToken(), 0);
        play.setVisibility(View.VISIBLE);
        play.start();
        poll.run();
    }

    private void exitPlayMode() {
        if (!playMode) return;
        playMode = false;
        play.setVisibility(View.GONE);
        cursor.setVisibility(focusMode ? View.GONE : View.VISIBLE);
        main.evaluateJavascript("window.__amw&&__amw.ring(" + focusMode + ")", null);
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            handler.removeCallbacks(this);
            if (resumed) handler.postDelayed(this, 1000);
            if (main == null) return;
            String known = lyricKey.replaceAll("[^A-Za-z0-9._-]", "");
            main.evaluateJavascript("window.__amw?__amw.state('" + known + "'):null", v -> onState(v));
        }
    };

    private void onState(String v) {
        if (v == null || v.equals("null")) { injectScripts(); return; }
        try {
            Object ov = new JSONTokener(v).nextValue();
            if (!(ov instanceof String)) return;
            JSONObject o = new JSONObject((String) ov);
            // sign-in iframe auto switching
            boolean af = o.optInt("af", 0) == 1;
            if (af && focusMode && !playMode) autoToCursor("Sign-in form: cursor mode", true);
            else if (!af && autoSwitched && !focusMode && popups.isEmpty()) {
                autoSwitched = false;
                setFocusMode(true, "Focus mode");
            }
            if (o.has("lk")) {
                String k = o.getString("lk");
                if (o.has("ll")) {
                    JSONArray arr = o.getJSONArray("ll");
                    int n = arr.length();
                    double[] b = new double[n], e = new double[n];
                    String[] t = new String[n];
                    for (int i = 0; i < n; i++) {
                        JSONArray l = arr.getJSONArray(i);
                        b[i] = l.getDouble(0); e[i] = l.getDouble(1); t[i] = l.getString(2);
                    }
                    play.setLyrics(b, e, t);
                }
                lyricKey = k;
            } else if (!lyricKey.isEmpty()) {
                lyricKey = "";
                play.setLyrics(null, null, null);
            }
            play.update(o);
        } catch (Exception e) { }
    }

    private void onBackTap() {
        long now = SystemClock.uptimeMillis();
        if (now - lastBack < 400) { finish(); return; } // double Back exits
        lastBack = now;
        if (customView != null) { exitCustomView(); return; }
        if (!popups.isEmpty()) {
            WebView p = popups.get(popups.size() - 1);
            if (p.canGoBack()) p.goBack(); else closePopup(p);
            return;
        }
        if (main.canGoBack()) { main.goBack(); return; }
        if (now - lastRootBack < 2000) { finish(); return; }
        lastRootBack = now;
        Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).show();
    }

    private void exitCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        if (customCb != null) customCb.onCustomViewHidden();
        customCb = null;
    }

    // Prevent the framework's default Back handling (we do it in dispatchKeyEvent).
    @Override public void onBackPressed() { }

    private static final String JS_TOGGLE =
        "(function(){function all(r,o){var m=r.querySelectorAll('*');for(var i=0;i<m.length;i++){o.push(m[i]);"
        + "if(m[i].shadowRoot)all(m[i].shadowRoot,o);}return o;}"
        + "var els=all(document,[]);var med=els.filter(function(e){return e.tagName==='AUDIO'||e.tagName==='VIDEO';});"
        + "var mode='%MODE%';"
        + "if(mode==='toggle'||mode==='play'||mode==='pause'||mode==='stop'){"
        + " var btns=els.filter(function(e){var l=(e.getAttribute&&(e.getAttribute('aria-label')||e.getAttribute('title')))||'';"
        + "  return (e.tagName==='BUTTON'||e.getAttribute('role')==='button')&&/^(play|pause)$/i.test(l.trim())&&e.getBoundingClientRect().width>0;});"
        + " var playing=med.some(function(m){return !m.paused&&!m.ended;});"
        + " if(mode==='play'&&playing)return 'already';if((mode==='pause'||mode==='stop')&&!playing)return 'already';"
        + " var pb=btns.filter(function(b){return /pause/i.test(b.getAttribute('aria-label')||b.getAttribute('title'))===playing;});"
        + " if(pb.length){pb[0].click();return 'btn';}"
        + " for(var i=0;i<med.length;i++){var m=med[i];if(playing){if(!m.paused)m.pause();}else if(m.src||m.currentSrc){m.play();}}return 'media';}"
        + "var re=mode==='next'?/^next/i:/^(previous|back)/i;"
        + "var b=els.filter(function(e){var l=(e.getAttribute&&(e.getAttribute('aria-label')||e.getAttribute('title')))||'';"
        + " return (e.tagName==='BUTTON'||e.getAttribute('role')==='button')&&re.test(l.trim())&&e.getBoundingClientRect().width>0;});"
        + "if(b.length){b[0].click();return 'btn';}return 'none';})()";

    private void media(int code) {
        String mode;
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY: mode = "play"; break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE: mode = "pause"; break;
            case KeyEvent.KEYCODE_MEDIA_STOP: mode = "stop"; break;
            case KeyEvent.KEYCODE_MEDIA_NEXT: mode = "next"; break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: mode = "prev"; break;
            default: mode = "toggle";
        }
        // Playback lives in the main page (popups are only for sign-in).
        main.evaluateJavascript(JS_TOGGLE.replace("%MODE%", mode), null);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        poll.run();
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(poll);
        CookieManager.getInstance().flush();
        dirKey(KeyEvent.KEYCODE_DPAD_UP, false); up = down = left = right = false;
        handler.removeCallbacks(ticker);
        // Intentionally NOT calling WebView.onPause(): keep music playing in background.
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    // ---------- cursor drawing ----------

    private class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path p = new Path();
        CursorView(Context c) {
            super(c);
            setFocusable(false);
            setClickable(false);
            fill.setColor(Color.WHITE);
            stroke.setColor(Color.BLACK);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(3f);
        }
        @Override protected void onDraw(Canvas c) {
            float s = 1.3f * density;
            c.save();
            c.translate(cx, cy);
            c.scale(s, s);
            p.reset();
            p.moveTo(0, 0); p.lineTo(0, 24); p.lineTo(6, 18); p.lineTo(11, 29);
            p.lineTo(15, 27); p.lineTo(10, 16.5f); p.lineTo(18, 16.5f); p.close();
            c.drawPath(p, fill);
            c.drawPath(p, stroke);
            c.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent e) { return false; }
    }

    // ---------- play mode overlay ----------

    private class PlayView extends View {
        private String title = "", artist = "", album = "", artUrl = "";
        private boolean playing, hasTrack;
        private double pos = -1, dur = -1, rate = 1;
        private long posTs;
        private double[] lb, le;
        private String[] lt;
        private String domCur = "", domNext = "";
        private Bitmap art;
        private String artLoading = "";

        private final Paint bg = new Paint();
        private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tArtist = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tAlbum = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tLyric = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tNext = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tSmall = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tClock = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final Matrix mat = new Matrix();
        private RadialGradient g1, g2;
        private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);
        private final Date date = new Date();

        // cached layouts
        private StaticLayout lTitle, lArtist, lAlbum, lCur, lNext;
        private String kTitle = "", kArtist = "", kAlbum = "", kCur = "\u0000", kNext = "\u0000";
        private int kWidth = -1;

        PlayView(Context c) {
            super(c);
            setFocusable(false);
            setClickable(false);
            bg.setColor(0xFF04060A);
            // everything stays under ~70% grey
            tTitle.setColor(0xFFB0B0B0); tTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tArtist.setColor(0xFF878787);
            tAlbum.setColor(0xFF626262);
            tLyric.setColor(0xFFA8A8A8); tLyric.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tNext.setColor(0xFF5E5E5E);
            tSmall.setColor(0xFF5E5E5E);
            tClock.setColor(0xFF7C7C7C); tClock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            artPaint.setColorFilter(new LightingColorFilter(0xFFD9D9D9, 0)); // art at ~85%
            box.setColor(0xFF10141B);
            bar.setStyle(Paint.Style.FILL);
        }

        void start() { invalidate(); }

        void setLyrics(double[] b, double[] e, String[] t) { lb = b; le = e; lt = t; }

        void update(JSONObject o) {
            String t = o.optString("t", ""), a = o.optString("a", ""), al = o.optString("al", "");
            String ps = o.optString("ps", "none");
            hasTrack = !"none".equals(ps) && !(t.isEmpty() && a.isEmpty());
            title = t; artist = a; album = al;
            playing = "playing".equals(ps);
            pos = o.has("pos") ? o.optDouble("pos", -1) : -1;
            dur = o.has("dur") ? o.optDouble("dur", -1) : -1;
            rate = o.optDouble("rate", 1);
            posTs = SystemClock.uptimeMillis();
            domCur = o.optString("lc", ""); domNext = o.optString("ln", "");
            String u = o.optString("art", "");
            if (u.contains("mzstatic.com")) u = u.replaceAll("/\\d+x\\d+([a-z]{0,3})\\.(jpg|jpeg|png|webp)$", "/800x800$1.$2");
            if (!u.equals(artUrl)) {
                artUrl = u;
                if (u.isEmpty()) art = null; else loadArt(u);
            }
        }

        private void loadArt(final String u) {
            if (u.equals(artLoading)) return;
            artLoading = u;
            new Thread(() -> {
                Bitmap bm = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000);
                    InputStream in = c.getInputStream();
                    bm = BitmapFactory.decodeStream(in);
                    in.close();
                } catch (Exception e) { }
                final Bitmap res = bm;
                post(() -> {
                    if (u.equals(artUrl)) { art = res; invalidate(); }
                    if (res == null) artLoading = ""; // allow a retry on the next update
                });
            }).start();
        }

        private double posNow() {
            if (pos < 0) return -1;
            double p = pos;
            if (playing) p += (SystemClock.uptimeMillis() - posTs) / 1000.0 * rate;
            if (dur > 0 && p > dur) p = dur;
            return p;
        }

        private StaticLayout lay(String text, TextPaint tp, int w, int maxLines) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), tp, w)
                    .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0, 1.05f).build();
        }

        private String fmtTime(double s) {
            int t = (int) Math.max(0, s);
            return (t / 60) + ":" + (t % 60 < 10 ? "0" : "") + (t % 60);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float r = Math.max(w, h) * 0.75f;
            g1 = new RadialGradient(0, 0, r, 0xFF0C1626, 0x0004060A, Shader.TileMode.CLAMP);
            g2 = new RadialGradient(0, 0, r * 0.8f, 0xFF121018, 0x0004060A, Shader.TileMode.CLAMP);
            kWidth = -1;
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            long now = System.currentTimeMillis();
            float u = h / 1080f;
            c.drawRect(0, 0, w, h, bg);
            // slow drifting dark gradients (full cycle takes several minutes)
            double t = now / 1000.0;
            if (g1 != null) {
                Paint p = new Paint();
                mat.setTranslate((float) (w * (0.3 + 0.12 * Math.sin(t / 37.0))), (float) (h * (0.35 + 0.10 * Math.cos(t / 41.0))));
                g1.setLocalMatrix(mat); p.setShader(g1); c.drawRect(0, 0, w, h, p);
                mat.setTranslate((float) (w * (0.75 + 0.10 * Math.cos(t / 29.0))), (float) (h * (0.72 + 0.10 * Math.sin(t / 43.0))));
                g2.setLocalMatrix(mat); p.setShader(g2); c.drawRect(0, 0, w, h, p);
            }
            // anti burn-in: whole layout steps a few px every minute
            long m = now / 60000L;
            float ox = (((m * 7) % 9) - 4) * 3f * Math.max(1f, u), oy = (((m * 5) % 7) - 3) * 3f * Math.max(1f, u);
            c.save();
            c.translate(ox, oy);

            date.setTime(now);
            String clock = fmt.format(date);
            if (!hasTrack) {
                tClock.setTextSize(280 * u);
                tClock.setColor(0xFF8A8A8A);
                float tw = tClock.measureText(clock);
                c.drawText(clock, (w - tw) / 2f, h / 2f + 100 * u, tClock);
                c.restore();
                postInvalidateDelayed(125);
                return;
            }
            tClock.setTextSize(120 * u);
            tClock.setColor(0xFF7C7C7C);
            c.drawText(clock, w - 110 * u - tClock.measureText(clock), 175 * u, tClock);

            float artSize = 540 * u;
            float ax = 150 * u, ay = (h - artSize) / 2f + 30 * u;
            rf.set(ax, ay, ax + artSize, ay + artSize);
            float rad = 28 * u;
            if (art != null) {
                Path clip = new Path();
                clip.addRoundRect(rf, rad, rad, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                c.drawBitmap(art, null, rf, artPaint);
                c.restore();
            } else {
                c.drawRoundRect(rf, rad, rad, box);
                tSmall.setTextSize(160 * u);
                String note = "\u266A";
                c.drawText(note, ax + (artSize - tSmall.measureText(note)) / 2f, ay + artSize / 2f + 55 * u, tSmall);
            }

            float tx = ax + artSize + 90 * u;
            int tw = (int) Math.max(200, w - tx - 130 * u);
            // lyric lines (timed if available, else what the page highlights)
            String cur = "", next = "";
            double p = posNow();
            if (lb != null && p >= 0) {
                int idx = -1;
                for (int i = 0; i < lb.length; i++) { if (lb[i] <= p + 0.12) idx = i; else break; }
                if (idx >= 0 && p <= le[idx] + 3.0) cur = lt[idx];
                if (idx + 1 < lb.length) next = lt[idx + 1];
                if (idx < 0 && lb.length > 0) next = lt[0];
            } else if (!domCur.isEmpty()) { cur = domCur; next = domNext; }

            if (kWidth != tw || !kTitle.equals(title) || !kArtist.equals(artist) || !kAlbum.equals(album)
                    || !kCur.equals(cur) || !kNext.equals(next)) {
                tTitle.setTextSize(64 * u); tArtist.setTextSize(40 * u); tAlbum.setTextSize(30 * u);
                tLyric.setTextSize(46 * u); tNext.setTextSize(34 * u);
                lTitle = lay(title.isEmpty() ? " " : title, tTitle, tw, 2);
                lArtist = lay(artist.isEmpty() ? " " : artist, tArtist, tw, 1);
                lAlbum = lay(album.isEmpty() ? " " : album, tAlbum, tw, 1);
                lCur = cur.isEmpty() ? null : lay(cur, tLyric, tw, 2);
                lNext = next.isEmpty() ? null : lay(next, tNext, tw, 1);
                kWidth = tw; kTitle = title; kArtist = artist; kAlbum = album; kCur = cur; kNext = next;
            }
            float y = ay;
            c.save(); c.translate(tx, y); lTitle.draw(c); c.restore(); y += lTitle.getHeight() + 8 * u;
            c.save(); c.translate(tx, y); lArtist.draw(c); c.restore(); y += lArtist.getHeight() + 4 * u;
            if (!album.isEmpty()) { c.save(); c.translate(tx, y); lAlbum.draw(c); c.restore(); y += lAlbum.getHeight(); }
            float barY = ay + artSize - 34 * u;
            y += 44 * u;
            if (lCur != null && y + lCur.getHeight() < barY - 30 * u) {
                c.save(); c.translate(tx, y); lCur.draw(c); c.restore(); y += lCur.getHeight() + 16 * u;
                if (lNext != null && y + lNext.getHeight() < barY - 20 * u) {
                    c.save(); c.translate(tx, y); lNext.draw(c); c.restore();
                }
            } else if (lCur == null && lNext != null && lb != null && y + lNext.getHeight() < barY - 20 * u) {
                c.save(); c.translate(tx, y); lNext.draw(c); c.restore();
            }
            // progress
            if (dur > 0 && p >= 0) {
                float bh = Math.max(3f, 5 * u);
                bar.setColor(0xFF1E232B);
                rf.set(tx, barY, tx + tw, barY + bh);
                c.drawRoundRect(rf, bh / 2, bh / 2, bar);
                bar.setColor(0xFF8E8E8E);
                rf.set(tx, barY, tx + (float) (tw * Math.min(1.0, p / dur)), barY + bh);
                c.drawRoundRect(rf, bh / 2, bh / 2, bar);
                tSmall.setTextSize(26 * u);
                c.drawText(fmtTime(p), tx, barY + bh + 34 * u, tSmall);
                String dt = fmtTime(dur);
                c.drawText(dt, tx + tw - tSmall.measureText(dt), barY + bh + 34 * u, tSmall);
                if (!playing) {
                    String ps = "Paused";
                    c.drawText(ps, tx + (tw - tSmall.measureText(ps)) / 2f, barY + bh + 34 * u, tSmall);
                }
            }
            c.restore();
            postInvalidateDelayed(125);
        }
    }
}

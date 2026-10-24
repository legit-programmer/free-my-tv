package io.freemytv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;

/** Runs against real Android framework classes, including the API 28 drawing fallback. */
public final class CompatSmoke extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context c = getTargetContext();
            c.getSharedPreferences("performance", Context.MODE_PRIVATE).edit().clear()
                    .putBoolean("weather", false).commit();
            ui(() -> { Ui.init(c); Watchdog.clear(c); });
            checkCachedDrawing();
            HomeActivity home = (HomeActivity) startActivitySync(new Intent().setClassName(c, HomeActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            HomeView view = (HomeView) ((ViewGroup) home.findViewById(android.R.id.content)).getChildAt(0);
            check(view.getWidth() > 0, "Home did not lay out");
            screenshot("cinematic");
            if (HomeApps.shelf(c, false).get(0).kind == HomeApps.ALL_APPS) {
                key(home, KeyEvent.KEYCODE_DPAD_CENTER);
                screenshot("all-apps");
                ui(() -> check(view.getChildAt(6).getVisibility() == View.VISIBLE, "All Apps did not open"));
                key(home, KeyEvent.KEYCODE_BACK);
            }
            for (int key : new int[] {KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BACK}) {
                key(home, key);
            }
            waitForIdleSync();
            // Settings and tuning are real activities, in the same isolated UI process.
            Activity settings = startActivitySync(new Intent().setClassName(c, SettingsActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            screenshot("settings");
            Activity tuning = startActivitySync(new Intent().setClassName(c, TuningActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            screenshot("tuning");
            ui(() -> { tuning.finish(); settings.finish(); });
            waitForIdleSync();
            // Exercise both the low-memory clock and the full-mode fallback on Android 9.
            for (boolean lightweight : new boolean[] {true, false}) {
                ui(() -> {
                    view.pause();
                    Performance.set(c, "lightweight", lightweight);
                    DreamView dream = new DreamView(home);
                    home.setContentView(dream);
                    dream.start();
                });
                screenshot(lightweight ? "clock-lightweight" : "clock-full");
                ui(() -> {
                    DreamView dream = (DreamView) ((ViewGroup) home.findViewById(android.R.id.content)).getChildAt(0);
                    dream.stop();
                });
            }
            // The recommendation layout must also work when no TMDB key is configured.
            ui(() -> {
                Performance.set(c, "recommendations", true);
                HomeView cards = new HomeView(home);
                home.setContentView(cards);
                cards.resume();
            });
            screenshot("recommendations-no-key");
            ui(() -> {
                HomeView cards = (HomeView) ((ViewGroup) home.findViewById(android.R.id.content)).getChildAt(0);
                cards.destroy();
                view.destroy();
                home.finish();
            });
            result.putString("stream", "COMPAT_SMOKE_OK API " + Build.VERSION.SDK_INT + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable t) {
            Log.e("CompatSmoke", "Compatibility smoke failed", t);
            result.putString("stream", "COMPAT_SMOKE_FAILED " + Log.getStackTraceString(t));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void checkCachedDrawing() {
        if (Build.VERSION.SDK_INT >= 29) return; // RenderNode requires a hardware canvas, exercised by the UI below.
        DisplayLayer layer = DisplayLayer.create("test");
        Canvas recorded = layer.beginRecording(40, 40);
        Paint p = new Paint();
        p.setColor(Color.RED);
        recorded.drawRect(0, 0, 20, 20, p);
        layer.endRecording();
        layer.setTranslationX(10);
        layer.setAlpha(0.5f);
        Bitmap bitmap = Bitmap.createBitmap(60, 40, Bitmap.Config.ARGB_8888);
        layer.draw(new Canvas(bitmap));
        check(Color.alpha(bitmap.getPixel(15, 5)) >= 126 && Color.alpha(bitmap.getPixel(15, 5)) <= 129,
                "Cached picture lost its alpha");
        check(bitmap.getPixel(5, 5) == 0, "Cached picture lost its translation");
        bitmap.eraseColor(0);
        layer.discard();
        layer.draw(new Canvas(bitmap));
        check(bitmap.getPixel(15, 5) == 0, "Discarded picture was still drawn");
        bitmap.recycle();
    }

    private void screenshot(String name) throws Exception {
        waitForIdleSync();
        SystemClock.sleep(650);
        Bitmap screenshot = getUiAutomation().takeScreenshot();
        check(screenshot != null, "Could not capture " + name);
        File dir = new File(getTargetContext().getExternalFilesDir(null), "compat");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        screenshot.recycle();
    }

    private void key(HomeActivity activity, int code) {
        // Route through Activity just like a remote; emulator system navigation must not steal the event.
        ui(() -> {
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        });
    }

    private void ui(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { error.set(t); } });
        if (error.get() != null) throw new AssertionError("UI compatibility failure", error.get());
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

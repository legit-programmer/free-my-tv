package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lock-screen home: one of the bundled photos (a new one every hour), the date and the big clock over it, and the
 * part of the scene that rises in front of the clock (a peak, a tower) drawn on top, as on the iPhone lock screen.
 * The photos come with their scrims baked in and the cut-out made in advance (tools/make_wallpapers.py), so drawing
 * is two bitmaps and two lines of text, redrawn once a minute. Without a photo yet: the quiet gradient.
 */
final class CinematicHome {
    static final int ACCENT = 0xFFF2F0EA;
    /** The photos are stored at the Mi TV's size; the cut-out positions are in its pixels. */
    private static final float PHOTO_W = 1280;
    private static final long FADE_MS = 1200;

    /** One wallpaper: where its clock goes and the bitmaps (only for the one on screen). */
    private static final class Shot {
        String id, line;      // line: the caption and the photographer, bottom left
        float cx, base, frontLeft, frontTop;
        int ink;
        Bitmap photo, front;
    }

    private final Paint backdrop = new Paint();
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF(), art = new RectF();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Paint clock, date, caption, label, hint;
    private float k;
    private String dateText = "", cachedLabel, fittedLabel;
    private long day = -1;

    private List<Shot> shots;           // the index (read once, in the background)
    private Shot shot, previous;        // on screen; fading out
    private long fadeAt;
    private int wanted = -1;            // the photo for this hour (being loaded or on screen)
    private boolean loading;

    void resize(float scale, int width, int height) {
        k = scale;
        bounds.set(0, 0, width, height);
        backdrop.setShader(new LinearGradient(0, 0, width, height,
                new int[] {0xFF101919, 0xFF172523, 0xFF0C1112}, null, Shader.TileMode.CLAMP));
        clock = Ui.numbers(Ui.displaySemibold, 300 * k, 0xFFF7F5F0);
        clock.setTextAlign(Paint.Align.CENTER);
        date = Ui.text(Ui.medium, 36 * k, 0xFFF7F5F0);
        date.setTextAlign(Paint.Align.CENTER);
        caption = Ui.text(Ui.regular, 22 * k, 0xA6FFFFFF);
        label = Ui.text(Ui.semibold, 34 * k, 0xFFFFFFFF);
        label.setTextAlign(Paint.Align.CENTER);
        label.setShadowLayer(18 * k, 0, 2 * k, 0x66000000);
        hint = Ui.text(Ui.medium, 24 * k, 0xB3FFFFFF);
        hint.setTextAlign(Paint.Align.RIGHT);
        hint.setShadowLayer(14 * k, 0, 2 * k, 0x59000000);
        cachedLabel = null;
        if (shot != null) ink(shot);
    }

    /** The photo for the current hour: loads it in the background when it changes; `ready` runs on the main thread. */
    void update(Context context, Runnable ready) {
        if (loading) return;
        int count = shots == null ? -1 : shots.size();
        if (count == 0) return;
        int index = count < 0 ? -1 : (int) ((System.currentTimeMillis() / 3_600_000L) % count);
        if (index >= 0 && index == wanted) return;
        loading = true;
        new Thread(new Load(context.getApplicationContext(), ready), "wallpaper").start();
    }

    /** In the background: the index (once) and this hour's photo; then {@link Loaded} on the main thread. */
    private final class Load implements Runnable {
        private final Context app;
        private final Runnable ready;

        Load(Context app, Runnable ready) {
            this.app = app;
            this.ready = ready;
        }

        @Override public void run() {
            List<Shot> list = shots != null ? shots : read(app);
            int at = list.isEmpty() ? -1 : (int) ((System.currentTimeMillis() / 3_600_000L) % list.size());
            main.post(new Loaded(list, at < 0 ? null : load(app, list.get(at)), at, ready));
        }
    }

    private final class Loaded implements Runnable {
        private final List<Shot> list;
        private final Shot next;
        private final int index;
        private final Runnable ready;

        Loaded(List<Shot> list, Shot next, int index, Runnable ready) {
            this.list = list;
            this.next = next;
            this.index = index;
            this.ready = ready;
        }

        @Override public void run() {
            shots = list;
            loading = false;
            if (next == null) return;
            wanted = index;
            show(next);
            ready.run();
        }
    }

    private void show(Shot next) {
        if (previous != null) release(previous);
        previous = shot;
        shot = next;
        ink(next);
        fadeAt = previous == null ? 0 : SystemClock.uptimeMillis();
    }

    /** True while the new photo fades in (the caller redraws the background until then). */
    boolean fading(long now) {
        if (fadeAt == 0) return false;
        if (now - fadeAt < FADE_MS) return true;
        fadeAt = 0;
        if (previous != null) {
            release(previous);
            previous = null;
        }
        return true;   // one last redraw without the old photo
    }

    void release() {
        if (shot != null) release(shot);
        if (previous != null) release(previous);
        shot = previous = null;
        wanted = -1;
    }

    private static void release(Shot s) {
        if (s.photo != null) s.photo.recycle();
        if (s.front != null) s.front.recycle();
        s.photo = s.front = null;
    }

    private void ink(Shot s) {
        if (clock == null) return;
        boolean dark = Color.luminance(s.ink) < 0.5f;
        clock.setColor(s.ink);
        date.setColor(Ui.alpha(s.ink, 0.92f));
        // A soft shadow lifts white digits off a busy photo; dark digits sit on a bright sky and need none.
        if (dark) {
            clock.clearShadowLayer();
            date.clearShadowLayer();
        } else {
            clock.setShadowLayer(40 * k, 0, 4 * k, 0x47000000);
            date.setShadowLayer(16 * k, 0, 2 * k, 0x59000000);
        }
    }

    private static List<Shot> read(Context c) {
        List<Shot> list = new ArrayList<>();
        try (InputStream in = c.getAssets().open("wallpapers/index.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\\|");
                if (f.length < 8) continue;
                Shot s = new Shot();
                s.id = f[0];
                s.cx = Float.parseFloat(f[1]);
                s.base = Float.parseFloat(f[2]);
                s.ink = (int) Long.parseLong(f[3], 16);
                s.frontLeft = Float.parseFloat(f[4]);
                s.frontTop = Float.parseFloat(f[5]);
                s.line = f[6] + "  ·  " + f[7];
                list.add(s);
            }
        } catch (Exception e) {
            AppLog.w("wallpaper index: " + e);
        }
        return list;
    }

    private static Shot load(Context c, Shot s) {
        Shot out = new Shot();
        out.id = s.id;
        out.line = s.line;
        out.cx = s.cx;
        out.base = s.base;
        out.ink = s.ink;
        out.frontLeft = s.frontLeft;
        out.frontTop = s.frontTop;
        try (InputStream photo = c.getAssets().open("wallpapers/" + s.id + ".jpg");
             InputStream front = c.getAssets().open("wallpapers/" + s.id + "-front.png")) {
            BitmapFactory.Options opaque = new BitmapFactory.Options();
            opaque.inPreferredConfig = Bitmap.Config.RGB_565;   // half the memory; the photo has no transparency
            out.photo = BitmapFactory.decodeStream(photo, null, opaque);
            out.front = BitmapFactory.decodeStream(front);
        } catch (Exception e) {
            AppLog.w("wallpaper " + s.id + ": " + e);
        }
        if (out.photo == null || out.front == null) {
            release(out);
            return null;
        }
        return out;
    }

    /** Background layer: the photo, the date, the clock, and the scene in front of the clock. */
    void background(Canvas c, Context context, String time) {
        if (shot == null) {
            c.drawRect(bounds, backdrop);
            return;
        }
        float e = fadeAt == 0 ? 1 : Ui.clamp((SystemClock.uptimeMillis() - fadeAt) / (float) FADE_MS);
        if (previous != null && e < 1) drawShot(c, context, time, previous, 1);
        drawShot(c, context, time, shot, e);
        image.setAlpha(255);
        c.drawText(shot.line, 80 * k, 1040 * k, caption);
    }

    private void drawShot(Canvas c, Context context, String time, Shot s, float alpha) {
        int a = Math.round(255 * alpha);
        image.setAlpha(a);
        c.drawBitmap(s.photo, null, bounds, image);
        if (s != shot) ink(s);
        long now = System.currentTimeMillis();
        long today = now / 60_000;
        if (today != day) {
            dateText = Ui.date(now);
            dateText = Character.toUpperCase(dateText.charAt(0)) + dateText.substring(1);
            day = today;
        }
        clock.setAlpha(a);
        date.setAlpha(Math.round(a * 0.92f));
        float cap = Ui.cap(clock);
        c.drawText(dateText, s.cx * k, s.base * k - cap - 40 * k, date);
        c.drawText(time, s.cx * k, s.base * k, clock);
        float scale = bounds.width() / PHOTO_W;
        art.set(s.frontLeft * scale, s.frontTop * scale,
                (s.frontLeft + s.front.getWidth()) * scale, (s.frontTop + s.front.getHeight()) * scale);
        c.drawBitmap(s.front, null, art, image);
        if (s != shot) ink(shot);
    }

    /** Above the dock: the focused app's name and the way to the inputs (hidden while idle, with the dock). */
    void hero(Canvas c, Context context, String time, HomeApps.Item item, boolean focused) {
        if (clock == null || !focused) return;
        String name = item == null ? Ui.tr("Aplicativos", "Apps") : item.label;
        if (!name.equals(cachedLabel)) {
            fittedLabel = TextUtils.ellipsize(name, new android.text.TextPaint(label), 900 * k,
                    TextUtils.TruncateAt.END).toString();
            cachedLabel = name;
        }
        c.drawText(fittedLabel, 960 * k, 770 * k, label);
        c.drawText(Ui.tr("↓ Entradas e atalhos", "↓ Inputs & shortcuts"), 1840 * k, 770 * k, hint);
    }
}

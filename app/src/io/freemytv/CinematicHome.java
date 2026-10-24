package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;

/** Quiet, static home. No wallpaper bitmap, texture, blur or animated background. */
final class CinematicHome {
    static final int ACCENT = 0xFFD8D4B8;
    private final Paint backdrop = new Paint();
    private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF(), art = new RectF();
    private Paint clock, detail;
    private TextPaint title;
    private float k;
    private String cachedLabel, fittedLabel, date = "";
    private long day = -1;

    void resize(float scale, int width, int height) {
        k = scale;
        bounds.set(0, 0, width, height);
        backdrop.setShader(new LinearGradient(0, 0, width, height,
                new int[] {0xFF101919, 0xFF172523, 0xFF0C1112}, null, Shader.TileMode.CLAMP));
        clock = Ui.numbers(Ui.regular, 46 * k, 0xFFF1F0E8);
        title = new TextPaint(Ui.text(Ui.displaySemibold, 100 * k, 0xFFF1F0E8));
        detail = Ui.text(Ui.regular, 27 * k, 0xFFACB7B2);
        cachedLabel = null;
    }

    void background(Canvas c) {
        c.drawRect(bounds, backdrop);
        // A restrained architectural panel; drawn directly, no full-screen image allocation.
        panel.setColor(0xFF213330);
        art.set(1230 * k, 230 * k, 1836 * k, 700 * k);
        c.drawRoundRect(art, 12 * k, 12 * k, panel);
        panel.setColor(0xFF768578);
        c.drawRect(1230 * k, 230 * k, 1234 * k, 700 * k, panel);
    }

    void hero(Canvas c, Context context, String time, HomeApps.Item item, boolean focused) {
        if (clock == null) return;
        long now = System.currentTimeMillis();
        long today = now / 60_000; // Also respects timezone/date changes on the next clock tick.
        if (today != day) {
            date = android.text.format.DateFormat.getMediumDateFormat(context).format(new java.util.Date(now));
            day = today;
        }
        c.drawText(time, 84 * k, 110 * k, clock);
        c.drawText(date, 84 * k, 158 * k, detail);
        String label = item == null ? Ui.tr("Aplicativos", "Apps") : item.label;
        if (!label.equals(cachedLabel)) {
            fittedLabel = TextUtils.ellipsize(label, title, 1070 * k, TextUtils.TruncateAt.END).toString();
            cachedLabel = label;
        }
        c.drawText(fittedLabel, 80 * k, 506 * k, title);
        if (focused) c.drawText(Ui.tr("OK para abrir", "OK to open"), 84 * k, 566 * k, detail);
        Bitmap logo = item == null ? null : item.logo;
        if (logo != null) {
            float fit = Math.min(350 * k / logo.getWidth(), 180 * k / logo.getHeight());
            float width = logo.getWidth() * fit, height = logo.getHeight() * fit;
            art.set(1533 * k - width / 2, 465 * k - height / 2,
                    1533 * k + width / 2, 465 * k + height / 2);
            c.drawBitmap(logo, null, art, image);
        } else {
            Symbols.draw(context, c, item != null && item.symbol != null ? item.symbol : "apps",
                    1533 * k, 465 * k, 120 * k, ACCENT);
        }
        c.drawText(Ui.tr("Aplicativos", "Apps"), 84 * k, 779 * k, detail);
        detail.setTextAlign(Paint.Align.RIGHT);
        c.drawText(Ui.tr("↓ Entradas e atalhos", "↓ Inputs & shortcuts"), 1836 * k, 779 * k, detail);
        detail.setTextAlign(Paint.Align.LEFT);
    }
}

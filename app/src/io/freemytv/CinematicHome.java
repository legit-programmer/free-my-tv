package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;

/** Static editorial home: reusable paints, a 16 KiB grain tile, and existing app artwork. */
final class CinematicHome {
    private final Paint backdrop = new Paint(), glow = new Paint(), grain = new Paint();
    private final Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF(), art = new RectF();
    private Paint clock, eyebrow, detail;
    private TextPaint title;
    private float k;
    private String cachedLabel, fittedLabel;
    private static final int ACCENT = 0xFFC9AB7A;

    CinematicHome() {
        // Deterministic, tiled grain: generated once, never animated or fetched.
        int[] pixels = new int[64 * 64];
        java.util.Random random = new java.util.Random(19);
        for (int i = 0; i < pixels.length; i++) pixels[i] = (random.nextInt(7) << 24) | 0x00FFFFFF;
        Bitmap tile = Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888);
        grain.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    void resize(float scale, int width, int height) {
        k = scale;
        bounds.set(0, 0, width, height);
        backdrop.setShader(new LinearGradient(0, 0, width, height,
                new int[] {0xFF111820, 0xFF101518, 0xFF070A0D}, null, Shader.TileMode.CLAMP));
        glow.setShader(new RadialGradient(1490 * k, 370 * k, 740 * k,
                new int[] {0x303D918E, 0x102D6868, 0x002D6868}, null, Shader.TileMode.CLAMP));
        clock = Ui.numbers(Ui.thin, 164 * k, 0xFFF1EDE5);
        eyebrow = Ui.text(Ui.medium, 22 * k, ACCENT);
        eyebrow.setLetterSpacing(0.18f);
        title = new TextPaint(Ui.text(Ui.displaySemibold, 84 * k, 0xFFF1EDE5));
        detail = Ui.text(Ui.regular, 25 * k, 0xFFA7AFB3);
        cachedLabel = null;
    }

    void background(Canvas c) {
        c.drawRect(bounds, backdrop);
        c.drawRect(bounds, glow);
        c.drawRect(bounds, grain);
    }

    void hero(Canvas c, Context context, String time, HomeApps.Item item, boolean focused) {
        if (clock == null) return;
        c.drawText("FREE MY TV", 84 * k, 108 * k, eyebrow);
        rule.setColor(ACCENT);
        c.drawRect(84 * k, 258 * k, 136 * k, 262 * k, rule);
        c.drawText(Ui.tr("SEU TEMPO. SUA TELA.", "YOUR TIME. YOUR SCREEN."), 84 * k, 309 * k, eyebrow);
        // Fit even a wide 12-hour clock without crossing the asymmetric divider.
        float clockScale = Math.min(1, 650 * k / Math.max(1, clock.measureText(time)));
        c.save();
        c.translate(80 * k, 492 * k);
        c.scale(clockScale, clockScale);
        c.drawText(time, 0, 0, clock);
        c.restore();
        c.drawText(Ui.tr("Escolha algo que vale seu tempo.", "Make room for something good."), 84 * k, 558 * k, detail);
        rule.setColor(0xFF343C40);
        c.drawRect(810 * k, 280 * k, 811 * k, 674 * k, rule);

        String label = item == null ? Ui.tr("Sua tela", "Your screen") : item.label;
        if (!label.equals(cachedLabel)) {
            fittedLabel = TextUtils.ellipsize(label, title, 860 * k, TextUtils.TruncateAt.END).toString();
            cachedLabel = label;
        }
        c.drawText(Ui.tr("EM DESTAQUE", "IN THE SPOTLIGHT"), 922 * k, 302 * k, eyebrow);
        Bitmap logo = item == null ? null : item.logo;
        if (logo != null) {
            float fit = Math.min(210 * k / logo.getWidth(), 100 * k / logo.getHeight());
            art.set(926 * k, 355 * k, 926 * k + logo.getWidth() * fit, 355 * k + logo.getHeight() * fit);
            c.drawBitmap(logo, null, art, image);
        } else {
            Symbols.draw(context, c, item != null && item.symbol != null ? item.symbol : "apps",
                    969 * k, 403 * k, 76 * k, ACCENT);
        }
        c.drawText(fittedLabel, 920 * k, 570 * k, title);
        rule.setColor(focused ? ACCENT : 0xFF788287);
        c.drawCircle(930 * k, 624 * k, 4 * k, rule);
        c.drawText(focused ? Ui.tr("OK para abrir", "Press OK to open") : Ui.tr("Seus aplicativos, prontos", "Your apps, ready when you are"),
                950 * k, 633 * k, detail);
        c.drawText(Ui.tr("SEUS APLICATIVOS", "YOUR APPS"), 84 * k, 779 * k, eyebrow);
        detail.setTextAlign(Paint.Align.RIGHT);
        c.drawText(Ui.tr("↑ Atalhos    ↓ Mais", "↑ Shortcuts    ↓ More"), 1836 * k, 779 * k, detail);
        detail.setTextAlign(Paint.Align.LEFT);
    }
}

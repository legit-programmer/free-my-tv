package io.freemytv;

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.os.Build;

/** Cached drawing commands, without allocating a full-screen bitmap on Android 9. */
abstract class DisplayLayer {
    static DisplayLayer create(String name) {
        return Build.VERSION.SDK_INT >= 29 ? new Api29(name) : new Legacy();
    }

    abstract Canvas beginRecording(int width, int height);
    abstract void endRecording();
    abstract void setPosition(int left, int top, int right, int bottom);
    abstract void setUseCompositingLayer(boolean enabled);
    abstract void setAlpha(float alpha);
    abstract void setTranslationX(float x);
    abstract void setTranslationY(float y);
    abstract void draw(Canvas canvas);
    abstract void discard();

    private static final class Legacy extends DisplayLayer {
        private Picture picture = new Picture();
        private int width, height;
        private float alpha = 1, x, y;
        private boolean recorded;

        @Override Canvas beginRecording(int w, int h) {
            width = Math.max(1, w);
            height = Math.max(1, h);
            return picture.beginRecording(width, height);
        }
        @Override void endRecording() { picture.endRecording(); recorded = true; }
        @Override void setPosition(int l, int t, int r, int b) { width = r - l; height = b - t; }
        @Override void setUseCompositingLayer(boolean enabled) { }
        @Override void setAlpha(float value) { alpha = Math.max(0, Math.min(1, value)); }
        @Override void setTranslationX(float value) { x = value; }
        @Override void setTranslationY(float value) { y = value; }
        @Override void draw(Canvas c) {
            if (!recorded || alpha <= 0) return;
            int save = c.save();
            c.translate(x, y);
            if (alpha < 1) c.saveLayerAlpha(0, 0, width, height, Math.round(alpha * 255));
            c.drawPicture(picture);
            c.restoreToCount(save);
        }
        @Override void discard() { picture = new Picture(); recorded = false; }
    }

    /** Kept in a separate class so API 28 never resolves RenderNode/RecordingCanvas. */
    @TargetApi(29)
    private static final class Api29 extends DisplayLayer {
        private final android.graphics.RenderNode node;
        Api29(String name) { node = new android.graphics.RenderNode(name); }
        @Override Canvas beginRecording(int w, int h) { return node.beginRecording(w, h); }
        @Override void endRecording() { node.endRecording(); }
        @Override void setPosition(int l, int t, int r, int b) { node.setPosition(l, t, r, b); }
        @Override void setUseCompositingLayer(boolean enabled) { node.setUseCompositingLayer(enabled, null); }
        @Override void setAlpha(float value) { node.setAlpha(value); }
        @Override void setTranslationX(float value) { node.setTranslationX(value); }
        @Override void setTranslationY(float value) { node.setTranslationY(value); }
        @Override void draw(Canvas canvas) { canvas.drawRenderNode(node); }
        @Override void discard() { node.discardDisplayList(); }
    }
}

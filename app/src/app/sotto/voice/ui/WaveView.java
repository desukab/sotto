package app.sotto.voice.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Scrolling microphone-level waveform. Levels come from the recognizer's RMS
 * callback, so the motion is real rather than decorative.
 */
public final class WaveView extends View {

    private static final int COUNT = 48;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels = new float[COUNT];
    private int head;
    private boolean active = true;

    public WaveView(Context c) {
        super(c);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Ui.ACCENT);
    }

    public void setBarColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void setActive(boolean value) {
        active = value;
        invalidate();
    }

    public void push(float level) {
        float v = Math.max(0.03f, Math.min(1f, level));
        levels[head] = v;
        head = (head + 1) % COUNT;
        invalidate();
    }

    public void clear() {
        for (int i = 0; i < COUNT; i++) levels[i] = 0f;
        head = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float step = w / COUNT;
        float barWidth = Math.max(1.5f, step * 0.45f);
        paint.setStrokeWidth(barWidth);
        float mid = h / 2f;
        for (int i = 0; i < COUNT; i++) {
            float level = levels[(head + i) % COUNT];
            if (!active && level == 0f) level = 0.06f;
            float half = Math.max(barWidth, mid * level * 0.92f);
            float x = i * step + step / 2f;
            int alpha = 60 + Math.round(195 * level);
            paint.setAlpha(Math.min(255, alpha));
            canvas.drawLine(x, mid - half, x, mid + half, paint);
        }
    }
}

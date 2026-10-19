package app.sotto.voice.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * The hero visual: a soft breathing orb. When listening it pulses with the
 * microphone level supplied by the service.
 */
public final class OrbView extends View {

    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase;
    private boolean listening;
    private float level;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            phase += listening ? 0.035f : 0.008f;
            if (phase > 6.283f) phase -= 6.283f;
            invalidate();
            postInvalidateOnAnimation();
            postDelayed(this, 16);
        }
    };

    public OrbView(Context c) {
        super(c);
        core.setColor(Ui.ACCENT);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(c, 1.2f));
        ring.setColor(Ui.alpha(Ui.ACCENT, 0.45f));
    }

    public void setListening(boolean value) {
        listening = value;
        invalidate();
    }

    public boolean isListening() {
        return listening;
    }

    public void setLevel(float value) {
        level = Math.max(0f, Math.min(1f, value));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float base = Math.min(w, h) * 0.5f;

        float breathe = (float) (0.5f + 0.5f * Math.sin(phase));
        float pulse = listening ? (0.12f + level * 0.3f) : 0.06f;
        float radius = base * (0.52f + pulse * breathe);

        glow.setShader(new RadialGradient(cx, cy, radius * 1.7f,
                new int[]{Ui.alpha(Ui.ACCENT, listening ? 0.30f : 0.16f),
                        Ui.alpha(Ui.ACCENT, 0.05f), 0x00000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius * 1.7f, glow);

        core.setColor(listening ? Ui.ACCENT : 0xFF1E2A18);
        canvas.drawCircle(cx, cy, radius, core);

        ring.setColor(Ui.alpha(Ui.ACCENT, listening ? 0.7f : 0.25f));
        canvas.drawCircle(cx, cy, radius * 1.22f, ring);
        canvas.drawCircle(cx, cy, radius * 1.42f, ring);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(listening ? 0xFF07120A : Ui.ACCENT);
        float glyph = radius * 0.5f;
        canvas.drawRoundRect(cx - glyph * 0.22f, cy - glyph * 0.62f,
                cx + glyph * 0.22f, cy + glyph * 0.15f, glyph * 0.24f, glyph * 0.24f, dot);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(glyph * 0.12f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(listening ? 0xFF07120A : Ui.ACCENT);
        canvas.drawArc(cx - glyph * 0.46f, cy - glyph * 0.18f, cx + glyph * 0.46f,
                cy + glyph * 0.52f, 0, 180, false, stroke);
        canvas.drawLine(cx, cy + glyph * 0.52f, cx, cy + glyph * 0.72f, stroke);
    }
}

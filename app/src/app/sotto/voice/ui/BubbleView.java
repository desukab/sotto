package app.sotto.voice.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * The floating bubble. A dark glass dot with a lime halo that becomes a live
 * waveform while dictating.
 */
public final class BubbleView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bars = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean listening;
    private float[] levels = new float[]{0.2f, 0.4f, 0.25f, 0.5f, 0.3f};
    private float phase;

    public BubbleView(Context c) {
        super(c);
        fill.setColor(0xFF11150F);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Ui.dp(c, 1.4f));
        stroke.setColor(Ui.alpha(Ui.ACCENT, 0.55f));
        bars.setStyle(Paint.Style.STROKE);
        bars.setStrokeCap(Paint.Cap.ROUND);
        bars.setStrokeWidth(Ui.dp(c, 2.2f));
        bars.setColor(Ui.ACCENT);
    }

    public void setListening(boolean value) {
        listening = value;
        invalidate();
    }

    public boolean isListening() {
        return listening;
    }

    public void setLevel(float value) {
        float v = Math.max(0.08f, Math.min(1f, value));
        levels = new float[]{
                v * 0.4f, v * 0.85f, v * 0.55f, v, v * 0.6f
        };
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) * 0.34f;

        if (listening) {
            phase += 0.08f;
            halo.setShader(new RadialGradient(cx, cy, r * 2.1f,
                    new int[]{Ui.alpha(Ui.ACCENT, 0.22f), 0x00000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 2.1f, halo);
        }

        canvas.drawCircle(cx, cy, r, fill);
        canvas.drawCircle(cx, cy, r, stroke);

        if (listening) {
            for (int i = 0; i < levels.length; i++) {
                float x = cx + (i - 2) * r * 0.36f;
                float amp = (float) (levels[i] * (0.6f + 0.4f * Math.sin(phase + i)));
                canvas.drawLine(x, cy - r * 0.42f * amp, x, cy + r * 0.42f * amp, bars);
            }
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Ui.ACCENT);
            canvas.drawRoundRect(cx - r * 0.16f, cy - r * 0.42f, cx + r * 0.16f,
                    cy + r * 0.06f, r * 0.18f, r * 0.18f, p);
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setStyle(Paint.Style.STROKE);
            s.setStrokeWidth(r * 0.11f);
            s.setStrokeCap(Paint.Cap.ROUND);
            s.setColor(Ui.ACCENT);
            canvas.drawArc(cx - r * 0.32f, cy - r * 0.16f, cx + r * 0.32f,
                    cy + r * 0.42f, 0, 180, false, s);
            canvas.drawLine(cx, cy + r * 0.42f, cx, cy + r * 0.6f, s);
        }
    }
}

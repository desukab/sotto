package app.sotto.voice.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Tiny stroke icon set drawn with Canvas. Keeping icons as code avoids a
 * vector-drawable dependency and keeps the APK small.
 */
public final class IconView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final String name;
    private int color;

    public IconView(Context c, String name) {
        super(c);
        this.name = name == null ? "mic" : name;
        this.color = Ui.TEXT;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Ui.dp(c, 1.7f));
    }

    public IconView setColor(int value) {
        this.color = value;
        invalidate();
        return this;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float s = Math.min(w, h);
        float cx = w / 2f;
        float cy = h / 2f;
        paint.setColor(color);
        paint.setStrokeWidth(Ui.dp(getContext(), 1.7f));
        float r = s * 0.32f;
        path.reset();

        if ("mic".equals(name)) {
            RectF body = new RectF(cx - s * 0.11f, cy - s * 0.34f, cx + s * 0.11f, cy + s * 0.06f);
            path.addRoundRect(body, s * 0.12f, s * 0.12f, Path.Direction.CW);
            canvas.drawPath(path, paint);
            canvas.drawArc(new RectF(cx - s * 0.24f, cy - s * 0.10f, cx + s * 0.24f,
                    cy + s * 0.28f), 0, 180, false, paint);
            canvas.drawLine(cx, cy + s * 0.28f, cx, cy + s * 0.38f, paint);
            canvas.drawLine(cx - s * 0.12f, cy + s * 0.38f, cx + s * 0.12f, cy + s * 0.38f, paint);
        } else if ("stop".equals(name)) {
            canvas.drawRoundRect(new RectF(cx - s * 0.18f, cy - s * 0.18f,
                    cx + s * 0.18f, cy + s * 0.18f), s * 0.06f, s * 0.06f, paint);
        } else if ("home".equals(name)) {
            path.moveTo(cx - s * 0.3f, cy - s * 0.02f);
            path.lineTo(cx, cy - s * 0.3f);
            path.lineTo(cx + s * 0.3f, cy - s * 0.02f);
            canvas.drawPath(path, paint);
            path.reset();
            path.moveTo(cx - s * 0.22f, cy - s * 0.06f);
            path.lineTo(cx - s * 0.22f, cy + s * 0.3f);
            path.lineTo(cx + s * 0.22f, cy + s * 0.3f);
            path.lineTo(cx + s * 0.22f, cy - s * 0.06f);
            canvas.drawPath(path, paint);
        } else if ("history".equals(name)) {
            canvas.drawCircle(cx, cy, r, paint);
            path.moveTo(cx, cy - r * 0.55f);
            path.lineTo(cx, cy);
            path.lineTo(cx + r * 0.45f, cy + r * 0.25f);
            canvas.drawPath(path, paint);
        } else if ("book".equals(name)) {
            RectF l = new RectF(cx - s * 0.32f, cy - s * 0.26f, cx - s * 0.02f, cy + s * 0.26f);
            RectF rr = new RectF(cx + s * 0.02f, cy - s * 0.26f, cx + s * 0.32f, cy + s * 0.26f);
            canvas.drawRoundRect(l, s * 0.05f, s * 0.05f, paint);
            canvas.drawRoundRect(rr, s * 0.05f, s * 0.05f, paint);
            canvas.drawLine(cx, cy - s * 0.26f, cx, cy + s * 0.26f, paint);
        } else if ("sliders".equals(name) || "gear".equals(name)) {
            float[] ys = {cy - s * 0.2f, cy, cy + s * 0.2f};
            for (int i = 0; i < ys.length; i++) {
                canvas.drawLine(cx - s * 0.3f, ys[i], cx + s * 0.3f, ys[i], paint);
                canvas.drawCircle(cx + (i == 0 ? -s * 0.12f : i == 1 ? s * 0.1f : -s * 0.02f),
                        ys[i], s * 0.06f, paint);
            }
        } else if ("plus".equals(name)) {
            canvas.drawLine(cx - s * 0.24f, cy, cx + s * 0.24f, cy, paint);
            canvas.drawLine(cx, cy - s * 0.24f, cx, cy + s * 0.24f, paint);
        } else if ("check".equals(name)) {
            path.moveTo(cx - s * 0.26f, cy + s * 0.02f);
            path.lineTo(cx - s * 0.06f, cy + s * 0.22f);
            path.lineTo(cx + s * 0.28f, cy - s * 0.2f);
            canvas.drawPath(path, paint);
        } else if ("copy".equals(name)) {
            canvas.drawRoundRect(new RectF(cx - s * 0.25f, cy - s * 0.25f,
                    cx + s * 0.14f, cy + s * 0.14f), s * 0.05f, s * 0.05f, paint);
            canvas.drawRoundRect(new RectF(cx - s * 0.09f, cy - s * 0.09f,
                    cx + s * 0.3f, cy + s * 0.3f), s * 0.05f, s * 0.05f, paint);
        } else if ("trash".equals(name)) {
            canvas.drawLine(cx - s * 0.24f, cy - s * 0.18f, cx + s * 0.24f, cy - s * 0.18f, paint);
            canvas.drawLine(cx - s * 0.1f, cy - s * 0.26f, cx + s * 0.1f, cy - s * 0.26f, paint);
            path.moveTo(cx - s * 0.18f, cy - s * 0.16f);
            path.lineTo(cx - s * 0.14f, cy + s * 0.28f);
            path.lineTo(cx + s * 0.14f, cy + s * 0.28f);
            path.lineTo(cx + s * 0.18f, cy - s * 0.16f);
            canvas.drawPath(path, paint);
        } else if ("close".equals(name)) {
            canvas.drawLine(cx - s * 0.2f, cy - s * 0.2f, cx + s * 0.2f, cy + s * 0.2f, paint);
            canvas.drawLine(cx + s * 0.2f, cy - s * 0.2f, cx - s * 0.2f, cy + s * 0.2f, paint);
        } else if ("chevron".equals(name)) {
            path.moveTo(cx - s * 0.06f, cy - s * 0.18f);
            path.lineTo(cx + s * 0.12f, cy);
            path.lineTo(cx - s * 0.06f, cy + s * 0.18f);
            canvas.drawPath(path, paint);
        } else if ("shield".equals(name)) {
            path.moveTo(cx, cy - s * 0.3f);
            path.lineTo(cx + s * 0.24f, cy - s * 0.18f);
            path.lineTo(cx + s * 0.2f, cy + s * 0.14f);
            path.quadTo(cx, cy + s * 0.34f, cx - s * 0.2f, cy + s * 0.14f);
            path.lineTo(cx - s * 0.24f, cy - s * 0.18f);
            path.close();
            canvas.drawPath(path, paint);
        } else if ("bolt".equals(name)) {
            path.moveTo(cx + s * 0.08f, cy - s * 0.32f);
            path.lineTo(cx - s * 0.2f, cy + s * 0.04f);
            path.lineTo(cx + s * 0.02f, cy + s * 0.04f);
            path.lineTo(cx - s * 0.08f, cy + s * 0.32f);
            path.lineTo(cx + s * 0.22f, cy - s * 0.06f);
            path.lineTo(cx, cy - s * 0.06f);
            path.close();
            canvas.drawPath(path, paint);
        } else if ("globe".equals(name)) {
            canvas.drawCircle(cx, cy, r, paint);
            canvas.drawOval(new RectF(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r), paint);
            canvas.drawLine(cx - r, cy, cx + r, cy, paint);
        } else if ("send".equals(name)) {
            path.moveTo(cx - s * 0.28f, cy - s * 0.26f);
            path.lineTo(cx + s * 0.3f, cy);
            path.lineTo(cx - s * 0.28f, cy + s * 0.26f);
            path.lineTo(cx - s * 0.18f, cy);
            path.close();
            canvas.drawPath(path, paint);
        } else if ("undo".equals(name)) {
            canvas.drawArc(new RectF(cx - s * 0.28f, cy - s * 0.2f, cx + s * 0.16f,
                    cy + s * 0.24f), 200, 160, false, paint);
            path.moveTo(cx - s * 0.28f, cy - s * 0.18f);
            path.lineTo(cx - s * 0.16f, cy + s * 0.02f);
            path.lineTo(cx - s * 0.04f, cy - s * 0.14f);
            canvas.drawPath(path, paint);
        } else if ("keyboard".equals(name)) {
            canvas.drawRoundRect(new RectF(cx - s * 0.32f, cy - s * 0.22f,
                    cx + s * 0.32f, cy + s * 0.22f), s * 0.06f, s * 0.06f, paint);
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    canvas.drawPoint(cx + i * s * 0.16f, cy + j * s * 0.1f, paint);
                }
            }
        } else if ("wave".equals(name)) {
            float[] amps = {0.1f, 0.24f, 0.16f, 0.3f, 0.12f};
            for (int i = 0; i < amps.length; i++) {
                float x = cx + (i - 2) * s * 0.11f;
                canvas.drawLine(x, cy - s * amps[i], x, cy + s * amps[i], paint);
            }
        } else if ("sparkle".equals(name)) {
            path.moveTo(cx, cy - s * 0.3f);
            path.lineTo(cx + s * 0.07f, cy - s * 0.07f);
            path.lineTo(cx + s * 0.3f, cy);
            path.lineTo(cx + s * 0.07f, cy + s * 0.07f);
            path.lineTo(cx, cy + s * 0.3f);
            path.lineTo(cx - s * 0.07f, cy + s * 0.07f);
            path.lineTo(cx - s * 0.3f, cy);
            path.lineTo(cx - s * 0.07f, cy - s * 0.07f);
            path.close();
            canvas.drawPath(path, paint);
        } else {
            canvas.drawCircle(cx, cy, r, paint);
        }
    }
}

package app.sotto.voice.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Small design system. Everything is built from code so the APK stays tiny and
 * the look is identical on every device.
 */
public final class Ui {

    public static final int BG = 0xFF07080A;
    public static final int SURFACE = 0xFF121418;
    public static final int SURFACE2 = 0xFF1A1D22;
    public static final int STROKE = 0xFF242830;
    public static final int TEXT = 0xFFF4F6F8;
    public static final int MUTED = 0xFF98A2AE;
    public static final int ACCENT = 0xFFB8FF5C;
    public static final int ACCENT2 = 0xFF6EE7FF;
    public static final int VIOLET = 0xFFA78BFA;
    public static final int DANGER = 0xFFFF6B6B;

    private Ui() {
    }

    public static float dp(Context c, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics());
    }

    public static int dpi(Context c, float value) {
        return Math.round(dp(c, value));
    }

    public static int alpha(int color, float amount) {
        int a = Math.round(255f * Math.max(0f, Math.min(1f, amount)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static float sp(Context c, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                c.getResources().getDisplayMetrics());
    }

    public static TextView text(Context c, String value, float sizeSp, int color) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        t.setLineSpacing(dp(c, 3), 1f);
        return t;
    }

    public static TextView bold(Context c, String value, float sizeSp, int color) {
        TextView t = text(c, value, sizeSp, color);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView mono(Context c, String value, float sizeSp, int color) {
        TextView t = text(c, value, sizeSp, color);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static View spacer(Context c, float heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dpi(c, heightDp)));
        return v;
    }

    public static View gap(Context c, float widthDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dpi(c, widthDp), 1));
        return v;
    }

    public static android.graphics.drawable.GradientDrawable round(int color, float radiusDp, Context c) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static android.graphics.drawable.GradientDrawable stroked(int color, int stroke,
                                                                   float radiusDp, Context c) {
        android.graphics.drawable.GradientDrawable d = round(color, radiusDp, c);
        d.setStroke(Math.max(1, dpi(c, 1)), stroke);
        return d;
    }

    public static android.graphics.drawable.GradientDrawable gradient(int from, int to,
                                                                     float radiusDp, Context c) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{from, to});
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static android.graphics.drawable.Drawable ripple(android.graphics.drawable.Drawable base) {
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF), base, null);
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setPadding(dpi(c, 18), dpi(c, 18), dpi(c, 18), dpi(c, 18));
        l.setBackground(stroked(SURFACE, STROKE, 20, c));
        return l;
    }

    public static TextView button(Context c, String label, int bg, int fg, float radiusDp) {
        TextView t = bold(c, label, 14, fg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dpi(c, 18), dpi(c, 13), dpi(c, 18), dpi(c, 13));
        t.setBackground(ripple(round(bg, radiusDp, c)));
        t.setClickable(true);
        return t;
    }

    public static TextView primary(Context c, String label) {
        TextView t = bold(c, label, 15, 0xFF07120A);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dpi(c, 20), dpi(c, 15), dpi(c, 20), dpi(c, 15));
        t.setBackground(ripple(gradient(ACCENT, 0xFF86E84A, 16, c)));
        t.setClickable(true);
        return t;
    }

    public static TextView ghost(Context c, String label) {
        return button(c, label, SURFACE2, TEXT, 14);
    }

    public static TextView pill(Context c, String label, int color) {
        TextView t = bold(c, label, 10, color);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(0.12f);
        t.setPadding(dpi(c, 10), dpi(c, 5), dpi(c, 10), dpi(c, 5));
        t.setBackground(round(alpha(color, 0.12f), 999, c));
        return t;
    }

    public static TextView chip(Context c, String label) {
        TextView t = bold(c, label, 13, TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dpi(c, 16), dpi(c, 9), dpi(c, 16), dpi(c, 9));
        t.setBackground(ripple(stroked(SURFACE, STROKE, 999, c)));
        t.setClickable(true);
        return t;
    }

    public static void selectedChip(Context c, TextView t, boolean selected) {
        if (selected) {
            t.setTextColor(0xFF07120A);
            t.setBackground(round(ACCENT, 999, c));
        } else {
            t.setTextColor(TEXT);
            t.setBackground(ripple(stroked(SURFACE, STROKE, 999, c)));
        }
    }

    public static TextView sectionLabel(Context c, String label) {
        TextView t = bold(c, label.toUpperCase(), 10, MUTED);
        t.setLetterSpacing(0.18f);
        t.setPadding(0, 0, 0, dpi(c, 8));
        return t;
    }

    public static TextView divider(Context c) {
        TextView v = new TextView(c);
        v.setBackgroundColor(STROKE);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dpi(c, 1))));
        return v;
    }

    public static LinearLayout navItem(Context c, String icon, String label) {
        LinearLayout l = column(c);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dpi(c, 8), 0, dpi(c, 6));
        IconView iv = new IconView(c, icon);
        iv.setColor(MUTED);
        l.addView(iv, new LinearLayout.LayoutParams(dpi(c, 22), dpi(c, 22)));
        TextView t = text(c, label, 10, MUTED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dpi(c, 3), 0, 0);
        l.addView(t);
        l.setTag(t);
        return l;
    }

    public static void navSelected(LinearLayout item, boolean selected) {
        TextView label = (TextView) item.getTag();
        for (int i = 0; i < item.getChildCount(); i++) {
            View v = item.getChildAt(i);
            if (v instanceof IconView) {
                ((IconView) v).setColor(selected ? ACCENT : MUTED);
            }
        }
        if (label != null) label.setTextColor(selected ? TEXT : MUTED);
    }

    public static void addPair(LinearLayout parent, View left, View right) {
        LinearLayout row = row(parent.getContext());
        row.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(right);
        parent.addView(row);
    }

    public static Path roundedRect(RectF r, float radius, Path p) {
        p.reset();
        p.addRoundRect(r, radius, radius, Path.Direction.CW);
        return p;
    }

    public static void setMargins(View v, Context c, float left, float top, float right, float bottom) {
        ViewGroup.LayoutParams raw = v.getLayoutParams();
        if (raw instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
            lp.setMargins(dpi(c, left), dpi(c, top), dpi(c, right), dpi(c, bottom));
            v.setLayoutParams(lp);
        }
    }

    public static void styleSwitch(android.widget.Switch s, Context c) {
        s.setButtonTintList(null);
        s.setTrackTintList(android.content.res.ColorStateList.valueOf(0xFF2A2F38));
        s.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFB8FF5C));
    }

    public static void drawDivider(Canvas canvas, Paint paint, float y, int width, int color) {
        paint.setColor(color);
        paint.setStrokeWidth(1f);
        canvas.drawLine(0, y, width, y, paint);
    }
}

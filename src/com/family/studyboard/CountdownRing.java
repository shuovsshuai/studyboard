package com.family.studyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class CountdownRing extends View {

    private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF rect = new RectF();

    private float progress = 0f;
    private String remain = "--:--";
    private String label = "剩余";

    public CountdownRing(Context c) {
        super(c);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(dp(10));
        bgPaint.setColor(Color.parseColor("#1B3350"));

        fgPaint.setStyle(Paint.Style.STROKE);
        fgPaint.setStrokeWidth(dp(10));
        fgPaint.setStrokeCap(Paint.Cap.ROUND);
        fgPaint.setColor(Color.parseColor("#4A90D9"));

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        labelPaint.setColor(Color.parseColor("#6B8BAE"));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setData(float progress, String remain, String label, boolean warn) {
        this.progress = progress;
        this.remain = remain;
        this.label = label;
        fgPaint.setColor(Color.parseColor(warn ? "#FFB443" : "#4A90D9"));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(12);
        float size = Math.min(getWidth(), getHeight()) - pad * 2;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        rect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);

        canvas.drawArc(rect, 0, 360, false, bgPaint);
        canvas.drawArc(rect, -90, 360 * progress, false, fgPaint);

        textPaint.setTextSize(size * 0.26f);
        canvas.drawText(remain, cx, cy + size * 0.09f, textPaint);

        labelPaint.setTextSize(size * 0.11f);
        canvas.drawText(label, cx, cy + size * 0.28f, labelPaint);
    }
}

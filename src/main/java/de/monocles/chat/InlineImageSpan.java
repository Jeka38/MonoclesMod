package de.monocles.chat;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.style.ImageSpan;

public class InlineImageSpan extends ImageSpan {
    private final Paint.FontMetricsInt mTmpFontMetrics = new Paint.FontMetricsInt();
    private final float dHeight;
    private final float dWidth;

    public InlineImageSpan(Drawable d, final String source) {
        super(d.getConstantState() == null ? d : d.getConstantState().newDrawable(), source);
        dHeight = d.getIntrinsicHeight();
        dWidth = d.getIntrinsicWidth();
        if (Build.VERSION.SDK_INT >= 28 && d instanceof AnimatedImageDrawable) {
            ((AnimatedImageDrawable) getDrawable()).start();
        }
    }

    @Override
    public int getSize(final Paint paint, final CharSequence text, final int start, final int end, final Paint.FontMetricsInt fm) {
        paint.getFontMetricsInt(mTmpFontMetrics);
        final int fontHeight = Math.abs(mTmpFontMetrics.descent - mTmpFontMetrics.ascent);
        float mRatio = fontHeight * 1.0f / dHeight;
        int mWidth = (int) (dWidth * mRatio);
        getDrawable().setBounds(0, 0, (int) dWidth, (int) dHeight);
        if (fm != null) {
            fm.ascent = mTmpFontMetrics.ascent;
            fm.descent = mTmpFontMetrics.descent;
            fm.top = mTmpFontMetrics.top;
            fm.bottom = mTmpFontMetrics.bottom;
        }
        return mWidth;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        paint.getFontMetricsInt(mTmpFontMetrics);
        final int fontHeight = Math.abs(mTmpFontMetrics.descent - mTmpFontMetrics.ascent);
        float mRatio = fontHeight * 1.0f / dHeight;

        Drawable b = getDrawable();
        canvas.save();

        int transY = bottom - ((int) (dHeight * mRatio));
        if (mVerticalAlignment == ALIGN_BASELINE) {
            transY -= paint.getFontMetricsInt().descent;
        }

        canvas.translate(x, transY);
        canvas.scale(mRatio, mRatio);
        b.draw(canvas);
        canvas.restore();
    }
}

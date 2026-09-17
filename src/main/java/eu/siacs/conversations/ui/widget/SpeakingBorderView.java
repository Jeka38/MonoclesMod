package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Draws a speaking indicator border for a participant tile: a rounded rectangle around a full video
 * tile or a circle around a circular video thumbnail. Visibility/alpha are driven from
 * {@code MujiConferenceActivity}, the colour is the same green used for the avatar speaking ring.
 */
public class SpeakingBorderView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int circleDiameterPx = 0;

    public SpeakingBorderView(final Context context) {
        this(context, null);
    }

    public SpeakingBorderView(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setBorderColor(final int color) {
        paint.setColor(color);
        invalidate();
    }

    /** A positive value draws a centred circle of that diameter instead of a rounded rectangle. */
    public void setCircleDiameterPx(final int diameter) {
        final int safe = Math.max(0, diameter);
        if (this.circleDiameterPx == safe) {
            return;
        }
        this.circleDiameterPx = safe;
        invalidate();
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        final int width = getWidth();
        final int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        final float stroke = Math.min(Math.max(0.05f * Math.min(width, height), dp(2)), dp(6));
        paint.setStrokeWidth(stroke);
        final float inset = stroke / 2f;
        if (circleDiameterPx > 0) {
            final float radius = Math.min(circleDiameterPx / 2f, Math.min(width, height) / 2f) - inset;
            if (radius <= 0f) {
                return;
            }
            canvas.drawCircle(width / 2f, height / 2f, radius, paint);
        } else {
            rect.set(inset, inset, width - inset, height - inset);
            final float radius = Math.min(dp(12), Math.min(rect.width(), rect.height()) / 2f);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }

    private float dp(final float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}

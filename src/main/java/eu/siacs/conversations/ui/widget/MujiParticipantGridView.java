package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ViewGroup;

/**
 * Simple auto-column grid for Muji conference participant tiles.
 *
 * <p>The column count is derived from the number of tiles (1, 2x2, 3x3, 4x…); every tile gets an
 * equal share of the width and a 16:9 aspect ratio. Meant to be placed inside a {@link
 * android.widget.ScrollView} (wrap_content height).
 */
public class MujiParticipantGridView extends ViewGroup {

    private final int gap;
    private int columns = 1;
    private int childWidth = 0;
    private int childHeight = 0;

    public MujiParticipantGridView(final Context context) {
        this(context, null);
    }

    public MujiParticipantGridView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MujiParticipantGridView(
            final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.gap =
                Math.round(
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                4,
                                getResources().getDisplayMetrics()));
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int count = getChildCount();
        if (count == 0) {
            setMeasuredDimension(width, resolveSize(0, heightMeasureSpec));
            return;
        }
        // Prefer as few columns as possible (i.e. full screen width tiles stacked vertically) and
        // only fall back to a mosaic when the tiles no longer fit the available height.
        int cols = count;
        for (int candidate = 1; candidate <= count; candidate++) {
            final int rows = (count + candidate - 1) / candidate;
            final int tileWidth = (width - gap * (candidate - 1)) / candidate;
            final int tileHeight = Math.round(tileWidth * 9f / 16f);
            final int totalHeight = rows * tileHeight + gap * (rows - 1);
            if (totalHeight <= height || candidate == count) {
                cols = candidate;
                this.childWidth = tileWidth;
                this.childHeight = tileHeight;
                break;
            }
        }
        this.columns = cols;
        final int rows = (count + cols - 1) / cols;
        final int contentHeight = rows * childHeight + gap * (rows - 1);
        final int childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
        final int childHeightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY);
        for (int i = 0; i < count; i++) {
            getChildAt(i).measure(childWidthSpec, childHeightSpec);
        }
        setMeasuredDimension(width, resolveSize(contentHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final int col = i % columns;
            final int row = i / columns;
            final int left = col * (childWidth + gap);
            final int top = row * (childHeight + gap);
            getChildAt(i).layout(left, top, left + childWidth, top + childHeight);
        }
    }
}

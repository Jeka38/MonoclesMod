package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

/**
 * Auto-column grid for Muji conference participant tiles.
 *
 * <p>Normally the column count is derived from the number of tiles (1, 2x2, 3x3, 4x…); every tile
 * gets an equal share of the width and a 16:9 aspect ratio.
 *
 * <p>When {@link #setExpandedChild(View)} is set, that tile fills the whole area while every other
 * tile collapses into a small thumbnail strip stacked vertically on the left edge, overlaying the
 * expanded tile.
 */
public class MujiParticipantGridView extends ViewGroup {

    private final int gap;
    private int columns = 1;
    private int rows = 1;
    private int childWidth = 0;
    private int childHeight = 0;
    @Nullable private View expandedChild = null;
    private int thumbWidth = 0;
    private int thumbHeight = 0;

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

    /** The tile that fills the whole grid, or {@code null} for the regular mosaic layout. */
    @Nullable
    public View getExpandedChild() {
        return expandedChild;
    }

    /** Expands a tile to fill the grid (others shrink to a left-side thumbnail strip). */
    public void setExpandedChild(@Nullable final View child) {
        if (this.expandedChild == child) {
            return;
        }
        // Sanity: the child must actually be one of ours.
        if (child != null && indexOfChild(child) < 0) {
            return;
        }
        this.expandedChild = child;
        requestLayout();
        invalidate();
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
        final View expanded = expandedChild;
        if (expanded != null && indexOfChild(expanded) >= 0) {
            final int density = Math.round(getResources().getDisplayMetrics().density);
            this.thumbWidth = Math.min(Math.round(width * 0.3f), Math.round(180f * density));
            this.thumbHeight = Math.round(thumbWidth * 9f / 16f);
            expanded.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            final int thumbWidthSpec = MeasureSpec.makeMeasureSpec(thumbWidth, MeasureSpec.EXACTLY);
            final int thumbHeightSpec = MeasureSpec.makeMeasureSpec(thumbHeight, MeasureSpec.EXACTLY);
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child != expanded) {
                    child.measure(thumbWidthSpec, thumbHeightSpec);
                }
            }
            setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
            return;
        }
        // Fill the whole grid area. Pick the column count whose cells come closest to the video's
        // 16:9 aspect ratio (so tiles look natural) while using all the available space; a remainder
        // row is stretched to the full width so no empty area is left behind.
        final float targetAspect = 16f / 9f;
        int bestCols = 1;
        int bestRows = count;
        int bestCellWidth = width;
        int bestCellHeight = height / count;
        double bestScore = Double.MAX_VALUE;
        for (int candidate = 1; candidate <= count; candidate++) {
            final int candidateRows = (count + candidate - 1) / candidate;
            final int cellWidth = (width - gap * (candidate - 1)) / candidate;
            final int cellHeight = (height - gap * (candidateRows - 1)) / candidateRows;
            if (cellWidth <= 0 || cellHeight <= 0) {
                continue;
            }
            final double distortion =
                    Math.abs(Math.log((cellWidth / (double) cellHeight) / targetAspect));
            final boolean partialRow = (count % candidate) != 0;
            final double score = distortion + (partialRow ? 0.35d : 0d);
            if (score < bestScore) {
                bestScore = score;
                bestCols = candidate;
                bestRows = candidateRows;
                bestCellWidth = cellWidth;
                bestCellHeight = cellHeight;
            }
        }
        this.columns = bestCols;
        this.rows = bestRows;
        this.childWidth = bestCellWidth;
        this.childHeight = bestCellHeight;
        for (int i = 0; i < count; i++) {
            final int row = i / bestCols;
            final int tilesInRow =
                    row == bestRows - 1 ? (count - (bestRows - 1) * bestCols) : bestCols;
            final int cellWidth =
                    tilesInRow == bestCols
                            ? bestCellWidth
                            : (width - gap * (tilesInRow - 1)) / tilesInRow;
            getChildAt(i)
                    .measure(
                            MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(bestCellHeight, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

@Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
        final int width = r - l;
        final int height = b - t;
        final int count = getChildCount();
        final float density = getResources().getDisplayMetrics().density;
        final float thumbZ = 2f * density;
        final View expanded = expandedChild;
        if (expanded != null && indexOfChild(expanded) >= 0) {
            expanded.layout(0, 0, width, height);
            expanded.setTranslationZ(0);
            int top = 0;
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child != expanded) {
                    child.layout(0, top, thumbWidth, top + thumbHeight);
                    child.setTranslationZ(thumbZ);
                    top += thumbHeight + gap;
                }
            }
            return;
        }
        int index = 0;
        int top = 0;
        for (int row = 0; row < rows && index < count; row++) {
            final int tilesInRow = Math.min(columns, count - index);
            final int cellWidth =
                    tilesInRow == columns
                            ? childWidth
                            : (width - gap * (tilesInRow - 1)) / tilesInRow;
            int left = 0;
            for (int col = 0; col < tilesInRow; col++) {
                final View child = getChildAt(index++);
                child.setTranslationZ(0);
                child.layout(left, top, left + cellWidth, top + childHeight);
                left += cellWidth + gap;
            }
            top += childHeight + gap;
        }
    }
}

package de.monocles.mod;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

public class WebviewAwareViewPager extends androidx.viewpager.widget.ViewPager {
    public WebviewAwareViewPager(Context context) {
        super(context);
    }

    public WebviewAwareViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (v instanceof WebView) {
            // This disables all viewpager swiping over the webview, which is a bit too aggressive
            // But the default is to do it too often, so tradeoffs...
            return true;
        }
        return super.canScroll(v, checkV, dx, x, y);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Отключаем обработку свайпов для переключения вкладок
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Предотвращаем перехват событий касания для отключения свайпов
        return false;
    }
}
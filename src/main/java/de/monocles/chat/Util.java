package de.monocles.chat;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ListAdapter;

import eu.siacs.conversations.R;

public class Util {

    public static void justifyListViewHeightBasedOnChildren (ListView listView) {
        ListAdapter adapter = listView.getAdapter();

        if (adapter == null) {
            return;
        }
        ViewGroup vg = listView;
        int totalHeight = 0;
        final int width = listView.getWidth() > 0 ? listView.getWidth() : listView.getContext().getResources().getDisplayMetrics().widthPixels;
        final int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST);
        for (int i = 0; i < adapter.getCount(); i++) {
            View listItem = adapter.getView(i, null, vg);
            listItem.measure(widthSpec, 0);
            totalHeight += listItem.getMeasuredHeight();
        }

        ViewGroup.LayoutParams par = listView.getLayoutParams();
        par.height = totalHeight + (listView.getDividerHeight() * (adapter.getCount() - 1));
        listView.setLayoutParams(par);
        listView.requestLayout();
    }

    public enum ReadmarkerType {
        RECEIVED,
        DISPLAYED
    }

    public static int getReadmakerType(boolean isDarkBackground, String readmarkervalue, ReadmarkerType readmakerType) {
        if(isDarkBackground) {
            if(readmakerType == ReadmarkerType.DISPLAYED) {
                if(readmarkervalue.equals("blue_readmarkers")) {
                    return R.drawable.ic_check_all_blue_18dp;
                } else if (readmarkervalue.equals("greenandblue_readmarkers")) {
                    return R.drawable.ic_check_all_green_blue_18dp;
                } else {
                    return R.drawable.ic_check_all_white_18dp;
                }
            } else {
                if(readmarkervalue.equals("blue_readmarkers")) {
                    return R.drawable.ic_check_blue_18dp;
                } else if (readmarkervalue.equals("greenandblue_readmarkers")) {
                    return R.drawable.ic_check_blue_18dp;
                } else {
                    return R.drawable.ic_check_white_18dp;
                }
            }
        } else {
            if(readmakerType == ReadmarkerType.DISPLAYED) {
                if(readmarkervalue.equals("blue_readmarkers")) {
                    return R.drawable.ic_check_all_blue_18dp;
                } else if (readmarkervalue.equals("greenandblue_readmarkers")) {
                    return R.drawable.ic_check_all_green_blue_18dp;
                } else {
                    return R.drawable.ic_check_all_black_18dp;
                }
            } else {
                if(readmarkervalue.equals("blue_readmarkers")) {
                    return R.drawable.ic_check_blue_18dp;
                } else if (readmarkervalue.equals("greenandblue_readmarkers")) {
                    return R.drawable.ic_check_blue_18dp;
                } else {
                    return R.drawable.ic_check_black_18dp;
                }
            }
        }
    }
}

package eu.siacs.conversations.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class ContextMenuRecyclerView extends androidx.recyclerview.widget.RecyclerView {
	protected AdapterContextMenuInfo mAdapterContextMenuInfo = null;

	public ContextMenuRecyclerView(Context context) {
		super(context);
	}

	public ContextMenuRecyclerView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ContextMenuRecyclerView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected ContextMenu.ContextMenuInfo getContextMenuInfo() {
		return mAdapterContextMenuInfo;
	}

	@Override
	public boolean showContextMenuForChild(View originalView) {
		mAdapterContextMenuInfo = new AdapterContextMenuInfo(
			originalView,
			getChildAdapterPosition(originalView),
			getChildItemId(originalView)
		);
		return super.showContextMenuForChild(originalView);
	}
}

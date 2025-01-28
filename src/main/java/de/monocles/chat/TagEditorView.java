package de.monocles.chat;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

import com.tokenautocomplete.TokenCompleteTextView;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.utils.UIHelper;

public class TagEditorView extends TokenCompleteTextView<ListItem.Tag> {
    public TagEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTokenClickStyle(TokenCompleteTextView.TokenClickStyle.Delete);
        setThreshold(1);
        performBestGuess(false);
        allowCollapse(false);
    }

    public void clearSync() {
        for (ListItem.Tag tag : getObjects()) {
            removeObjectSync(tag);
        }
    }

    @Override
    protected View getViewForObject(ListItem.Tag tag) {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        final TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag, (ViewGroup) getParent(), false);
        String upperString = tag.getName().substring(0, 1).toUpperCase() + tag.getName().substring(1).toLowerCase();
        tv.setText(upperString);
        Drawable unwrappedDrawable = AppCompatResources.getDrawable(this.getContext(), R.drawable.rounded_tag);
        Drawable wrappedDrawable = DrawableCompat.wrap(unwrappedDrawable);
        DrawableCompat.setTint(wrappedDrawable, tag.getColor());
        tv.setBackgroundResource(R.drawable.rounded_tag);
        return tv;
    }

    @Override
    protected ListItem.Tag defaultObject(String completionText) {
        return new ListItem.Tag(completionText, UIHelper.getColorForName(completionText), 0, null, true);
    }

    @Override
    public boolean shouldIgnoreToken(ListItem.Tag tag) {
        return getObjects().contains(tag);
    }

    @Override
    public void onFocusChanged(boolean hasFocus, int direction, Rect previous) {
        super.onFocusChanged(hasFocus, direction, previous);
        performCompletion();
    }
}

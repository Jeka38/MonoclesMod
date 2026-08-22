package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.xmpp.forms.Field;
import eu.siacs.conversations.xmpp.forms.Option;

public class FormListFieldWrapper extends FormFieldWrapper {

    private final ListView listView;
    private final List<Option> options;
    private ArrayAdapter<String> adapter;
    private OnItemLongClickedListener onItemLongClickedListener;

    protected FormListFieldWrapper(Context context, Field field) {
        super(context, field);
        listView = view.findViewById(R.id.list);
        options = field.getOptions();
        final boolean multiple = "list-multi".equals(field.getType());
        listView.setChoiceMode(multiple ? ListView.CHOICE_MODE_MULTIPLE : ListView.CHOICE_MODE_SINGLE);
        final List<String> labels = new ArrayList<>();
        for (Option option : options) {
            labels.add(option.getLabel());
        }
        adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final View view = super.getView(position, convertView, parent);
                view.setPadding(dp(16), dp(10), dp(16), dp(10));
                if (listView.isItemChecked(position)) {
                    final TypedValue outValue = new TypedValue();
                    context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, outValue, true);
                    view.setBackgroundColor(outValue.data);
                } else {
                    view.setBackground(null);
                }
                return view;
            }
        };
        listView.setAdapter(adapter);
        setListViewHeightBasedOnChildren(listView);
        listView.setOnItemClickListener((parent, view1, position, id) -> {
            adapter.notifyDataSetChanged();
            invokeOnFormFieldValuesEdited();
        });
        listView.setOnItemLongClickListener((parent, view1, position, id) -> {
            if (onItemLongClickedListener == null || position < 0 || position >= options.size()) {
                return false;
            }
            return onItemLongClickedListener.onItemLongClicked(options.get(position).getValue());
        });
    }

    private int dp(int value) {
        return (int) (context.getResources().getDisplayMetrics().density * value);
    }

    public void setOnItemLongClickedListener(OnItemLongClickedListener listener) {
        this.onItemLongClickedListener = listener;
    }

    public interface OnItemLongClickedListener {
        boolean onItemLongClicked(String value);
    }

    private static void setListViewHeightBasedOnChildren(ListView listView) {
        final ArrayAdapter<?> adapter = (ArrayAdapter<?>) listView.getAdapter();
        if (adapter == null) {
            return;
        }
        int totalHeight = 0;
        final int count = adapter.getCount();
        for (int i = 0; i < count; i++) {
            final View listItem = adapter.getView(i, null, listView);
            listItem.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            totalHeight += listItem.getMeasuredHeight();
        }
        final ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * Math.max(0, count - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
    }

    @Override
    protected void setLabel(String label, boolean required) {
        TextView textView = view.findViewById(R.id.label);
        textView.setText(createSpannableLabelString(label, required));
    }

    @Override
    List<String> getValues() {
        final Set<String> values = new HashSet<>();
        final long checkedCount = listView.getCheckedItemCount();
        if (checkedCount <= 0) {
            return new ArrayList<>();
        }
        for (int i = 0; i < options.size(); i++) {
            if (listView.isItemChecked(i)) {
                values.add(options.get(i).getValue());
            }
        }
        return new ArrayList<>(values);
    }

    @Override
    protected void setValues(List<String> values) {
        final Set<String> selected = new HashSet<>(values);
        for (int i = 0; i < options.size(); i++) {
            listView.setItemChecked(i, selected.contains(options.get(i).getValue()));
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    boolean validates() {
        if (!field.isRequired() || listView.getCheckedItemCount() > 0) {
            return true;
        }
        return false;
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.form_list;
    }

    @Override
    void setReadOnly(boolean readOnly) {
        listView.setEnabled(!readOnly);
        listView.setClickable(!readOnly);
    }
}

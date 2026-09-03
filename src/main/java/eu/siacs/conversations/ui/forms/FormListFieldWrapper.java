package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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

    private static final int SEARCH_THRESHOLD = 9;

    private final ListView listView;
    private final EditText searchEditText;
    private final List<Option> options;
    private final boolean multiple;
    private ArrayAdapter<Option> adapter;
    private OnItemLongClickedListener onItemLongClickedListener;

    protected FormListFieldWrapper(Context context, Field field) {
        super(context, field);
        listView = view.findViewById(R.id.list);
        searchEditText = view.findViewById(R.id.search);
        options = field.getOptions();
        multiple = "list-multi".equals(field.getType());
        listView.setChoiceMode(multiple ? ListView.CHOICE_MODE_MULTIPLE : ListView.CHOICE_MODE_SINGLE);
        adapter = createAdapter(options);
        listView.setAdapter(adapter);

        if (options.size() > SEARCH_THRESHOLD) {
            configureSearch();
        }

        setupListViewHeight(listView);
        listView.setOnItemClickListener((parent, view1, position, id) -> {
            adapter.notifyDataSetChanged();
            invokeOnFormFieldValuesEdited();
        });
        listView.setOnItemLongClickListener((parent, view1, position, id) -> {
            if (onItemLongClickedListener == null || position < 0 || position >= adapter.getCount()) {
                return false;
            }
            return onItemLongClickedListener.onItemLongClicked(adapter.getItem(position).getValue());
        });
    }

    private void configureSearch() {
        searchEditText.setVisibility(View.VISIBLE);
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final Set<String> checkedValues = new HashSet<>(getValues());
                final String q = s.toString().replaceAll("\\W", "").toLowerCase();
                final List<Option> filtered = new ArrayList<>();
                for (Option option : options) {
                    if (q.isEmpty()) {
                        filtered.add(option);
                    } else if (option.getLabel().replaceAll("\\W", "").toLowerCase().contains(q)) {
                        filtered.add(option);
                    }
                }
                adapter = createAdapter(filtered);
                listView.setAdapter(adapter);
                for (int i = 0; i < adapter.getCount(); i++) {
                    listView.setItemChecked(i, checkedValues.contains(adapter.getItem(i).getValue()));
                }
                overlapLayoutHeight();
            }
        });
    }

    private ArrayAdapter<Option> createAdapter(final List<Option> list) {
        return new ArrayAdapter<Option>(context, R.layout.simple_list_item, list) {
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
    }

    private void overlapLayoutHeight() {
        final int visibleRows = adapter.getCount();
        final int height = visibleRows > 0 ? Math.min(dp(240), (int) (visibleRows * dp(32))) : dp(48);
        final ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = height;
        listView.setLayoutParams(params);
        listView.requestLayout();
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

    private void setupListViewHeight(ListView listView) {
        if (options.size() > SEARCH_THRESHOLD) {
            final ViewGroup.LayoutParams params = listView.getLayoutParams();
            params.height = dp(240);
            listView.setLayoutParams(params);
            listView.requestLayout();
            return;
        }
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
        for (int i = 0; i < adapter.getCount(); i++) {
            if (listView.isItemChecked(i)) {
                values.add(adapter.getItem(i).getValue());
            }
        }
        return new ArrayList<>(values);
    }

    @Override
    protected void setValues(List<String> values) {
        final Set<String> selected = new HashSet<>(values);
        for (int i = 0; i < adapter.getCount(); i++) {
            listView.setItemChecked(i, selected.contains(adapter.getItem(i).getValue()));
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
        searchEditText.setEnabled(!readOnly);
    }
}
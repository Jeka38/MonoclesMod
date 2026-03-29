package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceGroup;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.SwitchPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.Compatibility;

public class SettingsFragment extends PreferenceFragment {

    private String page = null;
    private String suffix = null;
    private final List<SearchEntry> searchIndex = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        // Remove from standard preferences if the flag ONLY_INTERNAL_STORAGE is false
        if (!Config.ONLY_INTERNAL_STORAGE) {
            PreferenceCategory mCategory = (PreferenceCategory) findPreference("security_options");
            if (mCategory != null) {
                Preference cleanCache = findPreference("clean_cache");
                Preference cleanPrivateStorage = findPreference("clean_private_storage");
                mCategory.removePreference(cleanCache);
                mCategory.removePreference(cleanPrivateStorage);
            }
        }
        Compatibility.removeUnusedPreferences(this);
        rebuildSearchIndex();

        if (!TextUtils.isEmpty(page)) {
            openPreferenceScreen(page);
        }
    }

    public void showSearchDialog() {
        if (getActivity() == null) {
            return;
        }
        if (searchIndex.isEmpty()) {
            rebuildSearchIndex();
        }

        final LinearLayout container = new LinearLayout(getActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        final int padding = (int) (getResources().getDisplayMetrics().density * 16);
        container.setPadding(padding, padding, padding, 0);

        final EditText searchInput = new EditText(getActivity());
        searchInput.setHint(R.string.search);
        container.addView(searchInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        final ListView resultList = new ListView(getActivity());
        container.addView(resultList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        final List<SearchEntry> filtered = new ArrayList<>();
        final ArrayAdapter<String> adapter =
                new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, new ArrayList<>());
        resultList.setAdapter(adapter);

        final Runnable updateResults = () -> {
            final String query = searchInput.getText() == null ? "" : searchInput.getText().toString();
            filtered.clear();
            adapter.clear();
            for (SearchEntry entry : searchIndex) {
                if (matchesQuery(entry, query)) {
                    filtered.add(entry);
                    adapter.add(entry.displayTitle);
                }
            }
            adapter.notifyDataSetChanged();
        };
        updateResults.run();
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                updateResults.run();
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.search)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        resultList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filtered.size()) {
                return;
            }
            navigateToPreference(filtered.get(position));
            dialog.dismiss();
        });

        dialog.show();
    }

    public void setActivityIntent(final Intent intent) {
        boolean wasEmpty = TextUtils.isEmpty(page);
        if (intent != null) {
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                if (intent.getExtras() != null) {
                    this.page = intent.getExtras().getString("page");
                    this.suffix = intent.getExtras().getString("suffix");
                    if (wasEmpty) {
                        openPreferenceScreen(page);
                    }
                }
            }
        }
    }

    private void openPreferenceScreen(final String screenName) {
        final Preference pref = findPreference(screenName);
        if (pref instanceof PreferenceScreen) {
            final PreferenceScreen preferenceScreen = (PreferenceScreen) pref;
            getActivity().setTitle(preferenceScreen.getTitle());
            preferenceScreen.setDependency("");
            if (this.suffix != null) {
                for (int i = 0; i < preferenceScreen.getPreferenceCount(); i++) {
                    final Preference p = preferenceScreen.getPreference(i);
                    if (!p.hasKey()) continue;
                    p.setKey(p.getKey() + this.suffix);
                    if (p.getDependency() != null && !"".equals(p.getDependency())) {
                        p.setDependency(p.getDependency() + this.suffix);
                    }
                    reloadPref(p);
                }
            }
            setPreferenceScreen((PreferenceScreen) pref);
        }
    }

    private void rebuildSearchIndex() {
        searchIndex.clear();
        final PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            collectSearchEntries(root, null);
        }
    }

    private void collectSearchEntries(final PreferenceGroup group, final String parentScreenKey) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            final Preference pref = group.getPreference(i);
            String nextParentScreenKey = parentScreenKey;
            if (pref instanceof PreferenceScreen && pref.hasKey()) {
                nextParentScreenKey = pref.getKey();
            }
            if (pref.hasKey() && !(pref instanceof PreferenceCategory)) {
                final CharSequence title = pref.getTitle();
                if (!TextUtils.isEmpty(title)) {
                    searchIndex.add(new SearchEntry(
                            pref.getKey(),
                            title.toString(),
                            pref.getSummary() == null ? "" : pref.getSummary().toString(),
                            parentScreenKey
                    ));
                }
            }
            if (pref instanceof PreferenceGroup) {
                collectSearchEntries((PreferenceGroup) pref, nextParentScreenKey);
            }
        }
    }

    private boolean matchesQuery(final SearchEntry entry, final String query) {
        if (TextUtils.isEmpty(query)) {
            return true;
        }
        final String needle = query.toLowerCase(Locale.getDefault()).trim();
        return entry.displayTitle.toLowerCase(Locale.getDefault()).contains(needle)
                || entry.summary.toLowerCase(Locale.getDefault()).contains(needle);
    }

    private void navigateToPreference(final SearchEntry entry) {
        if (!TextUtils.isEmpty(entry.parentScreenKey)) {
            openPreferenceScreen(entry.parentScreenKey);
        }
        if (getView() == null) {
            return;
        }
        final ListView listView = getView().findViewById(android.R.id.list);
        if (listView == null) {
            return;
        }
        final android.widget.ListAdapter adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }
        for (int i = 0; i < adapter.getCount(); i++) {
            final Object item = adapter.getItem(i);
            if (item instanceof Preference) {
                final Preference pref = (Preference) item;
                if (entry.preferenceKey.equals(pref.getKey())) {
                    listView.setSelection(i);
                    break;
                }
            }
        }
    }

    private static class SearchEntry {
        private final String preferenceKey;
        private final String displayTitle;
        private final String summary;
        private final String parentScreenKey;

        private SearchEntry(
                final String preferenceKey,
                final String displayTitle,
                final String summary,
                final String parentScreenKey
        ) {
            this.preferenceKey = preferenceKey;
            this.displayTitle = displayTitle;
            this.summary = summary;
            this.parentScreenKey = parentScreenKey;
        }
    }

    static void reloadPref(final Preference pref) {
        Class iterClass = pref.getClass();
        while(iterClass != Object.class) {
            try {
                Method m = iterClass.getDeclaredMethod("onSetInitialValue", boolean.class, Object.class);
                m.setAccessible(true);
                m.invoke(pref, true, null);
            } catch (Exception e) { }
            iterClass = iterClass.getSuperclass();
        }
    }
}

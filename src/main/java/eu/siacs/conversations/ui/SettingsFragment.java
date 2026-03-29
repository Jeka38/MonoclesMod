package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.Compatibility;

public class SettingsFragment extends PreferenceFragment {

    private String page = null;
    private String suffix = null;
    private PreferenceScreen mRootPreferenceScreen;
    private PreferenceScreen mBeforeSearchScreen;
    private PreferenceScreen mSearchResultScreen;
    private Map<Preference, PreferenceGroup> originalParents = new HashMap<>();
    private Map<Preference, Integer> originalOrders = new HashMap<>();

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

        mRootPreferenceScreen = getPreferenceScreen();
        captureInfos(mRootPreferenceScreen);
        mBeforeSearchScreen = mRootPreferenceScreen;

        if (!TextUtils.isEmpty(page)) {
            openPreferenceScreen(page);
            mBeforeSearchScreen = getPreferenceScreen();
        }
    }

    private void captureInfos(PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference p = group.getPreference(i);
            originalParents.put(p, group);
            originalOrders.put(p, p.getOrder());
            if (p instanceof PreferenceGroup) {
                captureInfos((PreferenceGroup) p);
            }
        }
    }

    public void filter(String query) {
        if (mRootPreferenceScreen == null) {
            return;
        }

        restoreAll();

        if (TextUtils.isEmpty(query)) {
            setPreferenceScreen(mBeforeSearchScreen);
            return;
        }

        query = query.toLowerCase(Locale.getDefault());
        if (mSearchResultScreen == null) {
            mSearchResultScreen = getPreferenceManager().createPreferenceScreen(getActivity());
        } else {
            mSearchResultScreen.removeAll();
        }

        List<Preference> matches = new ArrayList<>();
        filter(mRootPreferenceScreen, query, matches);

        for (Preference p : matches) {
            PreferenceGroup parent = originalParents.get(p);
            if (parent != null) {
                parent.removePreference(p);
            }
            mSearchResultScreen.addPreference(p);
        }

        setPreferenceScreen(mSearchResultScreen);
    }

    private void restoreAll() {
        if (mSearchResultScreen != null) {
            for (int i = mSearchResultScreen.getPreferenceCount() - 1; i >= 0; i--) {
                Preference p = mSearchResultScreen.getPreference(i);
                mSearchResultScreen.removePreference(p);
                PreferenceGroup originalParent = originalParents.get(p);
                if (originalParent != null) {
                    originalParent.addPreference(p);
                    p.setOrder(originalOrders.get(p));
                }
            }
        }
    }

    private void filter(PreferenceGroup group, String query, List<Preference> matches) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference p = group.getPreference(i);
            if (p instanceof PreferenceGroup) {
                filter((PreferenceGroup) p, query, matches);
            } else {
                String title = p.getTitle() != null ? p.getTitle().toString().toLowerCase(Locale.getDefault()) : "";
                String summary = p.getSummary() != null ? p.getSummary().toString().toLowerCase(Locale.getDefault()) : "";
                if (title.contains(query) || summary.contains(query)) {
                    matches.add(p);
                }
            }
        }
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
                        mBeforeSearchScreen = getPreferenceScreen();
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

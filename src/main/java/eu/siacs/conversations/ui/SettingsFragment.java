package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.SwitchPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.text.TextUtils;

import java.lang.reflect.Method;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.Compatibility;

public class SettingsFragment extends PreferenceFragment {

    private String page = null;
    private String suffix = null;

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

        if (!TextUtils.isEmpty(page)) {
            openPreferenceScreen(page);
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

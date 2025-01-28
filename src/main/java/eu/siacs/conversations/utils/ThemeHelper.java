/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.widget.TextView;
import android.content.res.loader.ResourcesLoader;
import de.monocles.chat.ColorResourcesLoaderCreator;
import java.util.HashMap;
import android.util.Log;


import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.SettingsActivity;
import eu.siacs.conversations.ui.util.StyledAttributes;

public class ThemeHelper {
    public static HashMap<Integer, Integer> applyCustomColors(final Context context) {
        HashMap<Integer, Integer> colors = new HashMap<>();
        if (Build.VERSION.SDK_INT < 30) return colors;

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences.contains("custom_theme_primary")) colors.put(R.color.custom_theme_primary, sharedPreferences.getInt("custom_theme_primary", 0));
        if (sharedPreferences.contains("custom_theme_primary_dark")) colors.put(R.color.custom_theme_primary_dark, sharedPreferences.getInt("custom_theme_primary_dark", 0));
        if (sharedPreferences.contains("custom_theme_accent")) colors.put(R.color.custom_theme_accent, sharedPreferences.getInt("custom_theme_accent", 0));
        if (colors.isEmpty()) return colors;

        ResourcesLoader loader = de.monocles.chat.ColorResourcesLoaderCreator.create(context, colors);
        try {
            if (loader != null) context.getResources().addLoaders(loader);
        } catch (final IllegalArgumentException e) {
            Log.w(Config.LOGTAG, "Custom colour failed: " + e);
        }
        return colors;
    }
    public static int find(final Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        final boolean auto = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("auto");
        final boolean black = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("black");
        boolean dark;
        if (auto) {
            dark = nightMode(context);
        } else {
            dark = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("dark") || black;
        }
        final String themeColor = sharedPreferences.getString("theme_color", resources.getString(R.string.theme_color));
        final String fontSize = sharedPreferences.getString("font_size", resources.getString(R.string.default_font_size));
        switch (themeColor) {
            case "blue":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_Black_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Dark_Medium;
                        } else {
                            return R.style.ConversationsTheme_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_Black_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Dark_Large;
                        } else {
                            return R.style.ConversationsTheme_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_Black;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Dark;
                        } else {
                            return R.style.ConversationsTheme;
                        }
                }
            case "monocles":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_Monocles_Black_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Monocles_Dark_Medium;
                        } else {
                            return R.style.ConversationsTheme_Monocles_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_Monocles_Black_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Monocles_Dark_Large;
                        } else {
                            return R.style.ConversationsTheme_Monocles_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_Monocles_Black;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Monocles_Dark;
                        } else {
                            return R.style.ConversationsTheme_Monocles;
                        }
                }
            case "orange":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_Orange_Black_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Orange_Dark_Medium;
                        } else {
                            return R.style.ConversationsTheme_Orange_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_Orange_Black_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Orange_Dark_Large;
                        } else {
                            return R.style.ConversationsTheme_Orange_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_Orange_Black;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Orange_Dark;
                        } else {
                            return R.style.ConversationsTheme_Orange;
                        }
                }
            case "grey":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_Grey_Black_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Grey_Dark_Medium;
                        } else {
                            return R.style.ConversationsTheme_Grey_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_Grey_Black_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Grey_Dark_Large;
                        } else {
                            return R.style.ConversationsTheme_Grey_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_Grey_Black;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Grey_Dark;
                        } else {
                            return R.style.ConversationsTheme_Grey;
                        }
                }

            case "White":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_White_Black_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_White_Dark_Medium;
                        } else {
                            return R.style.ConversationsTheme_White_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_White_Black_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_White_Dark_Large;
                        } else {
                            return R.style.ConversationsTheme_White_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_White_Black;
                        } else if (dark) {
                            return R.style.ConversationsTheme_White_Dark;
                        } else {
                            return R.style.ConversationsTheme_White;
                        }
                }
            case "pink":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_Pink_Black_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Pink_Dark_Medium;
                        } else {
                            return R.style.ConversationsTheme_Pink_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_Pink_Black_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Pink_Dark_Large;
                        } else {
                            return R.style.ConversationsTheme_Pink_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_Pink_Black;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Pink_Dark;
                        } else {
                            return R.style.ConversationsTheme_Pink;
                        }
                }
            case "custom":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_CustomDark_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_CustomDark_Medium;
                        } else {
                            return R.style.ConversationsTheme_Custom_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_CustomDark_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_CustomDark_Large;
                        } else {
                            return R.style.ConversationsTheme_Custom_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_CustomDark;
                        } else if (dark) {
                            return R.style.ConversationsTheme_CustomDark;
                        } else {
                            return R.style.ConversationsTheme_Custom;
                        }
                }
            default:
                if (black) {
                    return R.style.ConversationsTheme_Monocles_Black;
                } else if (dark) {
                    return R.style.ConversationsTheme_Monocles_Dark;
                } else {
                    return R.style.ConversationsTheme_Monocles;
                }
        }
    }

    private static boolean nightMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_YES:
                return true;
            case Configuration.UI_MODE_NIGHT_NO:
                return false;
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
                return false;
            default:
                return false;
        }
    }

    public static int findDialog(Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        final boolean black = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("black");
        final boolean auto = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("auto");
        boolean dark;
        if (auto) {
            dark = nightMode(context);
        } else {
            dark = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("dark") || black;
        }
        final String fontSize = sharedPreferences.getString("font_size", resources.getString(R.string.default_font_size));
        final String themeColor = sharedPreferences.getString("theme_color", resources.getString(R.string.theme_color));
        switch (themeColor) {
            case "blue":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_Black_Dialog_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Dark_Dialog_Medium;
                        } else {
                            return R.style.ConversationsTheme_Dialog_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_Black_Dialog_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Dark_Dialog_Large;
                        } else {
                            return R.style.ConversationsTheme_Dialog_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_Black_Dialog;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Dark_Dialog;
                        } else {
                            return R.style.ConversationsTheme_Dialog;
                        }
                }
            case "monocles":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_Monocles_Black_Dialog_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Monocles_Dark_Dialog_Medium;
                        } else {
                            return R.style.ConversationsTheme_Monocles_Dialog_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_Monocles_Black_Dialog_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Monocles_Dark_Dialog_Large;
                        } else {
                            return R.style.ConversationsTheme_Monocles_Dialog_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_Monocles_Black_Dialog;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Monocles_Dark_Dialog;
                        } else {
                            return R.style.ConversationsTheme_Monocles_Dialog;
                        }
                }
            case "orange":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_Orange_Black_Dialog_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Orange_Dark_Dialog_Medium;
                        } else {
                            return R.style.ConversationsTheme_Orange_Dialog_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_Orange_Black_Dialog_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Orange_Dark_Dialog_Large;
                        } else {
                            return R.style.ConversationsTheme_Orange_Dialog_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_Orange_Black_Dialog;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Orange_Dark_Dialog;
                        } else {
                            return R.style.ConversationsTheme_Orange_Dialog;
                        }
                }
            case "grey":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_Grey_Black_Dialog_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Grey_Dark_Dialog_Medium;
                        } else {
                            return R.style.ConversationsTheme_Grey_Dialog_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_Grey_Black_Dialog_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Grey_Dark_Dialog_Large;
                        } else {
                            return R.style.ConversationsTheme_Grey_Dialog_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_Grey_Black_Dialog;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Grey_Dark_Dialog;
                        } else {
                            return R.style.ConversationsTheme_Grey_Dialog;
                        }
                }

            case "White":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_White_Black_Dialog_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_White_Dark_Dialog_Medium;
                        } else {
                            return R.style.ConversationsTheme_White_Dialog_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_White_Black_Dialog_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_White_Dark_Dialog_Large;
                        } else {
                            return R.style.ConversationsTheme_White_Dialog_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_White_Black_Dialog;
                        } else if (dark) {
                            return R.style.ConversationsTheme_White_Dark_Dialog;
                        } else {
                            return R.style.ConversationsTheme_White_Dialog;
                        }
                }
            case "pink":
                switch (fontSize) {
                    case "medium":
                        if (black) {
                            return R.style.ConversationsTheme_Pink_Black_Dialog_Medium;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Pink_Dark_Dialog_Medium;
                        } else {
                            return R.style.ConversationsTheme_Pink_Dialog_Medium;
                        }
                    case "large":
                        if (black) {
                            return R.style.ConversationsTheme_Pink_Black_Dialog_Large;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Pink_Dark_Dialog_Large;
                        } else {
                            return R.style.ConversationsTheme_Pink_Dialog_Large;
                        }
                    default:
                        if (black) {
                            return R.style.ConversationsTheme_Pink_Black_Dialog;
                        } else if (dark) {
                            return R.style.ConversationsTheme_Pink_Dark_Dialog;
                        } else {
                            return R.style.ConversationsTheme_Pink_Dialog;
                        }
                }
            default:
                if (black) {
                    return R.style.ConversationsTheme_Monocles_Black_Dialog;
                } else if (dark) {
                    return R.style.ConversationsTheme_Monocles_Dark_Dialog;
                } else {
                    return R.style.ConversationsTheme_Monocles_Dialog;
                }
        }
    }
    private static boolean isDark(final SharedPreferences sharedPreferences, final Resources resources) {
        final String setting = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && "automatic".equals(setting)) {
            return (resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        } else {
            if ("custom".equals(setting)) return sharedPreferences.getBoolean("custom_theme_dark", false);
            return "dark".equals(setting) || "obsidian".equals(setting) || "oledblack".equals(setting);
        }
    }
    public static boolean isDark(@StyleRes int id) {
        switch (id) {
            //blue
            case R.style.ConversationsTheme_Dark:
            case R.style.ConversationsTheme_Dark_Large:
            case R.style.ConversationsTheme_Dark_Medium:
            case R.style.ConversationsTheme_Black:
            case R.style.ConversationsTheme_Black_Large:
            case R.style.ConversationsTheme_Black_Medium:
                //monocles
            case R.style.ConversationsTheme_Monocles_Dark:
            case R.style.ConversationsTheme_Monocles_Dark_Large:
            case R.style.ConversationsTheme_Monocles_Dark_Medium:
            case R.style.ConversationsTheme_Monocles_Black:
            case R.style.ConversationsTheme_Monocles_Black_Large:
            case R.style.ConversationsTheme_Monocles_Black_Medium:
                //orange
            case R.style.ConversationsTheme_Orange_Dark:
            case R.style.ConversationsTheme_Orange_Dark_Large:
            case R.style.ConversationsTheme_Orange_Dark_Medium:
            case R.style.ConversationsTheme_Orange_Black:
            case R.style.ConversationsTheme_Orange_Black_Large:
            case R.style.ConversationsTheme_Orange_Black_Medium:
                //grey
            case R.style.ConversationsTheme_Grey_Dark:
            case R.style.ConversationsTheme_Grey_Dark_Large:
            case R.style.ConversationsTheme_Grey_Dark_Medium:
            case R.style.ConversationsTheme_Grey_Black:
            case R.style.ConversationsTheme_Grey_Black_Large:
            case R.style.ConversationsTheme_Grey_Black_Medium:
                //White
            case R.style.ConversationsTheme_White_Dark:
            case R.style.ConversationsTheme_White_Dark_Large:
            case R.style.ConversationsTheme_White_Dark_Medium:
            case R.style.ConversationsTheme_White_Black:
            case R.style.ConversationsTheme_White_Black_Large:
            case R.style.ConversationsTheme_White_Black_Medium:
                //pink
            case R.style.ConversationsTheme_Pink_Dark:
            case R.style.ConversationsTheme_Pink_Dark_Large:
            case R.style.ConversationsTheme_Pink_Dark_Medium:
            case R.style.ConversationsTheme_Pink_Black:
            case R.style.ConversationsTheme_Pink_Black_Large:
            case R.style.ConversationsTheme_Pink_Black_Medium:
                //custom
            case R.style.ConversationsTheme_CustomDark:
            case R.style.ConversationsTheme_CustomDark_Large:
            case R.style.ConversationsTheme_CustomDark_Medium:
                return true;
            default:
                return false;
        }
    }

    public static ColorStateList AudioPlayerColor(Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        final boolean auto = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("auto");
        boolean dark;
        if (auto) {
            dark = nightMode(context);
        } else {
            dark = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("dark");
        }
        final String themeColor = sharedPreferences.getString("theme_color", resources.getString(R.string.theme_color));
        switch (themeColor) {
            case "blue":
                return dark ? ContextCompat.getColorStateList(context, R.color.white70) : ContextCompat.getColorStateList(context, R.color.darkblue);
            case "monocles":
                return dark ? ContextCompat.getColorStateList(context, R.color.white70) : ContextCompat.getColorStateList(context, R.color.darkmonocles);
            case "orange":
                return dark ? ContextCompat.getColorStateList(context, R.color.white70) : ContextCompat.getColorStateList(context, R.color.darkorange);
            case "grey":
                return dark ? ContextCompat.getColorStateList(context, R.color.white70) : ContextCompat.getColorStateList(context, R.color.darkgrey);
            case "White":
                return dark ? ContextCompat.getColorStateList(context, R.color.white70) : ContextCompat.getColorStateList(context, R.color.darkWhite);
            case "pink":
                return dark ? ContextCompat.getColorStateList(context, R.color.white70) : ContextCompat.getColorStateList(context, R.color.darkpink);
            case "custom":
                return dark ? ContextCompat.getColorStateList(context, R.color.white70) : ContextCompat.getColorStateList(context, R.color.darkWhite);
            default:
                return dark ? ContextCompat.getColorStateList(context, R.color.white70) : ContextCompat.getColorStateList(context, R.color.darkmonocles);
        }
    }

    public SharedPreferences getPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static int notificationColor(Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        final String themeColor = sharedPreferences.getString("theme_color", resources.getString(R.string.theme_color));
        switch (themeColor) {
            case "blue":
                return R.color.primary;
            case "monocles":
                return R.color.primary_monocles;
            case "orange":
                return R.color.primary_orange;
            case "grey":
                return R.color.primary_grey;
            case "White":
                return R.color.primary_White;
            case "pink":
                return R.color.primary_pink;
            default:
                return R.color.primary_monocles;
        }
    }

    public static int getMessageTextColor(Context context, boolean onDark, boolean primary) {
        if (onDark) {
            return ContextCompat.getColor(context, primary ? R.color.white : R.color.white70);
        } else {
            return ContextCompat.getColor(context, primary ? R.color.black87 : R.color.black54);
        }
    }

    public static int messageTextColor(Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        final boolean auto = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("auto");
        boolean dark;
        if (auto) {
            dark = nightMode(context);
        } else {
            dark = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("dark") || sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("black");
        }
        final String themeColor = sharedPreferences.getString("theme_color", resources.getString(R.string.theme_color));
        switch (themeColor) {
            case "blue":
                return dark ? getMessageTextColor(context, dark, false) : ContextCompat.getColor(context, R.color.darkblue);
            case "monocles":
                return dark ? getMessageTextColor(context, dark, false) : ContextCompat.getColor(context, R.color.darkmonocles);
            case "orange":
                return dark ? getMessageTextColor(context, dark, false) : ContextCompat.getColor(context, R.color.darkorange);
            case "grey":
                return dark ? getMessageTextColor(context, dark, false) : ContextCompat.getColor(context, R.color.darkgrey);
            case "White":
                return dark ? getMessageTextColor(context, dark, false) : ContextCompat.getColor(context, R.color.darkWhite);
            case "pink":
                return dark ? getMessageTextColor(context, dark, false) : ContextCompat.getColor(context, R.color.darkpink);
            default:
                return dark ? getMessageTextColor(context, dark, false) : ContextCompat.getColor(context, R.color.darkmonocles);
        }
    }

    public static int getMessageTextColorPrivate(Context context) {
        return StyledAttributes.getColor(context, R.attr.colorAccent);
    }

    public static int getWarningTextColor(Context context, boolean onDark) {
        if (onDark) {
            return ContextCompat.getColor(context, R.color.white70);
        } else {
            return ContextCompat.getColor(context, R.color.black26);
        }
    }

    public static int getMissedCallTextColor(Context context, boolean onDark) {
        if (onDark) {
            return ContextCompat.getColor(context, R.color.redmissedcall);
        } else {
            return ContextCompat.getColor(context, R.color.redmissedcall);
        }
    }

    public static int getCallTextColor(Context context, boolean onDark) {
        if (onDark) {
            return ContextCompat.getColor(context, R.color.realwhite);
        } else {
            return ContextCompat.getColor(context, R.color.realwhite);
        }
    }

    public static void fix(Snackbar snackbar) {
        final Context context = snackbar.getContext();
        TypedArray typedArray = context.obtainStyledAttributes(new int[]{R.attr.TextSizeBody1});
        final float size = typedArray.getDimension(0, 0f);
        typedArray.recycle();
        if (size != 0f) {
            final TextView text = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
            final TextView action = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_action);
            if (text != null && action != null) {
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                action.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                action.setTextColor(ContextCompat.getColor(context, R.color.accent_monocles));
            }
        }
    }

    public static boolean showColoredUsernameBackGround(Context context, boolean dark) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        final String themeColor = sharedPreferences.getString("theme_color", resources.getString(R.string.theme_color));
        switch (themeColor) {
            case "blue":
                return dark ? false : false;
            case "monocles":
                return dark ? true : false;
            case "orange":
                return dark ? true : false;
            case "grey":
                return dark ? false : false;
            case "White":
                return dark ? false : false;
            case "pink":
                return dark ? true : false;
            default:
                return dark ? true : false;
        }
    }

}
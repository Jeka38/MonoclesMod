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

package eu.siacs.conversations.ui.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.SettingsActivity;
import eu.siacs.conversations.utils.UIHelper;

public class SendButtonTool {

    public static SendButtonAction getAction(final Activity activity, final Conversation c, final String text, final String subject) {
        if (activity == null) {
            return SendButtonAction.TEXT;
        }
        final boolean empty = text.length() == 0;
        final boolean conference = c.getMode() == Conversation.MODE_MULTI;
        if (c.getCorrectingMessage() != null && (empty || (text.equals(c.getCorrectingMessage().getBody()) && (subject.equals(c.getCorrectingMessage().getSubject())) && (c.getThread() == c.getCorrectingMessage().getThread() || (c.getThread() != null && c.getThread().equals(c.getCorrectingMessage().getThread())))))) {
            return SendButtonAction.CANCEL;
        } else if (conference && !c.getAccount().httpUploadAvailable()) {
            if (empty && c.getNextCounterpart() != null) {
                return SendButtonAction.CANCEL;
            } else {
                return SendButtonAction.TEXT;
            }
        } else {
            if (empty && (c.getThread() == null || subject.length() == 0)) {
                final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
                if (conference && c.getNextCounterpart() != null) {
                    return SendButtonAction.CANCEL;
                } else {
                    String setting = preferences.getString("quick_action", activity.getResources().getString(R.string.quick_action));
                    if (quickShareChoice(activity) && AttachmentsVisible(c)) {
                        return SendButtonAction.CHOOSE_ATTACHMENT;
                    } else if (quickShareChoice(activity) && !AttachmentsVisible(c)) {
                        return SendButtonAction.TEXT;
                    } else {
                        if (!"none".equals(setting) && UIHelper.receivedLocationQuestion(c.getLatestMessage())) {
                            return SendButtonAction.SEND_LOCATION;
                        } else {
                            if ("recent".equals(setting)) {
                                setting = preferences.getString(ConversationFragment.RECENTLY_USED_QUICK_ACTION, SendButtonAction.TEXT.toString());
                                return SendButtonAction.valueOfOrDefault(setting);
                            } else {
                                return SendButtonAction.valueOfOrDefault(setting);
                            }
                        }
                    }
                }
            } else {
                return SendButtonAction.TEXT;
            }
        }
    }

    public static boolean AttachmentsVisible(Conversation conversation) {
        final boolean visible;
        visible = conversation.getMode() != Conversation.MODE_MULTI || conversation.getAccount().httpUploadAvailable() && conversation.getMucOptions().participating();
        return visible;
    }

    public static int getSendButtonImageResource(Activity activity, SendButtonAction action, Presence.Status status) {
        switch (action) {
            case TEXT:
                switch (status) {
                    case CHAT:
                    case ONLINE:
                        return R.drawable.ic_send_text_online;
                    case AWAY:
                        return R.drawable.ic_send_text_away;
                    case XA:
                    case DND:
                        return R.drawable.ic_send_text_dnd;
                    default:
                        setSendButtonColor(activity, R.drawable.ic_send_text_offline_white);
                        return R.drawable.ic_send_text_offline_white;
                }
            case RECORD_VIDEO:
                switch (status) {
                    case CHAT:
                    case ONLINE:
                        return R.drawable.ic_send_videocam_online;
                    case AWAY:
                        return R.drawable.ic_send_videocam_away;
                    case XA:
                    case DND:
                        return R.drawable.ic_send_videocam_dnd;
                    default:
                        setSendButtonColor(activity, R.drawable.ic_send_videocam_offline_white);
                        return R.drawable.ic_send_videocam_offline_white;                }
            case TAKE_PHOTO:
                switch (status) {
                    case CHAT:
                    case ONLINE:
                        return R.drawable.ic_send_photo_online;
                    case AWAY:
                        return R.drawable.ic_send_photo_away;
                    case XA:
                    case DND:
                        return R.drawable.ic_send_photo_dnd;
                    default:
                        setSendButtonColor(activity, R.drawable.ic_send_photo_offline_white);
                        return R.drawable.ic_send_photo_offline_white;                }
            case RECORD_VOICE:
                switch (status) {
                    case CHAT:
                    case ONLINE:
                        return R.drawable.ic_send_voice_online;
                    case AWAY:
                        return R.drawable.ic_send_voice_away;
                    case XA:
                    case DND:
                        return R.drawable.ic_send_voice_dnd;
                    default:
                        setSendButtonColor(activity, R.drawable.ic_send_voice_offline_white);
                        return R.drawable.ic_send_voice_offline_white;                }
            case SEND_LOCATION:
                switch (status) {
                    case CHAT:
                    case ONLINE:
                        return R.drawable.ic_send_location_online;
                    case AWAY:
                        return R.drawable.ic_send_location_away;
                    case XA:
                    case DND:
                        return R.drawable.ic_send_location_dnd;
                    default:
                        setSendButtonColor(activity, R.drawable.ic_send_location_offline_white);
                        return R.drawable.ic_send_location_offline_white;                }
            case CANCEL:
                switch (status) {
                    case CHAT:
                    case ONLINE:
                        return R.drawable.ic_send_cancel_online;
                    case AWAY:
                        return R.drawable.ic_send_cancel_away;
                    case XA:
                    case DND:
                        return R.drawable.ic_send_cancel_dnd;
                    default:
                        setSendButtonColor(activity, R.drawable.ic_send_cancel_offline_white);
                        return R.drawable.ic_send_cancel_offline_white;                }
            case CHOOSE_PICTURE:
                switch (status) {
                    case CHAT:
                    case ONLINE:
                        return R.drawable.ic_send_picture_online;
                    case AWAY:
                        return R.drawable.ic_send_picture_away;
                    case XA:
                    case DND:
                        return R.drawable.ic_send_picture_dnd;
                    default:
                        setSendButtonColor(activity, R.drawable.ic_send_picture_offline_white);
                        return R.drawable.ic_send_picture_offline_white;                }

            case CHOOSE_ATTACHMENT:
                switch (status) {
                    case CHAT:
                    case ONLINE:
                        return R.drawable.ic_send_attachment_online;
                    case AWAY:
                        return R.drawable.ic_send_attachment_away;
                    case XA:
                    case DND:
                        return R.drawable.ic_send_attachment_dnd;
                    default:
                        setSendButtonColor(activity, R.drawable.ic_send_attachment_offline_white);
                        return R.drawable.ic_send_attachment_offline_white;                }
        }
        return getThemeResource(activity, R.attr.ic_send_text_offline, R.drawable.ic_send_text_offline);
    }

    private static int getThemeResource(Activity activity, int r_attr_name, int r_drawable_def) {
        int[] attrs = {r_attr_name};
        TypedArray ta = activity.getTheme().obtainStyledAttributes(attrs);

        int res = ta.getResourceId(0, r_drawable_def);
        ta.recycle();

        return res;
    }

    private static void setSendButtonColor(Activity activity, int ic_send_button_offline_white)
    {
        // takes a white drawable, and fills accent color in it
        Drawable unwrappedDrawable = AppCompatResources.getDrawable(activity.getBaseContext(), ic_send_button_offline_white);
        Drawable wrappedDrawable = DrawableCompat.wrap(unwrappedDrawable);
        DrawableCompat.setTint(wrappedDrawable, StyledAttributes.getColor(activity, R.attr.colorAccent));
    }

    public static boolean quickShareChoice(Activity activity) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        return preferences.getBoolean(SettingsActivity.QUICK_SHARE_ATTACHMENT_CHOICE, activity.getResources().getBoolean(R.bool.quick_share_attachment_choice));
    }
}
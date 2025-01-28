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

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.XmppActivity;

public class ConversationMenuConfigurator {

    private static boolean microphoneAvailable = false;
    private static boolean locationAvailable = false;
    private static boolean cameraAvailable = false;
    private static boolean attachSubject = false;

    public static void reloadFeatures(@NonNull Conversation conversation, Context context) {
        microphoneAvailable = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        cameraAvailable = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        locationAvailable = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) || context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK);
        if (conversation.getNextEncryption()== Message.ENCRYPTION_NONE) {
            attachSubject = true;
        } else {
            attachSubject = false;
        }
    }

    public static void configureQuickShareAttachmentMenu(@NonNull Conversation conversation, Menu menu, boolean hideVoiceAndTakePicture) {
        final boolean visible = SendButtonTool.AttachmentsVisible(conversation);
        if (!visible) {
            return;
        }
        if (hideVoiceAndTakePicture) {
            microphoneAvailable = false;
            cameraAvailable = false;
        }
        menu.findItem(R.id.attach_record_voice).setVisible(microphoneAvailable);
        menu.findItem(R.id.attach_take_picture).setVisible(cameraAvailable);
        menu.findItem(R.id.attach_location).setVisible(locationAvailable);
        menu.findItem(R.id.attach_subject).setVisible(attachSubject);
    }

    public static void configureAttachmentMenu(@NonNull Conversation conversation, Menu menu, Boolean Quick_share_attachment_choice, boolean hasAttachments) {
        if (menu == null) {
            return;
        }
        final MenuItem menuAttach = menu.findItem(R.id.action_attach_file);
        boolean isPM = false;
        try {
            isPM = conversation.getMode() == Conversation.MODE_MULTI && conversation.getNextCounterpart() != null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (Quick_share_attachment_choice && !hasAttachments && !isPM) {
            menuAttach.setVisible(false);
            return;
        }
        final boolean visible;
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            visible = conversation.getAccount().httpUploadAvailable() && conversation.getMucOptions().participating()
                    || conversation.getAccount().httpUploadAvailable() && isPM;
        } else {
            visible = true;
        }
        menuAttach.setVisible(visible);
        if (!visible) {
            return;
        }
        menu.findItem(R.id.attach_record_voice).setVisible(microphoneAvailable);
        menu.findItem(R.id.attach_take_picture).setVisible(cameraAvailable);
        menu.findItem(R.id.attach_location).setVisible(locationAvailable);
        menu.findItem(R.id.attach_subject).setVisible(attachSubject);
    }

    public static void configureEncryptionMenu(@NonNull Conversation conversation, Menu menu, final XmppActivity activity) {
        final MenuItem menuSecure = menu.findItem(R.id.action_security);
        final boolean participating = conversation.getMode() == Conversational.MODE_SINGLE || conversation.getMucOptions().participating();
        if (!participating) {
            menuSecure.setVisible(false);
            return;
        }
        final MenuItem none = menu.findItem(R.id.encryption_choice_none);
        final MenuItem otr = menu.findItem(R.id.encryption_choice_otr);
        final MenuItem pgp = menu.findItem(R.id.encryption_choice_pgp);
        final MenuItem axolotl = menu.findItem(R.id.encryption_choice_axolotl);

        final int next = conversation.getNextEncryption();

        boolean visible;
        if (OmemoSetting.isAlways() || OmemoSetting.isNever()) {
            visible = false;
        } else if (conversation.getMode() == Conversation.MODE_MULTI) {
            if (next == Message.ENCRYPTION_NONE && !conversation.isPrivateAndNonAnonymous() && !conversation.getBooleanAttribute(Conversation.ATTRIBUTE_FORMERLY_PRIVATE_NON_ANONYMOUS, false)) {
                visible = false;
            } else {
                visible = (Config.supportOpenPgp() || Config.supportOmemo()) && Config.multipleEncryptionChoices();
            }
        } else {
            visible = Config.multipleEncryptionChoices();
        }

        menuSecure.setVisible(visible);

        if (!visible) {
            return;
        }
        boolean darkBackground = activity.isDarkTheme();
        if (conversation.getNextEncryption() != Message.ENCRYPTION_NONE) {
            menuSecure.setIcon(darkBackground ? R.drawable.ic_lock_white_24dp : R.drawable.ic_lock_black_24dp);
        }
        otr.setVisible(Config.supportOtr() && activity.enableOTR());
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            otr.setVisible(false);
        }
        pgp.setVisible(Config.supportOpenPgp());
        none.setVisible(Config.supportUnencrypted() || conversation.getMode() == Conversation.MODE_MULTI);
        axolotl.setVisible(Config.supportOmemo());
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_OTR:
                menuSecure.setTitle(R.string.encryption_choice_otr);
                otr.setChecked(true);
                break;
            case Message.ENCRYPTION_PGP:
                menuSecure.setTitle(R.string.encrypted_with_openpgp);
                pgp.setChecked(true);
                break;
            case Message.ENCRYPTION_AXOLOTL:
                menuSecure.setTitle(R.string.encrypted_with_omemo);
                axolotl.setChecked(true);
                break;
            default:
                menuSecure.setTitle(R.string.not_encrypted);
                none.setChecked(true);
                break;
        }
    }
}
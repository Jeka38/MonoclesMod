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

package eu.siacs.conversations.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import android.os.Build;

import com.canhub.cropper.CropImage;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityPublishProfilePictureBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.interfaces.OnAvatarPublication;
import eu.siacs.conversations.ui.util.PendingItem;
import me.drakeet.support.toast.ToastCompat;

import static eu.siacs.conversations.ui.PublishProfilePictureActivity.REQUEST_CHOOSE_PICTURE;

public class PublishGroupChatProfilePictureActivity extends XmppActivity implements OnAvatarPublication {

    private final PendingItem<String> pendingConversationUuid = new PendingItem<>();
    private ActivityPublishProfilePictureBinding binding;
    private Conversation conversation;
    private Uri uri;

    @Override
    protected void refreshUiReal() {

    }

    @Override
    protected void onBackendConnected() {
        String uuid = pendingConversationUuid.pop();
        if (uuid != null) {
            this.conversation = xmppConnectionService.findConversationByUuid(uuid);
        }
        if (this.conversation == null) {
            return;
        }
        reloadAvatar();
    }

    private void reloadAvatar() {
        final int size = getPixel(Config.AVATAR_SIZE);
        Drawable bitmap;
        if (uri == null) {
            bitmap = xmppConnectionService.getAvatarService().get(conversation, size);
        } else {
            Log.d(Config.LOGTAG, "loading " + uri.toString() + " into preview");
            bitmap = xmppConnectionService.getFileBackend().cropCenterSquareDrawable(uri, size);
        }
        this.binding.accountImage.setImageDrawable(bitmap);
        this.binding.publishButton.setEnabled(uri != null);
        if (Build.VERSION.SDK_INT >= 28 && bitmap instanceof AnimatedImageDrawable) {
            ((AnimatedImageDrawable) bitmap).start();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_publish_profile_picture);
        setSupportActionBar((Toolbar) this.binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        this.binding.cancelButton.setOnClickListener((v) -> this.finish());
        this.binding.secondaryHint.setVisibility(View.GONE);
        this.binding.accountImage.setOnClickListener((v) -> PublishProfilePictureActivity.chooseAvatar(this));
        Intent intent = getIntent();
        String uuid = intent == null ? null : intent.getStringExtra("uuid");
        if (uuid != null) {
            pendingConversationUuid.push(uuid);
        }
        this.binding.publishButton.setEnabled(uri != null);
        this.binding.publishButton.setOnClickListener(this::publish);
    }


    private void publish(View view) {
        binding.publishButton.setText(R.string.publishing);
        binding.publishButton.setEnabled(false);
        xmppConnectionService.publishMucAvatar(conversation, uri, this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            final CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                this.uri = result.getUri();
                if (xmppConnectionServiceBound) {
                    reloadAvatar();
                }
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Exception error = result.getError();
                if (error != null) {
                    ToastCompat.makeText(this, error.getMessage(), ToastCompat.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == REQUEST_CHOOSE_PICTURE) {
            if (resultCode == RESULT_OK) {
                cropUri(data.getData());
            }
        }
    }

    public void cropUri(final Uri uri) {
        if (Build.VERSION.SDK_INT >= 28) {
            this.uri = uri;
            reloadAvatar();
            if (this.binding.accountImage.getDrawable() instanceof AnimatedImageDrawable || this.binding.accountImage.getDrawable() instanceof FileBackend.SVGDrawable) {
                return;
            }
        }

        CropImage.activity(uri).setOutputCompressFormat(Bitmap.CompressFormat.PNG)
                .setAspectRatio(1, 1)
                .setMinCropResultSize(Config.AVATAR_SIZE, Config.AVATAR_SIZE)
                .start(this);
    }

    @Override
    public void onAvatarPublicationSucceeded() {
        runOnUiThread(() -> {
            ToastCompat.makeText(this, R.string.avatar_has_been_published, ToastCompat.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public void onAvatarPublicationFailed(@StringRes int res) {
        runOnUiThread(() -> {
            ToastCompat.makeText(this, res, ToastCompat.LENGTH_SHORT).show();
            this.binding.publishButton.setText(R.string.publish);
            this.binding.publishButton.setEnabled(true);
        });
    }
}
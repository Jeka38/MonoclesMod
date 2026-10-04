package eu.siacs.conversations.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import eu.siacs.conversations.ui.widget.AvatarView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityVcardBinding;
import eu.siacs.conversations.databinding.FragmentVcardAboutBinding;
import eu.siacs.conversations.databinding.FragmentVcardAddressBinding;
import eu.siacs.conversations.databinding.FragmentVcardGeneralBinding;
import eu.siacs.conversations.databinding.FragmentVcardPhotoBinding;
import eu.siacs.conversations.databinding.FragmentVcardWorkBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.VCard;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.xmpp.Jid;

/**
 * Shows a profile card ({@code vcard-temp} / vCard 4) with the same field set as Psi's info dialog:
 * general, work, address, about and photo.
 *
 * <p>The account's own profile is editable ({@code ACTION_EDIT_OWN} opens it straight in edit mode);
 * a roster contact or MUC occupant is shown read-only. MUC <em>room</em> cards are also read-only
 * here — they are edited through {@link ConferenceDetailsActivity}.
 */
public class VCardActivity extends XmppActivity
        implements XmppConnectionService.OnAccountUpdate {

    public static final String EXTRA_JID = "jid";
    public static final String EXTRA_ROOM = "room";
    public static final String EXTRA_NAME = "display_name";
    /** Intent action meaning "open the own profile in edit mode". */
    public static final String ACTION_EDIT_OWN = "eu.siacs.conversations.EDIT_PROFILE";

    private ActivityVcardBinding binding;
    private Account account;
    private Jid jid;
    private boolean mucRoom;
    private boolean own;
    private boolean editable;
    private VCard card = new VCard();
    private String accountJid;
    private String displayName;

    private ViewDataBinding[] pages;
    private FragmentVcardGeneralBinding general;
    private AvatarView previewPhoto;
    private AvatarView largePhoto;
    private View photoRow;
    private LinearLayout photoButtons;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_vcard);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        this.jid = Jid.ofEscaped(getIntent().getStringExtra(EXTRA_JID));
        this.mucRoom = getIntent().getBooleanExtra(EXTRA_ROOM, false);
        final String displayName = getIntent().getStringExtra(EXTRA_NAME);
        this.accountJid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        this.displayName = displayName;
        this.editable = ACTION_EDIT_OWN.equals(getIntent().getAction());
        updateTitle();
        buildPages();
        addTabs();
        setLoading(true);
    }

    private void updateTitle() {
        if (getSupportActionBar() == null) {
            return;
        }
        if (own || ACTION_EDIT_OWN.equals(getIntent().getAction())) {
            getSupportActionBar().setTitle(R.string.vcard_profile);
        } else if (displayName != null && !displayName.trim().isEmpty()) {
            getSupportActionBar().setTitle(displayName.trim());
        } else if (jid != null) {
            getSupportActionBar().setTitle(jid.asBareJid().toEscapedString());
        }
    }

    private void buildPages() {
        final LayoutInflater inflater = LayoutInflater.from(this);
        this.general = FragmentVcardGeneralBinding.inflate(inflater);
        final FragmentVcardWorkBinding work = FragmentVcardWorkBinding.inflate(inflater);
        final FragmentVcardAddressBinding address = FragmentVcardAddressBinding.inflate(inflater);
        final FragmentVcardAboutBinding about = FragmentVcardAboutBinding.inflate(inflater);
        final FragmentVcardPhotoBinding photo = FragmentVcardPhotoBinding.inflate(inflater);
        this.pages = new ViewDataBinding[] {general, work, address, about, photo};

        this.previewPhoto = general.vcardPhoto;
        this.photoRow = general.vcardPhotoRow;
        this.photoButtons = (LinearLayout) general.vcardPhotoChange.getParent();
        this.largePhoto = photo.vcardPhotoLarge;

        general.vcardPhotoChange.setOnClickListener(v -> choosePhoto());
        photo.vcardPhotoChange.setOnClickListener(v -> choosePhoto());
        general.vcardPhotoRemove.setOnClickListener(v -> removePhoto());
        photo.vcardPhotoRemove.setOnClickListener(v -> removePhoto());
        previewPhoto.setOnClickListener(v -> showPhotoDialog());
        largePhoto.setOnClickListener(v -> showPhotoDialog());

        showPage(0);
    }

    private void addTabs() {
        if (jid == null) {
            return;
        }
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.vcard_tab_general));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.vcard_tab_work));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.vcard_tab_address));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.vcard_tab_about));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.vcard_tab_photo));
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(final TabLayout.Tab tab) {
                showPage(tab.getPosition());
            }

            @Override
            public void onTabUnselected(final TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(final TabLayout.Tab tab) {
            }
        });
    }

    private void showPage(final int position) {
        binding.vcardPage.removeAllViews();
        binding.vcardPage.addView(pages[position].getRoot());
    }

    private void setLoading(final boolean loading) {
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.vcardPage.setVisibility(loading ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (xmppConnectionServiceBound) {
            // coming back from PublishProfilePictureActivity, which publishes the photo itself
            resolveAccount();
            loadCard();
        }
    }

    @Override
    protected void onBackendConnected() {
        resolveAccount();
        loadCard();
    }

    /** The account can only be looked up once the service is bound (i.e. not in onCreate). */
    private void resolveAccount() {
        if (accountJid == null) {
            return;
        }
        try {
            this.account = xmppConnectionService.findAccountByJid(Jid.ofEscaped(accountJid));
        } catch (final IllegalArgumentException ignored) {
            this.account = null;
        }
        this.own = account != null && jid != null
                && jid.asBareJid().equals(account.getJid().asBareJid());
        updateTitle();
        applyEditable();
        invalidateOptionsMenu();
    }

    @Override
    protected void refreshUiReal() {
        // the card is loaded once; edits are kept locally until saved
    }

    @Override
    public void onAccountUpdate() {
        if (own && !editable) {
            runOnUiThread(this::loadCard);
        }
    }

    private void loadCard() {
        if (jid == null || account == null) {
            setLoading(false);
            binding.empty.setVisibility(View.VISIBLE);
            return;
        }
        setLoading(true);
        binding.empty.setVisibility(View.GONE);
        xmppConnectionService.fetchVCard(account, jid, mucRoom, vcard -> runOnUiThread(() -> {
            card = vcard;
            setLoading(false);
            binding.empty.setVisibility(vcard.isEmpty() ? View.VISIBLE : View.GONE);
            bind(vcard);
        }));
    }

    private void bind(final VCard vcard) {
        general.vcardFullName.setText(vcard.fullName);
        general.vcardNickname.setText(vcard.nickName);
        general.vcardBirthday.setText(vcard.getDisplayableBirthday());
        general.vcardEmail.setText(vcard.email);
        general.vcardPhone.setText(vcard.phone);
        general.vcardHomepage.setText(vcard.url);
        ((FragmentVcardWorkBinding) pages[1]).vcardOrganization.setText(vcard.organization);
        ((FragmentVcardWorkBinding) pages[1]).vcardOrganizationUnit.setText(vcard.organizationUnit);
        ((FragmentVcardWorkBinding) pages[1]).vcardTitle.setText(vcard.title);
        ((FragmentVcardWorkBinding) pages[1]).vcardRole.setText(vcard.role);
        ((FragmentVcardAddressBinding) pages[2]).vcardStreet.setText(vcard.street);
        ((FragmentVcardAddressBinding) pages[2]).vcardExt.setText(vcard.extAddress);
        ((FragmentVcardAddressBinding) pages[2]).vcardCity.setText(vcard.city);
        ((FragmentVcardAddressBinding) pages[2]).vcardRegion.setText(vcard.region);
        ((FragmentVcardAddressBinding) pages[2]).vcardPcode.setText(vcard.postalCode);
        ((FragmentVcardAddressBinding) pages[2]).vcardCountry.setText(vcard.country);
        ((FragmentVcardAboutBinding) pages[3]).vcardNote.setText(vcard.note);
        updatePhotoViews(vcard);
    }

    @Nullable
    private Uri photoUri() {
        if (card == null || !card.hasPhoto()) {
            return null;
        }
        if (card.photoBase64 != null) {
            final String type = card.photoType == null ? "image/*" : card.photoType;
            return Uri.parse("data:" + type + ";base64," + card.photoBase64);
        }
        return Uri.parse(card.photoExternal);
    }

    private void updatePhotoViews(final VCard vcard) {
        final boolean hasPhoto = vcard != null && vcard.hasPhoto();
        final Uri uri = photoUri();
        if (uri != null) {
            Glide.with(this).load(uri).into(previewPhoto);
            Glide.with(this).load(uri).into(largePhoto);
        } else {
            Glide.with(this).clear(previewPhoto);
            Glide.with(this).clear(largePhoto);
        }
        if (!hasPhoto && account != null) {
            // The own photo lives in the PEP avatar node (XEP-0084/XEP-0153) and is usually absent
            // from vcard-temp; contacts and MUC occupants fall back to their cached avatar too.
            final AvatarService.Avatarable avatarable = avatarable();
            if (avatarable != null) {
                AvatarWorkerTask.loadAvatar(avatarable, previewPhoto, R.dimen.avatar_big);
                AvatarWorkerTask.loadAvatar(avatarable, largePhoto, R.dimen.avatar_big);
            }
        }
        previewPhoto.setVisibility(View.VISIBLE);
        photoRow.setVisibility(View.VISIBLE);
        photoButtons.setVisibility(editable ? View.VISIBLE : View.GONE);
    }

    private void applyEditable() {
        for (final ViewDataBinding page : pages) {
            setEnabled(page.getRoot(), editable);
        }
        photoButtons.setVisibility(editable ? View.VISIBLE : View.GONE);
    }

    @Nullable
    private AvatarService.Avatarable avatarable() {
        if (own) {
            return account;
        }
        if (jid == null) {
            return null;
        }
        if (mucRoom) {
            final Conversation conversation = xmppConnectionService.findConversation(account, jid, true);
            if (conversation != null && conversation.getMucOptions() != null) {
                final MucOptions.User user = conversation.getMucOptions().findUserByFullJid(jid);
                if (user != null) {
                    return user;
                }
            }
            return null;
        }
        return account.getRoster().getContact(jid.asBareJid());
    }

    private void setEnabled(final View view, final boolean enabled) {
        if (view instanceof EditText) {
            final EditText editText = (EditText) view;
            editText.setFocusable(enabled);
            editText.setFocusableInTouchMode(enabled);
            editText.setCursorVisible(enabled);
            if (!enabled) {
                editText.clearFocus();
            }
        } else if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setEnabled(group.getChildAt(i), enabled);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.vcard, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final boolean canEdit = own && account != null;
        final MenuItem edit = menu.findItem(R.id.action_edit_vcard);
        if (edit != null) {
            edit.setVisible(canEdit && !editable);
        }
        final MenuItem save = menu.findItem(R.id.action_save_vcard);
        if (save != null) {
            save.setVisible(editable);
        }
        final MenuItem discard = menu.findItem(R.id.action_discard_vcard);
        if (discard != null) {
            discard.setVisible(editable);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_edit_vcard) {
            editable = true;
            invalidateOptionsMenu();
            applyEditable();
            return true;
        } else if (id == R.id.action_save_vcard) {
            save();
            return true;
        } else if (id == R.id.action_discard_vcard) {
            editable = false;
            invalidateOptionsMenu();
            applyEditable();
            bind(card);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void choosePhoto() {
        final Intent intent = new Intent(this, PublishProfilePictureActivity.class);
        intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
        startActivity(intent);
    }

    private void removePhoto() {
        if (card == null) {
            card = new VCard();
        }
        card.photoBase64 = null;
        card.photoExternal = null;
        card.photoType = null;
        updatePhotoViews(card);
        if (own) {
            xmppConnectionService.deleteAvatar(account);
            Toast.makeText(this, R.string.vcard_photo_removed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showPhotoDialog() {
        final Uri uri = photoUri();
        if (uri == null) {
            return;
        }
        final ImageView imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        Glide.with(this).load(uri).into(imageView);
        new AlertDialog.Builder(this)
                .setTitle(R.string.vcard_photo_show)
                .setView(imageView)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void save() {
        final VCard vcard = new VCard();
        vcard.fullName = clean(general.vcardFullName);
        vcard.nickName = clean(general.vcardNickname);
        vcard.birthday = VCard.normalizeBirthday(textOf(general.vcardBirthday));
        vcard.email = clean(general.vcardEmail);
        vcard.phone = clean(general.vcardPhone);
        vcard.url = clean(general.vcardHomepage);
        final FragmentVcardWorkBinding work = (FragmentVcardWorkBinding) pages[1];
        vcard.organization = clean(work.vcardOrganization);
        vcard.organizationUnit = clean(work.vcardOrganizationUnit);
        vcard.title = clean(work.vcardTitle);
        vcard.role = clean(work.vcardRole);
        final FragmentVcardAddressBinding address = (FragmentVcardAddressBinding) pages[2];
        vcard.street = clean(address.vcardStreet);
        vcard.extAddress = clean(address.vcardExt);
        vcard.city = clean(address.vcardCity);
        vcard.region = clean(address.vcardRegion);
        vcard.postalCode = clean(address.vcardPcode);
        vcard.country = clean(address.vcardCountry);
        vcard.note = clean(((FragmentVcardAboutBinding) pages[3]).vcardNote);
        // the photo is managed through PublishProfilePictureActivity, keep whatever is attached
        vcard.photoBase64 = card == null ? null : card.photoBase64;
        vcard.photoExternal = card == null ? null : card.photoExternal;
        vcard.photoType = card == null ? null : card.photoType;
        xmppConnectionService.publishVCard(account, vcard);
        this.card = vcard;
        this.editable = false;
        invalidateOptionsMenu();
        applyEditable();
        Toast.makeText(this, R.string.vcard_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Nullable
    private static String textOf(@Nullable final TextView textView) {
        return textView == null || textView.getText() == null ? null : textView.getText().toString();
    }

    @Nullable
    private static String clean(@Nullable final TextView textView) {
        final String value = textOf(textView);
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

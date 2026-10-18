package eu.siacs.conversations.ui;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.rarepebble.colorpicker.ColorPickerView;

import java.io.File;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.whiteboard.WhiteboardManager;
import eu.siacs.conversations.xmpp.whiteboard.WhiteboardShape;
import eu.siacs.conversations.xmpp.whiteboard.WhiteboardView;
import me.drakeet.support.toast.ToastCompat;

/**
 * Collaborative SVG whiteboard for a 1:1 chat or a MUC room.
 *
 * <p>Opened from the contact/conversation context menu. The board is local by default; pressing
 * "Поделиться" starts a Jingle session ({@link WhiteboardManager}) over the XEP-0284 SXE transport,
 * after which local edits are broadcast and remote edits are applied to the canvas.
 */
public class WhiteboardActivity extends XmppActivity implements WhiteboardManager.OnWhiteboardUpdate {

    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_PEER = "peer";

    private static final int REQUEST_SEND_BOARD = 0x4d31;

    private static final float[] WIDTHS = {1f, 2f, 4f, 8f};
    private static final String DEFAULT_STROKE_COLOR = "#1a1a1a";
    /** Font size (document units) used when placing text on the board. */
    private static final float TEXT_FONT_SIZE = 48f;
    private static final String DEFAULT_FILL_COLOR = "#e53935";

    private WhiteboardView canvas;
    /** The toolbar doubles as the status line: its title is the board name, its subtitle the sync state. */
    private Toolbar syncStatus;
    private TextView emptyHint;
    private ImageButton widthButton;
    private ImageButton strokeButton;
    private ImageButton fillButton;
    private View strokeSwatch;
    private View fillSwatch;
    private ImageButton shareButton;
    private View toolsContainer;
    private ImageButton railToggle;
    private boolean railCollapsed = true;
    private int railReservePx = 0;

    private Account account;
    private Jid peer;
    private Conversation conversation;

    private String sessionId = null;
    private WhiteboardManager whiteboardManager;
    private boolean listenerRegistered = false;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whiteboard);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        configureActionBar(getSupportActionBar(), true);

        this.canvas = findViewById(R.id.wb_canvas);
        this.syncStatus = findViewById(R.id.toolbar);
        this.emptyHint = findViewById(R.id.wb_empty);
        this.shareButton = findViewById(R.id.wb_share);
        this.widthButton = findViewById(R.id.wb_width);
        this.strokeButton = findViewById(R.id.wb_stroke);
        this.fillButton = findViewById(R.id.wb_fill);
        this.strokeSwatch = findViewById(R.id.wb_stroke_swatch);
        this.fillSwatch = findViewById(R.id.wb_fill_swatch);
        this.toolsContainer = findViewById(R.id.wb_tools);
        this.railToggle = findViewById(R.id.wb_rail_toggle);

        canvas.setListener(new CanvasListener());
        applyThemeColors();
        canvas.setFillColor(DEFAULT_FILL_COLOR);
        canvas.setStrokeWidth(2f);
        canvas.setTool("pencil");

        wireTool(R.id.wb_tool_pencil, "pencil");
        wireTool(R.id.wb_tool_line, "line");
        wireTool(R.id.wb_tool_arrow, "arrow");
        wireTool(R.id.wb_tool_rect, "rect");
        wireTool(R.id.wb_tool_ellipse, "ellipse");
        wireTool(R.id.wb_tool_pan, "pan");
        wireTool(R.id.wb_tool_move, "edit");
        wireTool(R.id.wb_tool_text, "text");
        canvas.setTextRequestedListener((x, y) -> promptForText(x, y));
        // Keep the scrollable tool list sized to the available space so it can always be scrolled to
        // the bottom in both orientations. The activity declares configChanges="orientation|screenSize"
        // (no re-inflate on rotation), so recompute on every layout change instead of once.
        this.railReservePx = (int) (getResources().getDisplayMetrics().density * 74f);
        final View stage = findViewById(R.id.wb_stage);
        stage.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) -> clampToolsHeight(stage));
        clampToolsHeight(stage);

        findViewById(R.id.wb_send).setOnClickListener(v -> sendBoardToContact());
        findViewById(R.id.wb_undo).setOnClickListener(v -> canvas.undo());
        findViewById(R.id.wb_reset_view).setOnClickListener(v -> canvas.resetView());
        findViewById(R.id.wb_clear).setOnClickListener(v -> confirmClear());
        widthButton.setOnClickListener(v -> chooseWidth());
        strokeButton.setOnClickListener(v -> chooseColor(true));
        fillButton.setOnClickListener(v -> chooseColor(false));
        shareButton.setOnClickListener(v -> toggleSharing());
        railToggle.setOnClickListener(v -> toggleRail());
        wireTooltips();
        selectTool("pencil");
        updateColorSwatches();
        updateEmptyHint();
    }

    /** A long press on any rail button shows its name (some icons are not self-explanatory). */
    private void wireTooltips() {
        final int[] ids = {
                R.id.wb_tool_pencil, R.id.wb_tool_line, R.id.wb_tool_arrow, R.id.wb_tool_rect,
                R.id.wb_tool_ellipse, R.id.wb_tool_pan, R.id.wb_tool_move, R.id.wb_tool_text,
                R.id.wb_reset_view, R.id.wb_width, R.id.wb_stroke, R.id.wb_fill,
                R.id.wb_undo, R.id.wb_clear, R.id.wb_send, R.id.wb_share,
        };
        for (final int id : ids) {
            final View view = findViewById(id);
            view.setOnLongClickListener(v -> {
                final CharSequence text = v.getContentDescription();
                if (text != null && !text.toString().isEmpty()) {
                    android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * The board follows the app theme: the paper colour mirrors the primary background and the
     * low-contrast helper grid is tinted from the tertiary background.
     */
    private void applyThemeColors() {
        final int paper = StyledAttributes.getColor(this, R.attr.color_background_primary);
        canvas.setPageColor(paper);
        final int grid = StyledAttributes.getColor(this, R.attr.color_background_tertiary);
        canvas.setGridColor(withAlpha(grid, 0x40));
        // Keep the default ink visible on both light and dark paper.
        canvas.setStrokeColor(isDark(paper) ? "#f5f5f5" : DEFAULT_STROKE_COLOR);
    }

    private static boolean isDark(final int color) {
        final double luma =
                (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0;
        return luma < 0.5;
    }

    private static int withAlpha(final int color, final int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private void updateColorSwatches() {
        strokeSwatch.getBackground().mutate().setTint(parse(canvas.getStrokeColor(), Color.BLACK));
        fillSwatch.getBackground().mutate().setTint(parse(canvas.getFillColor(), Color.RED));
    }

    private void toggleRail() {
        railCollapsed = !railCollapsed;
        toolsContainer.setVisibility(railCollapsed ? View.GONE : View.VISIBLE);
        railToggle.setImageResource(
                railCollapsed ? R.drawable.ic_whiteboard_chevron : R.drawable.ic_whiteboard_chevron_right);
    }

    /**
     * Sizes the scrollable tool list to the stage so every tool can be reached by scrolling — in
     * portrait and in landscape alike. Called from a layout listener because the activity keeps the
     * same view tree across rotation (configChanges), so the list must be re-clamped on every resize.
     */
    private void clampToolsHeight(final View stage) {
        if (stage == null || toolsContainer == null) {
            return;
        }
        final int available = stage.getHeight() - railReservePx;
        if (available <= 0) {
            return;
        }
        final android.view.ViewGroup.LayoutParams params = toolsContainer.getLayoutParams();
        if (params.height != available) {
            params.height = available;
            toolsContainer.setLayoutParams(params);
        }
    }

    private void wireTool(final int id, final String tool) {
        findViewById(id).setOnClickListener(v -> selectTool(tool));
    }

    private void selectTool(final String tool) {
        canvas.setTool(tool);
        highlightTool(R.id.wb_tool_pencil, "pencil".equals(tool));
        highlightTool(R.id.wb_tool_line, "line".equals(tool));
        highlightTool(R.id.wb_tool_arrow, "arrow".equals(tool));
        highlightTool(R.id.wb_tool_rect, "rect".equals(tool));
        highlightTool(R.id.wb_tool_ellipse, "ellipse".equals(tool));
        highlightTool(R.id.wb_tool_pan, "pan".equals(tool));
        highlightTool(R.id.wb_tool_move, "edit".equals(tool));
        highlightTool(R.id.wb_tool_text, "text".equals(tool));
    }

    private void highlightTool(final int id, final boolean active) {
        final View view = findViewById(id);
        view.setAlpha(active ? 1.0f : 0.6f);
        view.setSelected(active);
    }

    @Override
    protected void onBackendConnected() {
        final Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        final String accountJid = intent.getStringExtra(EXTRA_ACCOUNT);
        final String peerJid = intent.getStringExtra(EXTRA_PEER);
        if (accountJid == null || peerJid == null) {
            finish();
            return;
        }
        this.account = xmppConnectionService.findAccountByJid(Jid.of(accountJid));
        this.peer = Jid.of(peerJid).asBareJid();
        if (account == null) {
            finish();
            return;
        }
        this.conversation = xmppConnectionService.find(account, peer);
        setTitle(getString(R.string.whiteboard_title_with, peer.asBareJid().toEscapedString()));
        this.whiteboardManager = xmppConnectionService.getWhiteboardManager();
        whiteboardManager.addListener(this);
        listenerRegistered = true;
        // Re-attach to a running session (e.g. after a rotation), otherwise start sharing right
        // away so the board synchronizes with the contact by default.
        final WhiteboardManager.Session session =
                xmppConnectionService.getWhiteboardManager().getSessionForPeer(account, peer);
        if (session != null) {
            attachSession(session);
        } else {
            startSharing();
        }
    }

    @Override
    protected void onDestroy() {
        if (listenerRegistered && whiteboardManager != null) {
            whiteboardManager.removeListener(this);
            listenerRegistered = false;
        }
        // Leaving the board screen: announce our departure. The board is kept alive while at least
        // one participant remains and is closed and fully deleted only when the **last** one leaves
        // ("last one out closes it"). `isFinishing()` guards against a system-initiated destroy,
        // which is not the user leaving. A cancelled board already detached, so this is a no-op then.
        if (isFinishing()) {
            final WhiteboardManager.Session session = currentSession();
            if (session != null && whiteboardManager != null) {
                whiteboardManager.leaveSession(session);
            }
        }
        super.onDestroy();
    }

    @Nullable
    private WhiteboardManager.Session currentSession() {
        if (sessionId == null || whiteboardManager == null) {
            return null;
        }
        return whiteboardManager.getSession(sessionId);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SEND_BOARD || resultCode != RESULT_OK || data == null) {
            return;
        }
        final List<Jid> jids = ChooseContactActivity.extractJabberIds(data);
        if (jids.isEmpty()) {
            return;
        }
        final String accountJid = data.getStringExtra(EXTRA_ACCOUNT);
        Account targetAccount = account;
        if (accountJid != null) {
            final Account chosen = xmppConnectionService.findAccountByJid(Jid.of(accountJid));
            if (chosen != null) {
                targetAccount = chosen;
            }
        }
        final boolean room = data.getBooleanExtra(ChooseContactActivity.EXTRA_PICKED_CONFERENCE, false);
        renderAndSendBoard(jids.get(0), targetAccount, room);
    }

    @Override
    protected void refreshUiReal() {
        // nothing periodic
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.whiteboard, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_whiteboard_cancel) {
            confirmCancelBoard();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Sharing ───────────────────────────────────────────────────────────

    /**
     * Sends the board as a PNG: asks for a contact or room, renders the drawing, writes it to the
     * app's private Files directory and attaches it to that conversation (uploaded via HTTP upload
     * when the account supports it).
     */
    private void sendBoardToContact() {
        if (canvas.getShapes().isEmpty()) {
            ToastCompat.makeText(this, R.string.whiteboard_send_empty, ToastCompat.LENGTH_SHORT).show();
            return;
        }
        final Intent intent = new Intent(this, ChooseContactActivity.class);
        intent.putExtra(ChooseContactActivity.EXTRA_SHOW_ENTER_JID, true);
        intent.putExtra(ChooseContactActivity.EXTRA_SELECT_MULTIPLE, false);
        // List bookmarked rooms too, so the board (a PNG) can be sent to a conference, and offer the
        // "hide offline" toggle so the list is not cluttered with offline contacts.
        intent.putExtra(ChooseContactActivity.EXTRA_SHOW_CONFERENCES, true);
        intent.putExtra(ChooseContactActivity.EXTRA_SHOW_HIDE_OFFLINE, true);
        intent.putExtra(ChooseContactActivity.EXTRA_TITLE_RES_ID, R.string.whiteboard_send_contact);
        if (account != null) {
            intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
        }
        startActivityForResult(intent, REQUEST_SEND_BOARD);
    }

    private void renderAndSendBoard(final Jid contactJid, final Account account, final boolean muc) {
        if (account == null || contactJid == null) {
            return;
        }
        final boolean isRoom = muc
                || xmppConnectionService.isMuc(account, contactJid)
                || account.getBookmark(contactJid.asBareJid()) != null;
        final Conversation conversation =
                xmppConnectionService.findOrCreateConversation(account, contactJid.asBareJid(), isRoom, true);
        if (conversation == null) {
            ToastCompat.makeText(this, R.string.whiteboard_send_failed, ToastCompat.LENGTH_SHORT).show();
            return;
        }
        // Ask for confirmation before rendering/uploading, so a mis-tap does not send the board.
        new AlertDialog.Builder(this)
                .setTitle(R.string.whiteboard_send_confirm_title)
                .setMessage(getString(R.string.whiteboard_send_confirm, conversation.getName()))
                .setPositiveButton(R.string.ok, (dialog, which) -> doRenderAndSendBoard(conversation))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doRenderAndSendBoard(final Conversation conversation) {
        final Toast progress = ToastCompat.makeText(this, R.string.whiteboard_sending, ToastCompat.LENGTH_SHORT);
        progress.show();
        new Thread(() -> {
            final Uri uri = writeBoardPng();
            final WhiteboardActivity activity = this;
            activity.runOnUiThread(() -> {
                if (uri == null) {
                    progress.cancel();
                    ToastCompat.makeText(activity, R.string.whiteboard_send_failed, ToastCompat.LENGTH_SHORT).show();
                    return;
                }
                activity.delegateUriPermissionsToService(uri);
                // Send as a file (not as a compressible image) so the PNG is uploaded via HTTP upload
                // exactly as rendered.
                activity.xmppConnectionService.attachFileToConversation(conversation, uri, "image/png", null,
                        new UiCallback<Message>() {
                            @Override
                            public void success(final Message message) {
                                activity.runOnUiThread(() -> {
                                    progress.cancel();
                                    ToastCompat.makeText(activity, R.string.whiteboard_sent, ToastCompat.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void error(final int errorCode, final Message message) {
                                activity.runOnUiThread(() -> {
                                    progress.cancel();
                                    ToastCompat.makeText(activity, errorCode, ToastCompat.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void userInputRequired(final android.app.PendingIntent pi, final Message message) {
                                // no interactive input for an image upload
                            }

                            @Override
                            public void progress(final int ignored) {
                            }

                            @Override
                            public void showToast() {
                            }
                        });
            });
        }).start();
    }

    /** Renders the board (2×) and writes it to a PNG file; returns a {@code file://} URI or null. */
    @Nullable
    private Uri writeBoardPng() {
        try {
            final android.graphics.Bitmap bitmap = canvas.exportBitmap(2f);
            final File dir = new File(getFilesDir(), "Files" + File.separator + "Sent");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            final File file = new File(dir, "whiteboard_" + System.currentTimeMillis() + ".png");
            try (final java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
            }
            bitmap.recycle();
            return Uri.fromFile(file);
        } catch (final Exception e) {
            return null;
        }
    }

    private void toggleSharing() {
        if (sessionId != null) {
            final WhiteboardManager.Session session =
                    xmppConnectionService.getWhiteboardManager().getSession(sessionId);
            if (session != null) {
                // Leaving sharing: if others remain they keep the board; if we were the last one it
                // is closed and fully deleted.
                xmppConnectionService.getWhiteboardManager().leaveSession(session);
            }
            detachSession();
            return;
        }
        startSharing();
    }

    /** Host a new session (sharing by default) and carry the local board into it. */
    private void startSharing() {
        if (account == null || peer == null) {
            return;
        }
        final boolean muc = conversation != null && conversation.getMode() == Conversation.MODE_MULTI;
        final String sid = xmppConnectionService.getWhiteboardManager().startSession(account, peer, muc);
        final WhiteboardManager.Session session =
                xmppConnectionService.getWhiteboardManager().getSession(sid);
        if (session != null && (session.host || session.muc)) {
            // Carry anything drawn before sharing into the shared document, so pressing Share
            // does not wipe the local board.
            for (final WhiteboardShape shape : canvas.getShapes()) {
                final String rid =
                        xmppConnectionService.getWhiteboardManager().addShape(sid, shape);
                canvas.setShapeRid(shape, rid);
            }
        }
        attachSession(session);
    }

    private void attachSession(final WhiteboardManager.Session session) {
        if (session == null) {
            return;
        }
        this.sessionId = session.sid;
        // We are looking at the board: count as a participant (the board lives while someone is on
        // it). Idempotent; re-registers us if we had left an earlier time.
        whiteboardManager.openBoard(session);
        // If we are the responding side of an invite (or opening a room board that already exists)
        // and have not connected yet, announce ourselves so the current document is offered.
        // Idempotent.
        if (!session.host || session.muc) {
            whiteboardManager.joinSession(session);
        }
        shareButton.setSelected(true);
        updateSyncStatus();
        refreshFromSession();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (intent == null || whiteboardManager == null || account == null) {
            return;
        }
        final String peerJid = intent.getStringExtra(EXTRA_PEER);
        if (peerJid == null) {
            return;
        }
        final WhiteboardManager.Session session =
                whiteboardManager.getSessionForPeer(account, Jid.of(peerJid));
        if (session != null) {
            setIntent(intent);
            attachSession(session);
        }
    }

    private void detachSession() {
        this.sessionId = null;
        shareButton.setSelected(false);
        updateSyncStatus();
    }

    private void refreshFromSession() {
        if (sessionId == null) {
            return;
        }
        final List<WhiteboardShape> shapes =
                xmppConnectionService.getWhiteboardManager().shapes(sessionId);
        canvas.loadShapes(shapes);
        updateEmptyHint();
    }

    private void updateSyncStatus() {
        final boolean shared = sessionId != null;
        shareButton.setSelected(shared);
        shareButton.setImageResource(R.drawable.ic_whiteboard_share);
        // The board name stays the title; the sync state goes into the toolbar subtitle.
        syncStatus.setSubtitle(shared ? R.string.whiteboard_shared : R.string.whiteboard_not_shared);
    }

    private void updateEmptyHint() {
        emptyHint.setVisibility(canvas.getShapes().isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onWhiteboardUpdated(final String sid) {
        if (sid == null) {
            return;
        }
        if (sid.equals(sessionId)) {
            runOnUiThread(() -> {
                refreshFromSession();
                updateSyncStatus();
            });
            return;
        }
        // Safety net: an update for this peer's session under a different id (e.g. the peer started
        // the session first). Re-attach so the live edits keep flowing.
        final WhiteboardManager.Session session =
                xmppConnectionService == null ? null : xmppConnectionService.getWhiteboardManager().getSession(sid);
        if (session != null && peer != null && peer.asBareJid().equals(session.peer.asBareJid())) {
            runOnUiThread(() -> attachSession(session));
        }
    }

    @Override
    public void onWhiteboardEnded(final String sid) {
        if (sid == null || !sid.equals(sessionId)) {
            return;
        }
        // The board was ended (the last participant left, or the peer/room closed it): detach and
        // leave the screen so the user is not left looking at a board that no longer exists.
        runOnUiThread(() -> {
            detachSession();
            if (!isFinishing()) {
                finish();
            }
        });
    }

    // ── Toolbar dialogs ───────────────────────────────────────────────────

    private void chooseWidth() {
        final String[] labels = new String[WIDTHS.length];
        for (int i = 0; i < WIDTHS.length; i++) {
            labels[i] = getString(R.string.whiteboard_width_px, (int) WIDTHS[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.whiteboard_line_width)
                .setItems(labels, (dialog, which) -> canvas.setStrokeWidth(WIDTHS[which]))
                .show();
    }

    private void chooseColor(final boolean stroke) {
        final ColorPickerView picker = new ColorPickerView(this);
        picker.setColor(parse(stroke ? canvas.getStrokeColor() : canvas.getFillColor(),
                stroke ? Color.BLACK : Color.RED));
        picker.showAlpha(false);
        picker.showHex(false);
        picker.showPreview(true);
        new AlertDialog.Builder(this)
                .setTitle(stroke ? R.string.whiteboard_line_color : R.string.whiteboard_fill_color)
                .setView(picker)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    final String hex = String.format("#%06X", 0xFFFFFF & picker.getColor());
                    if (stroke) {
                        canvas.setStrokeColor(hex);
                    } else {
                        canvas.setFillColor(hex);
                    }
                    updateColorSwatches();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static int parse(final String value, final int fallback) {
        try {
            return Color.parseColor(value);
        } catch (final IllegalArgumentException e) {
            return fallback;
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.whiteboard_clear)
                .setMessage(R.string.whiteboard_clear_confirm)
                .setPositiveButton(R.string.ok, (dialog, which) -> canvas.clearBoard())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Cancels the whole board: clears the drawing (broadcast so the peer sees it cleared too), ends
     * the shared session and closes the screen.
     */
    private void confirmCancelBoard() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.whiteboard_cancel)
                .setMessage(R.string.whiteboard_cancel_confirm)
                .setPositiveButton(R.string.ok, (dialog, which) -> cancelBoard())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void cancelBoard() {
        // clearBoard() removes each shape locally and calls onShapeRemoved, which broadcasts the
        // removal to the peer while the session is still attached.
        canvas.clearBoard();
        if (sessionId != null) {
            final WhiteboardManager.Session session =
                    xmppConnectionService.getWhiteboardManager().getSession(sessionId);
            if (session != null) {
                xmppConnectionService.getWhiteboardManager().endSession(session);
            }
            detachSession();
        }
        finish();
    }

    /** Asks for multiline text and places it on the board at the tapped point. */
    private void promptForText(final float worldX, final float worldY) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        final int pad = (int) (getResources().getDisplayMetrics().density * 16);
        final android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(R.string.whiteboard_tool_text)
                .setView(container)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    // addText() already notifies the OnShapeListener (which publishes it); calling
                    // shareShape() here too would create a SECOND record and duplicate the text.
                    canvas.addText(worldX, worldY, input.getText().toString(), TEXT_FONT_SIZE);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Publishes a locally created shape to the shared document. */
    private void shareShape(final WhiteboardShape shape) {
        if (sessionId == null) {
            return;
        }
        canvas.setShapeRid(shape,
                xmppConnectionService.getWhiteboardManager().addShape(sessionId, shape));
    }

    // ── Canvas callbacks ──────────────────────────────────────────────────

    private final class CanvasListener implements WhiteboardView.OnShapeListener {
        @Override
        public void onShapeAdded(final WhiteboardShape shape) {
            updateEmptyHint();
            shareShape(shape);
        }

        @Override
        public void onShapeUpdated(final WhiteboardShape shape) {
            if (sessionId == null || shape.rid.isEmpty()) {
                return;
            }
            xmppConnectionService.getWhiteboardManager().updateShape(sessionId, shape.rid, shape);
        }

        @Override
        public void onShapeRemoved(final WhiteboardShape shape) {
            updateEmptyHint();
            if (sessionId == null || shape.rid.isEmpty()) {
                return;
            }
            xmppConnectionService.getWhiteboardManager().removeShape(sessionId, shape.rid);
        }
    }

    public static Intent create(final android.content.Context context, final String accountJid, final String peerJid) {
        final Intent intent = new Intent(context, WhiteboardActivity.class);
        intent.putExtra(EXTRA_ACCOUNT, accountJid);
        intent.putExtra(EXTRA_PEER, peerJid);
        return intent;
    }
}

package eu.siacs.conversations.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.rarepebble.colorpicker.ColorPickerView;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.whiteboard.WhiteboardManager;
import eu.siacs.conversations.xmpp.whiteboard.WhiteboardShape;
import eu.siacs.conversations.xmpp.whiteboard.WhiteboardView;

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

    private static final float[] WIDTHS = {1f, 2f, 4f, 8f};
    private static final String DEFAULT_STROKE_COLOR = "#1a1a1a";
    private static final String DEFAULT_FILL_COLOR = "#e53935";

    private WhiteboardView canvas;
    private TextView syncStatus;
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
        this.syncStatus = findViewById(R.id.wb_sync_status);
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
        wireTool(R.id.wb_tool_rect, "rect");
        wireTool(R.id.wb_tool_ellipse, "ellipse");
        wireTool(R.id.wb_tool_pan, "pan");

        findViewById(R.id.wb_done).setOnClickListener(v -> canvas.finishDrawing());
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
                R.id.wb_tool_pencil, R.id.wb_tool_line, R.id.wb_tool_rect,
                R.id.wb_tool_ellipse, R.id.wb_tool_pan, R.id.wb_reset_view,
                R.id.wb_width, R.id.wb_stroke, R.id.wb_fill, R.id.wb_undo,
                R.id.wb_clear, R.id.wb_done, R.id.wb_share,
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

    /** Collapses the tool rail down to just the toggle button. */
    private void toggleRail() {
        railCollapsed = !railCollapsed;
        toolsContainer.setVisibility(railCollapsed ? View.GONE : View.VISIBLE);
        railToggle.setImageResource(
                railCollapsed ? R.drawable.ic_whiteboard_chevron : R.drawable.ic_whiteboard_chevron_right);
    }

    private void wireTool(final int id, final String tool) {
        findViewById(id).setOnClickListener(v -> selectTool(tool));
    }

    private void selectTool(final String tool) {
        canvas.setTool(tool);
        highlightTool(R.id.wb_tool_pencil, "pencil".equals(tool));
        highlightTool(R.id.wb_tool_line, "line".equals(tool));
        highlightTool(R.id.wb_tool_rect, "rect".equals(tool));
        highlightTool(R.id.wb_tool_ellipse, "ellipse".equals(tool));
        highlightTool(R.id.wb_tool_pan, "pan".equals(tool));
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
        super.onDestroy();
    }

    @Override
    protected void refreshUiReal() {
        // nothing periodic
    }

    // ── Sharing ───────────────────────────────────────────────────────────

    private void toggleSharing() {
        if (sessionId != null) {
            final WhiteboardManager.Session session =
                    xmppConnectionService.getWhiteboardManager().getSession(sessionId);
            if (session != null) {
                xmppConnectionService.getWhiteboardManager().endSession(session);
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
        if (session != null && session.host) {
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
        // If we are the responding side of an invite and have not connected yet, announce
        // ourselves so the host offers the current document. Idempotent.
        if (!session.host) {
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
        syncStatus.setText(shared ? R.string.whiteboard_shared : R.string.whiteboard_not_shared);
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

    // ── Canvas callbacks ──────────────────────────────────────────────────

    private final class CanvasListener implements WhiteboardView.OnShapeListener {
        @Override
        public void onShapeAdded(final WhiteboardShape shape) {
            updateEmptyHint();
            if (sessionId == null) {
                return;
            }
            final String rid = xmppConnectionService.getWhiteboardManager().addShape(sessionId, shape);
            canvas.setShapeRid(shape, rid);
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

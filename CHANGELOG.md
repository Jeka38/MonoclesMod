### Changelog

#### Version 1.9.1.66
* **code blocks are now a rounded card with a header** (Telegram-like) instead of a bare tinted rectangle: a lighter header band with a hairline divider, a muted `code c++` label and a copy glyph, a darker monospaced body, rounded corners, padding on all sides (including air on the right of the longest line), and the card scaling with the message font. Tapping the block copies the code to the clipboard. Both XEP-0393 preformatted blocks and XEP-0394 `bcode` use it

#### Version 1.9.1.65
* **fix: a drawing made in Psi+ with the pencil / simple brush was painted as a solid filled blob in Monocles** — Qt's SVG generator (which Psi uses) serialises a *solid transparent* brush as `fill="#000000" fill-opacity="0"`, and Psi's pencil uses exactly that brush (the default fill colour is transparent). The board only looked at the fill **colour**, saw black and filled the whole freehand path. `WhiteboardShape` now carries `fill-opacity` and treats a zero-opacity fill as unfilled (also `fill="none"`/`transparent`), and a translucent fill is drawn with its opacity applied

#### Version 1.9.1.64
* **fix: a whiteboard opened in Monocles still showed no invitation in Psi** — Monocles sent a bare Jingle `session-initiate`, but Psi (and the other whiteboard clients) start a board from inside a chat through **XEP-0353 Jingle Message Initiation**: a `<propose/>` to the contact's bare JID, and the `session-initiate` only after a `<proceed/>` from the resource that takes the call. Monocles now does the same — it proposes, then sends `session-initiate` to the **answering full JID** (a Jingle IQ to a bare JID is rejected by the server). The proposal id is used as the Jingle `sid` (as XEP-0353 requires) instead of the old deterministic id, which could collide with the peer's still-known session. As the responder Monocles answers `<ringing/>` + `<proceed/>`; crossing proposals are resolved with the XEP-0166 tie-break; and it falls back to a direct invite after 10 s for a peer that implements only the minimal profile

#### Version 1.9.1.63
* **fix: a whiteboard opened in Monocles never reached Psi** — `jingleTarget` fell back to `Contact.getMostAvailableResource()`, which is a *display* helper that wraps the resource in parentheses (`"(" + resource + ")"`). The Jingle IQ was therefore addressed to `` user@host/(pc) `` — a nonexistent resource — and the invite vanished (the reverse direction worked because Psi proposes to the bare JID). The resource is now taken from the presence map's plain keys

#### Version 1.9.1.62
* **fix: the peer showed no whiteboard invitation when Monocles opened the board** — `Session.host` was derived from a bare-JID comparison (`hostFor`: the smaller JID) but was also used to decide who sends `session-accept`. Opening a board whose JID ranks us "larger" left `host=false` for a session **we** had started, so `attachSession` sent an unsolicited `session-accept` (plus a `<connect/>`) with no preceding `session-initiate`, and Psi — seeing an accept for a session it never offered — showed nothing. Roles now follow the profile/Iris: the **Jingle initiator is the host** (`Session.initiator`, XEP-0284 §4.1); `session-accept` is only sent by the responder and `<connect/>` only by the joiner. The JID-derived `hostFor()` was removed

#### Version 1.9.1.61
* **fix: our drawing fell outside Psi's whiteboard canvas** — Monocles used a 1280×720 document while the whiteboard profile (and Iris/Psi) use `viewBox="0 0 600 400"`. The `viewBox` is a **shared document attribute** and Psi sizes its scene from whatever arrives, so our strokes at x>600 landed outside its canvas. The canvas now defaults to the profile's 600×400 and, per session, adopts the negotiated `viewBox` (`WhiteboardManager.documentSize` → `WhiteboardView.setDocumentSize`), so both sides draw into the same coordinate space

#### Version 1.9.1.60
* **fix: Psi's drawing was invisible on the whiteboard** — `WhiteboardManager.shapes()` matched the shared document's element name against `"path"` and read only a `d` attribute, so Psi's shape tools (`<polygon>`, and likewise `<polyline>/<rect>/<circle>/<ellipse>/<line>`) were skipped before rendering and never appeared, even though our own strokes showed up in Psi. Every drawable SVG element is now converted to the equivalent path data; metadata (`<title>/<desc>/<defs>/<g>`) is still ignored

#### Version 1.9.1.59
* **fix: the whiteboard still did not synchronize with Psi when the invitation came as a XEP-0353 `<propose/>`** — the proposal pre-creates the session under the very `sid` the following `session-initiate` reuses, so the offer took the "session already exists" branch, which never read the content — and therefore never saw `senders="none"` (the frozen board Psi uses). The edit-permission request was consequently never sent, the document stayed frozen on Psi's side and every stroke was dropped. The offered `senders`/content identity is now read in **both** branches

#### Version 1.9.1.58
* **fix: the whiteboard still did not synchronize with Psi** — Psi opens the whiteboard of a call with `<content … senders="none">`, and per the whiteboard profile `senders` lists **who may edit**: the peer then ignores every edit it receives (Iris gates the application of incoming edits on `mayEdit(peerRole)`), so the board looked connected but all strokes were silently dropped. Monocles ignored `senders`. It now records the negotiated value (`Session.senders`, plus `Session.initiator` since the attribute names roles, not JIDs), skips sending edits it knows are ignored, and asks the peer to widen it to `both` with a Jingle `content-modify` (re-requested after a `session-accept`/`content-modify` if needed)

#### Version 1.9.1.57
* **fix: the collaborative whiteboard did not synchronize with Psi** — Monocles sent its Jingle `session-accept` before acknowledging the peer's `session-initiate`. XEP-0166 requires the acknowledgement first, and Psi (Iris) enforces it strictly: it answered `<bad-request/>` to the accept, so the session never became active and **every `<sxe/>` edit was ignored** (the board opened on both sides, but nothing was drawn across). The IQ result is now sent before dispatching the invite. Also fixed: the `session-accept` now repeats the offered `<host/>` (the initiator) instead of naming ourselves

#### Version 1.9.1.56
* **fix: crash on update to 1.9.1.55** ("Unable to create service … duplicate column name: retractedAt"). The XEP-0424 `retractedAt` column was added in *two* migrations: the monocles side-table migration (`monoclesMigrate`, which runs from `onConfigure` and got there first) and the `oldVersion < 62` block in the main `onUpgrade`. The second `ALTER TABLE … ADD COLUMN retractedAt` then failed. The column is now added in exactly one place (the monocles migration); the main DB version stays at 62 so devices that already ran the broken build upgrade cleanly

#### Version 1.9.1.55
* **Message retraction (XEP-0424)** — withdrawing a message is now done with the modern **v1** form (`<retract xmlns='urn:xmpp:message-retract:1' id='…'/>`), so other clients see the standardization placeholder instead of an ordinary message. The incoming side accepts both v1 and the legacy v0.3 (`<apply-to xmlns='urn:xmpp:fasten:0'><retract/></apply-to>`), advertises both in caps, and matches the retracted id against UUID / origin-id / **stanza-id** / edit ids (the old lookup never checked the stanza-id, so a MUC retraction like `<retract id='1792766308589286'/>` was never applied). The retracted row shows **«Это сообщение было отозвано (дата и точное время)»** — the exact retraction time (stored in a new `retractedAt` column) — both in the chat bubble and in the chat list, and survives a reload. A MUC retraction can now only remove a message by the same occupant. The menu's Retract and Delete actions were unified (the old Retract handler blanked and re-sent the body)

#### Version 1.9.1.54
* Muji group call: the "More" popup no longer covers the "More" button — `showAsDropDown` anchors at the button's *bottom* edge, so the popup was left overlapping it. Positioning now uses explicit screen coordinates (`showAtLocation`) and places the popup's bottom edge above the button's top, still clamped inside the display

#### Version 1.9.1.53
* Muji group call: the "More" popup no longer puts a shared grey panel behind its actions — the switch-camera and whiteboard entries are now **stand-alone round buttons formatted exactly like the main call controls** (grey circle, white icon), stacked vertically on a transparent popup

#### Version 1.9.1.52
* Muji group call: the "More" popup (and the control bar) can no longer leave the screen — the popup is measured and size-capped against the display and its drop-down offset is clamped horizontally, so the buttons never poke past the screen edge (the "More" button sits at the right edge). The five-button control bar is also scaled down only when it would not fit the smallest displays

#### Version 1.9.1.51
* Muji group call: the "More" popup now stacks its actions **vertically** and draws them as **flat icon buttons without the grey circle plate** (`MujiFlatButton`) — the icons are tinted with the theme's text colour and sit on the popup card surface

#### Version 1.9.1.50
* Muji group call: the switch-camera action was still missing on devices with two cameras — it was hidden whenever the shared camera track was not running (the camera only exists once video is contributed), so it disappeared exactly when the camera was off. Switchability now comes from a cached device camera enumeration (`Camera2`/`Camera1`, no capture), and `switchCamera()` starts the shared camera first so a flip before enabling video is not a silent no-op; a failed flip now shows "Could not switch camera"

#### Version 1.9.1.49
* Muji group call: the switch-camera action is back and now works in a solo call as well — it is read from the shared conference camera instead of the per-participant connections, so it no longer disappears when nobody else has joined yet. The "More" popup now shows **icon buttons** (switch camera, whiteboard) instead of plain text rows

#### Version 1.9.1.48
* fix: the system permission dialog's buttons were unresponsive — the manifest declared `android.permission.SYSTEM_ALERT_WINDOW` ("display over other apps") although no code ever uses an overlay window, and Android's tapjacking protection then swallows taps on a permission dialog while the app holds that permission (a known problem on EMUI/MIUI). The unused permission was removed

#### Version 1.9.1.47
* Muji group call: the control buttons are now a **centred** compact group with equal margins, so the red hang-up button is exactly in the middle and the others are evenly spaced to its left and right on any screen width

#### Version 1.9.1.46
* Muji group call: mic, camera, the red hang-up and the speaker buttons are now **always visible**; the control bar is a five-slot row with equal weights, so the hang-up button is centred with the others evenly spaced left and right. Switch camera and whiteboard moved into a "More" overflow popup, and the camera button works even before anyone else joins (an audio-only call can be upgraded to video, requesting the camera permission first)

#### Version 1.9.1.45
* WebXDC removed entirely: the "Choose WebXDC" attach menu entries, the `WebxdcStore` WebView activity (and its manifest entry), the in-chat `WebxdcPage` mini-app, the `WebxdcUpdate` store, the XEP-`urn:xmpp:webxdc:0` handling in `MessageParser`, the `monocles.webxdc_updates` DB table and its accessors, the icons/themes/layouts/strings, and the now-unused `org.tomlj` dependency. A received `.xdc` file is just an ordinary file now

#### Version 1.9.1.44
* fix: a message containing a link with a `/me` substring (e.g. `https://mega.nz/…`) was mangled — the `/me` command was detected in the middle of the URL (the `//me` of `https://mega…`) and replaced with `* nick`, rendering `https://* nickga.nz/…`. `Message.getMeCommandIndex()` now requires `/me` to be a standalone token at the start of a line (only whitespace before it, whitespace/end-of-line after it)

#### Version 1.9.1.43
* whiteboard: an invite sent as an XEP-0353 Jingle Message Initiation `<propose>` (`urn:xmpp:jingle:apps:svg-whiteboard:0`) is now handled — `JingleConnectionManager.deliverMessage` only accepted all-RTP proposals, so a whiteboard proposal was ignored and the "Open board" button never appeared; the board now surfaces with the usual notice and the proposal is answered with `<proceed/>`, and a `<retract/>`/`<reject/>` withdraws it

#### Version 1.9.1.42
* whiteboard: "Send board" can now be sent to a **conference room** (the contact picker lists bookmarked rooms, and the result marks a room pick so the conversation is created in MUC mode) and the picker gained a **"hide offline"** menu toggle (persisted in the `hide_offline` preference), so the list is no longer cluttered with offline contacts

#### Version 1.9.1.41
* whiteboard: the board's creator now owns its lifetime — when the creator leaves the board (or the room), the board closes for everyone; joining a board does not create a new one (the same room-wide session is reused); a board cancellation really ends the shared session on the other side (1:1 `session-terminate`, room `<close/>`) and closes the screen for all participants; the room creator is advertised on the `<sxe/>` (`creator`) and converged to the smallest occupant JID so every device agrees

#### Version 1.9.1.40
* SIMS (XEP-0385) incoming support: a received `<media-sharing>` is typed from its media-type (image/audio/video/file) right away, the best download source is chosen among all `<sources>` (HTTP > AES-GCM > BobTransfer `cid:` > Jingle), the media-type is used when a URL has no usable extension, and a downloaded file is **verified against the shared hashes** (mismatched content is discarded). `urn:xmpp:sims:1` is now advertised in caps

#### Version 1.9.1.39
* whiteboard: fix the missing "Started a shared whiteboard" message in a 1:1 chat — the previous idempotency guard reused *any* existing board notice, so a leftover one from an earlier board suppressed the peer's new board (now the guard applies to rooms only, and a fresh 1:1 notice is always posted). The button is also surfaced now when our session already exists (a missed/duplicate invite) and when a 1:1 board is discovered from the peer's SXE traffic

#### Version 1.9.1.38
* whiteboard: sending the board as a PNG now asks for confirmation before uploading
* whiteboard: **Start board** is back in the 1:1 chat's overflow menu (it was only reachable through the paperclip popup)
* whiteboard: ending a room board now clears the `<sxe/>` presence advertisement, so re-entering the room no longer re-shows the "Started a shared whiteboard" button; the notice is also idempotent (never duplicated)

#### Version 1.9.1.37
* whiteboard: the rail's "Done" button is now **Send** — pick a contact or room and the board is rendered to a PNG (2×, no grid/handles) and uploaded (HTTP upload) into that chat
* whiteboard: the separate **Move** and **Scale** tools are merged into one classic **Edit** tool (four-way-arrows icon) — drag a shape to move it, drag any of the **eight handles** to resize it freely and non-uniformly in any direction

#### Version 1.9.1.36
* whiteboard: no more empty messages in the room — a board stroke was sent as a `groupchat` with an invisible body (U+2063), which the room archived and other clients (and our own history) rendered as an empty bubble. Room and 1:1 payloads now carry **no body** at all (the XEP examples and our own MUC chat-state messages do the same) plus a `no-store` hint; a MUC relays a bodyless groupchat but does not add it to the room history. A one-time DB migration drops any message whose whole body is that invisible separator

#### Version 1.9.1.35
* whiteboard: stop flooding stream management — every board edit is a `MessagePacket`, and upstream asks for a stream-management ack (`<r/>`) after *every* outgoing message with an id, so drawing produced a constant `<r/>`/`<a/>` barrage. Board `<state>` edits now send with `MessagePacket.setRequestAck(false)`; they stay reliable via the periodic keep-alive ack and the stanza queue

#### Version 1.9.1.34
* whiteboard: group (MUC) boards — starting a board in a room now posts the same "Open board" notice into the room (with the starter's nick and a themed bubble), so every member sees the button; the same notice is posted when another participant starts one, and a member joining after the board started sees it too (discovered from the occupant's presence)
* whiteboard: fix room board synchronization — every participant is a plain participant (no "host" fighting), snapshots are **merged** instead of replacing the drawing (shared document root), and room payloads carry an invisible fallback body so MUC servers do not drop them
* whiteboard: board notifications now follow the conversation's notification settings (a muted or mention-only room stays quiet); the old always-on invite notification was removed

#### Version 1.9.1.33
* whiteboard: added an arrow tool (a line with an arrowhead; the head scales with the stroke width) next to the line tool — it syncs as a normal `<path>` so it renders/moves on the peer too

#### Version 1.9.1.32
* whiteboard: the "Open board" chat notice now notifies like a normal 1:1 text message (unread badge + system notification) and its bubble follows the app theme (`color_bubble_light`/`color_bubble_dark`) instead of a fixed light style

#### Version 1.9.1.31
* whiteboard: the text tool now uses the conventional capital-"T" icon (as in graphics editors) instead of horizontal lines

#### Version 1.9.1.30
* whiteboard: fix duplicated text — a placed text was published twice (once by `addText` → `onShapeAdded`, once by `promptForText`), so moving it left a stale copy behind on the peer

#### Version 1.9.1.29
* whiteboard: all whiteboard icons (`ic_whiteboard_*`) now tint from the theme (`@color/whiteboard_icon` → `?attr/text_Color_Main`) instead of hardcoded white, so they stay visible as menu icons on a light theme

#### Version 1.9.1.28
* whiteboard: added "Draw with contact" to the composer's paperclip (quick-attach) popup — it was only in the overflow attach submenu before, so it did not appear where expected

#### Version 1.9.1.27
* whiteboard: the "Open board" chat message is now removed when the board is cancelled/closed (both when you cancel and when the peer ends the session)
* whiteboard: the tool rail follows the app theme — icons/labels use `text_Color_Main`, the selected tool uses a themed `whiteboard_selected_background`, and the rail card uses `whiteboard_rail_background`
* whiteboard: the cancel-board button moved from the tool rail into the toolbar (right-aligned)

#### Version 1.9.1.26
* whiteboard: moved "Draw with contact" from the conversation overflow (three-dot) menu into the **attach file** button's submenu (`attach_whiteboard`), next to the other attachment actions

#### Version 1.9.1.25
* whiteboard: fix the 1:1 invite being rejected — a `session-initiate` addressed to the peer's bare JID is refused by the server (`service-unavailable`), so no board message ever arrived; the invite now targets the peer's full JID (`jingleTarget`: a whiteboard-capable resource, else the most available one)
* whiteboard: a 1:1 board is now invited by **both** sides (previously only the deterministic host sent the invite, so opening the board from the other side silently did nothing); a duplicate invite for a session we already have is accepted without creating a second board or a second chat notice

#### Version 1.9.1.24
* whiteboard: fix 1:1 invites never arriving — `WhiteboardManager.isWhiteboardJingle` looked for `<content>` as a direct child of the IQ, but it lives inside `<jingle>`, so the whiteboard branch was dead and the invite was rejected as `unsupported-info` (no session, no chat notice)

#### Version 1.9.1.23
* whiteboard: fix the 1:1 whiteboard chat notice not appearing — `MessageAdapter.getViewTypeCount()` was 5 but the new `WHITEBOARD` view type is 5, so the row was out of range; the count is now 6

#### Version 1.9.1.22
* whiteboard: a 1:1 contact starting a board now posts a **normal chat message** ("Started a shared whiteboard.") with an "Open board" button instead of a push notification; no push is sent

#### Version 1.9.1.21
* whiteboard: a contact starting a 1:1 whiteboard no longer force-opens the board on your device — it posts a notification instead (title "Whiteboard · <contact>", tap to open); the session is still joined so the drawing is live when you tap

#### Version 1.9.1.20
* whiteboard: the sync state ("shared / local") moved from a floating overlay into the "Whiteboard" toolbar as its subtitle; the overlay badge is gone

#### Version 1.9.1.19
* whiteboard: the tool list now re-clamps to the available height on every layout change, so it can be scrolled all the way through in landscape as well (the activity keeps its view tree across rotation)

#### Version 1.9.1.18
* whiteboard: "Cancel board" button — clears the drawing (broadcast, so the peer's board clears too), ends the shared session and closes the screen

#### Version 1.9.1.17
* whiteboard: removed the separate "select object" button (the move tool now selects too); each tool in the bottom-left rail shows its name under the icon; the tool list scrolls

#### Version 1.9.1.16
* whiteboard: fix moved/scaled objects not reaching the other side — a shape's first `transform` (and any other attribute that did not exist yet) was sent as a `<set>`, but the peer had never seen that record id so it silently dropped it; new attributes are now announced with `<new parent="…"/>` (existing ones still use `<set>`)

#### Version 1.9.1.15
* whiteboard: select / move / scale tools for objects (tap to select, drag to move, drag the bottom-right handle to scale) — edits are broadcast as the shape's SVG `transform`, so they sync to other clients; text tool (multiline input placed as filled glyph-outline vector paths, so it renders and syncs like any other shape)
* whiteboard: fix object moving — a reload (remote edit / snapshot) now clears the stale selection, and move/scale drag the current selection from anywhere with a nearest-shape fallback, so dragging actually works on a phone

#### Version 1.9.1.14
* whiteboard: apply the SVG `transform` attribute (move / rotate / scale) of shapes received from other clients — a shape edited remotely (e.g. moved in Stanza) now moves on this end too; previously only create/delete were handled, so remote transforms were recorded but never rendered

#### Version 1.9.1.13
* fix "return to ongoing call": returning to a 1:1 call from the chat/details screen used a wrong `instanceof` so the call screen opened without a connection — the hang-up button did nothing and the call could not be ended

#### Version 1.9.1.12
* collaborative SVG whiteboard per contact / room (Jingle app `urn:xmpp:jingle:apps:svg-whiteboard:0` + XEP-0284 SXE): pinch-zoom canvas, pencil/line/rectangle/ellipse brushes, width and colour; opened from the contact context menu and the 1:1 conversation menu, **shared and synchronized by default** — opening the board on one side opens it on the other end too; 1280×720 themed canvas (uniform cover scale: true circles, even brush thickness) with drawing clamped to the board, collapsible bottom-left tool rail, reset-view button, shape fill, long-press tooltips; the board can also be opened from the 1:1 call and the Muji group-call screens

#### Version 1.8.8.5
* ad-hoc commands: parse XEP-0050 allowed actions from the <actions> element; proper Next/Previous/Cancel/Finish buttons and spec-valid action values so multi-step commands (e.g. RSS transports) advance past category selection
* redesigned file downloads: HTTP auto-download and manual downloads save to the public Download folder with the original filename; downloaded files get an "Open file" button in the chat
* service management dialog: action menu derived from disco#info features and identities; falls back to all actions if disco#info fails
* service discovery: editable server field to browse any service, node-aware browsing, bold service names, back navigation one level at a time
* service management dialog: registration (XEP-0077), ad-hoc commands (XEP-0050), user search (XEP-0055), search results can be added to the roster
* service management dialog: system back button navigates one step back instead of closing to the service overview
* sort conversation list by last message option, ignoring MUC status messages
* highlight conversation rows with the account color only when all accounts are active

#### Version 1.1
* presettings correction to avoid connection problems
* some text corrections
* added optional monocles registration link
* changed appicon
* changed registration and usage for monocles users only

#### Version 1.0
* initial release

### Changelog

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

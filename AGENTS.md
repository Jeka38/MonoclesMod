# Monocles mod — AGENTS.md

## What this is

XMPP chat client for Android — a Conversations fork (via Monocles Chat). Java + Kotlin, Gradle 8.8 / AGP 8.5.2, Kotlin 1.9.10.

## Build

JVM target is 17 (set via `sourceCompatibility`/`targetCompatibility`). Only JDK 21 is installed and compiles fine to target 17. Export `JAVA_HOME` first, e.g.:

```bash
export JAVA_HOME=/home/eugene/.jdks/jbr-21.0.11
export PATH=$JAVA_HOME/bin:$PATH
```

Focused compile/verify (git flavor, debug):

```bash
./gradlew compileGitDebugJavaWithJavac
```

Closest thing to a test suite is lint (this is the CI `test` job — no unit/instrumentation tests exist):

```bash
./gradlew lintGitDebug
```

APKs land in `build/outputs/apk/<flavor>/`. `git/release/` and `playstore/release/` hold local (gitignored) release artifacts.

Build Git (F-Droid) flavor:

```bash
./gradlew assembleGit
```

Build Play Store flavor:

```bash
./gradlew assemblePlaystore
```

Note: the first `assembleGit` invocation occasionally fails on a compile/resource race — re-running the identical command usually succeeds.

## Product flavors

| Flavor     | applicationId         | FCM push | Migration info |
|------------|----------------------|----------|----------------|
| `playstore` | `de.monocles.mod`    | yes      | hidden         |
| `git`       | `de.monocles.mod`    | no       | shown          |

ProGuard + shrinking (`-dontobfuscate`) are enabled on **both** debug and release — but only when signing properties (`mStoreFile`, `mStorePassword`, `mKeyAlias`, `mKeyPassword`) are set in `gradle.properties`. Without them, default build types are used (no minification).

## Source layout

- `src/main/` — shared code (upstream Conversations + mod additions)
- `src/playstore/` — Play Store flavor overrides (FCM, manifest)
- `src/git/` — Git flavor overrides (manifest, media/storage permissions)
- `src/debug/` — debug-only resource overrides (logo, res)
- `de.monocles.mod.*` — mod-specific additions; `eu.siacs.conversations.*` — upstream core; `p32929.easypasscodelock` — third-party lockscreen library
- `README.md` is **in Russian** — it is the fork's feature changelog (incl. a Muji summary). `docs/` holds upstream (English) Conversations docs.

**Namespace is `eu.siacs.conversations`** (so the generated `R` and `BuildConfig` live there) even though the applicationId is `de.monocles.mod`. New mod classes must still `import eu.siacs.conversations.R`.
**Launcher activity is flavor-specific:** git = `src/git/java/de/monocles/mod/ui/StartUI.java`; playstore = `src/playstore/java/de/monocles/chat/StartUI.java` (directory does not match its `de.monocles.mod.ui` package — leave it). The main manifest references `de.monocles.mod.ui.StartUI`, so touch both sources if you change the launcher.
**Core service:** `eu.siacs.conversations.services.XmppConnectionService`

## Key gotchas

- **No tests exist.** No `src/test/` or `src/androidTest/` directory. `libs:AXML` has a `// TODO UNIT TESTS` comment.
- **No formatter/static-analysis tasks exist** (no ktlint, detekt, spotless, checkstyle, or `.editorconfig`). Gradle lint (`lintGitDebug`) is the only static check.
- **SDK levels / packaging:** `compileSdk`/`targetSdk` 34, `minSdk` 23 — guard any API newer than 23 behind version checks. Multi-dex and ABI splits (per-ABI version-code offsets, `universalApk`) are enabled.
- **Conversations restored from DB have a null `account` until attached** (`XmppConnectionService.restoreFromDatabase`). During early connection setup (`switchOverToTls`), a live message can be processed against such a conversation. `Message.getContact()`/`Conversation.getContact()` must remain null-safe (they intentionally return `null`/short-circuit when `account` is null rather than NPE). Same rule applies to `Conversation.getBookmark()` and `MucOptions.getProposedNick()` (called from the `MucOptions` constructor via `MessageParser` → `getMucOptions()`), which previously NPE'd on `account.getBookmark(...)`. `NotificationService.push` must likewise handle a null `account` (`message.getConversation().getAccount()` can be null for the restore window): http file-size checks (`HttpDownloadConnection.FileSizeChecker`) run on a background thread and can call `push` on a still-unattached conversation — `push` short-circuits to `pushFromBacklog` for catchup and otherwise **skips** the notification (the notification builder chain derefs the account) instead of crashing. `Conversation.getName()` is likewise null-safe in both branches: the MUC (`nextCounterpart`) branch skips the `account.getXmppConnection()` look-up when `account` is null; the 1:1 branch falls back to the JID local part instead of dereffing a null `Contact`. This covers the `NotificationService.buildMultipleConversation` → `getName()` path triggered by `markRead()` during `switchOverToTls`. `MyLinkify.addLinks(Editable, Account, Jid)` also short-circuits on a null `account`, and the link-preview HEAD probe skips a null `HttpUrl.parse(...)` result.
- **Live 1:1 message dedup:** `MessageParser.onMessagePacketReceived` skips the duplicate check unless the message is a MUC-delay, private, or carries a serverMsgId/remoteMsgId, or catchup is in progress. Re-delivered live messages lacking a serverMsgId rely on `remoteMsgId` matching in `Message.similar()` to be dropped.
- **Data binding + view binding** are both enabled.
- **Translations are Crowdin-managed** (`crowdin.yml`): source is `src/main/res/values/strings.xml`, output `values-%two_letters_code%/`. Only `values-ru-rRU/strings.xml` is a real translation; `MissingTranslation` is disabled, so adding English-only strings builds fine.
- **Signing config** is conditional — requires `mStoreFile`, `mStorePassword`, `mKeyAlias`, `mKeyPassword` project properties. Without them, signing configs are omitted.
- **JVM target:** 17, with Java 8+ desugaring enabled (`coreLibraryDesugaringEnabled true`).
- **Gradle daemon is disabled** (`org.gradle.daemon=false` in `gradle.properties`). Parallel builds are on (`org.gradle.parallel=true`).
- **Lint:** `abortOnError false`; disables `ExtraTranslation`, `MissingTranslation`, `InvalidPackage`, `MissingQuantity`, `AppCompatResource`, `RestrictedApi`. But `StringFormatInvalid` and `StringFormatMatches` are escalated to errors — the only two checks that can fail the build.
- **Layout variants diverge.** `layout-land/fragment_conversation.xml` can differ significantly from portrait. Always update both variants when adding views to `fragment_conversation.xml`.
- **`settings.gradle` references `:libs:xmpp-addr`** but that directory does not exist — only `libs/AXML` is present. This is a stale reference.
- **CircleCI downloads a stale WebRTC `.aar`** (`libwebrtc-m85.aar`) that is no longer used — WebRTC is now a Maven dependency (`im.conversations.webrtc:webrtc-android:119.0.1`).

## CI references

- **CircleCI:** `test` job runs `lintGitDebug`, `build` job runs `assembleGit`. Uses F-Droid CI Docker image. Current. (`store_artifacts` still points at the nonexistent `build/outputs/apk/standard` path — harmless.)
- **GitLab CI:** uses `assembleStandard`, which does **not** exist in the current `build.gradle` (only `git`/`playstore` flavors) — the pipeline is broken/stale.
- **GitHub Actions:** builds `QuicksyFree*` and `ConversationsFree*` variants — these flavor names do **not** exist in the current `build.gradle`; the workflow is outdated for this fork.

## Mod-specific behaviors (do not "fix" back to upstream)

- **File downloads are redesigned:** HTTP transfers save to the **public Download folder** (`Download/<APP_DIRECTORY>/`) using the **original filename**. Name priority is the SIMS `<name>` element (`Message.FileParams.getName()`) first, then the last URL path segment (`HttpDownloadConnection.originalFilenameFromUrl`). Downloaded files show an "Open file" button in the chat.
- The chat shows the original filename under a downloaded/openable file — but **deliberately not** under image/video previews (`displayMediaPreviewMessage`). Do not "fix" the preview to show the name.
- OMEMO-encrypted HTTP downloads and background fetches (webxdc previews, link images) are **deliberately not** redirected — they keep private storage/temp paths.
- Legacy Jingle (XEP-0234) transfers intentionally keep the upstream private-storage behaviour.
- `ServiceManagementDialog` derives its action menu from disco#info; if `disco#info` fails (error/timeout) it falls back to showing all actions rather than collapsing to just "Copy JID".
- **MUC conversation name fallback** (`Conversation.getName()`): `muc_name` → bookmark name → **JID local part (before `@`)**. The room topic/subject is deliberately **not** used as a fallback — do not "fix" it back.
- **Notification default is decoupled from MUC privacy/anonymity**: `Conversation.alwaysNotify()` defaults to `Config.ALWAYS_NOTIFY_BY_DEFAULT` for *all* rooms — it does **not** implicitly become true for private non-anonymous rooms. So toggling "make real JIDs visible" (`muc#roomconfig_whois=anyone`) in the conference configurator never flips a room into "notify on all messages" ("individual notifications"); the per-room choice (always / mention-only / mention+reply / never, `ConferenceDetailsActivity` `mNotifyStatusClickListener`) is the only thing that controls it.
- **MUC topic strip** (preference `show_muc_topic`, default `false`): when enabled, `ConversationFragment` shows the room topic in a narrow `muc_topic` strip above the message list (added to **both** `layout/` and `layout-land/fragment_conversation.xml`); tapping it opens the conference details (`switchToMUCDetails`).
- **Per-status presence priority**: edited in **Expert settings → Presence → "Presence priorities"** (`preferences.xml` key `presence_priorities`; `SettingsActivity.editPresencePriorities()` → `showPresencePriorityDialog()`, `layout/dialog_presence_priority.xml`). With one account the dialog opens directly; with several it first asks which account (`choose_account`). One field per presence status — Online / Away / XA / DND (each -128..127, default 50 / 30 / 20 / 10, stored in `Account.keys` via `KEY_PRESENCE_PRIORITY_ONLINE/_AWAY/_XA/_DND`, `getPresencePriority()` / `getPresencePriority(Presence.Status)` / `setPresencePriority(Presence.Status, int)`; CHAT/OFFLINE map to the Online bucket). Saving persists with `databaseBackend.updateAccount(account)` and sends presence immediately (`sendPresence(account)`). **These fields were deliberately moved out of the account editor** — `EditAccountActivity` no longer reads, writes, or has UI for them; do not add them back. `PresenceGenerator.selfPresence()` emits `<priority>` (only when non-zero) for the value matching the account's **current** status — a status change (`changeStatus` → `sendPresence`) picks up the new value immediately, and MUC join presences go through the same path. Saving account options reconnects the account (`updateAccount` → `reconnectAccountInBackground`).
- **Live-location movement track**: a live-location share renders a **polyline** of the actual route, not just the current point. The sender keeps the full history: `MessageParser` stores the *previous geo body* (`getRawBody()`, since `getBody()` is `""` for geo URIs) into the message edits in `LiveLocationManager.correctMessage` (replaced the old trim-to-first-entry behaviour). `GeoHelper.getTrackPoints(Message)` reconstructs points from edit bodies + the raw body (dedups points <10 m apart); `createGeoIntentsFromMessage` adds them as the `track` parcelable-array extra, and `ShowLocationActivity` draws the track as the `Polyline` (blue, 8 dp) and auto-frames it on first display. `LocationActivity.clearMarkers` also removes polyline overlays.
- **Image link previews in chat** (`displayTextMessage`): first tries `extractFirstImageUrl` (URL path must end in a literal image extension), then falls back to `extractFirstUrl` for any http(s) link that dominates the body. Failure to render hides the preview (`onLoadFailed`), leaving just the clickable link.
- **Animated avatar blink fix** (`AvatarWorkerTask`): when `play_gif_inside` is enabled and the avatar cache misses, the original code fell back to an async worker that briefly showed a blank placeholder before the avatar loaded — a visible "blink" on every presence update. The fix tries a synchronous `get(cachedOnly=false)` first so the avatar appears immediately; the async worker is now only used for truly absent avatars (e.g. newly joined occupants without cached vCards).

## XEP-0272 Multiparty Jingle (Muji)

The deepest mod. A mesh audio/video conference per MUC room; `MujiConference`/`MujiConferenceManager` (`eu.siacs.conversations.xmpp.jingle`) own the state, `MujiConferenceActivity` + `MujiParticipantGridView` + `ParticipantView` own the UI.

### Lifecycle, sessions, resources
- **Trigger:** MUC overflow item `action_muji_conference` ("Start group call" / "Group call", `ConversationFragment.toggleMujiConference`) → audio-only / audio+video chooser (`dialog_muji_call_type.xml`) → join and open `MujiConferenceActivity`. Latecomers can join from the `muji_call_bar` panel (see below). Join requests `RECORD_AUDIO` (+`CAMERA` when available) with request code `REQUEST_START_MUJI_CONFERENCE`, plus optional `BLUETOOTH_CONNECT` on Android 12+ (`PermissionUtils.removeBluetoothConnect` keeps it non-blocking).
- All peer connections share one `PeerConnectionFactory`/`EglBase`/microphone track **and one camera track** via `WebRTCResources` (refcounted, `getOrCreateVideoTrack()`, owned by the conference), so the mesh opens one audio device and one camera, not N. MUC presence carries `<muji>` (`Namespace.JINGLE_MUJI`), advertised in caps; per-occupant contents are parsed into `MucOptions.User.getMuji()`.
- An incoming Jingle `session-initiate` with `<muji room>` is only accepted if the sender is an occupant of an active local conference (`JingleConnectionManager.deliverMujiSessionInitiate`), then auto-accepted through a Muji-tagged `JingleRtpConnection` (`isMuji()`); such sessions are excluded from `isBusy()` and never create a Telecom call.
- Muji sessions deliberately **do not create a 1:1 conversation or call-log messages** per participant (`JingleRtpConnection` reuses the MUC `Conversation` and skips `writeLogMessage` when `isMuji()`), so participants don't show up in Chats.
- When a Muji voice chat is created or active in a room, the conversation is **bumped to the top of the chat list** (`Conversation.setMujiCallTimestamp` incorporated into `getSortableTime`/`getSortableTimeExcludingStatusMessages`, set on local join in `MujiConference` and remotely in `PresenceParser` on muji-to-active transitions) so it surfaces like a new message.
- `XmppConnectionService.startForegroundOrCatch` must add `FOREGROUND_SERVICE_TYPE_CAMERA` for video calls/conferences (otherwise background capture fails with `Camera2Session … device policy`).
- Known limitations (stage 4): presence content descriptions still carry no payload-type mapping (codecs are negotiated per-session by WebRTC SDP), and remote audio playout is not explicitly mixed.

### "Video chat" call bar (stage 4)
- Telegram-style `muji_call_bar` panel (added to **both** `layout/` and `layout-land/fragment_conversation.xml` below `muc_topic`, with the ViewPager re-anchored to it) appears/refreshes via MUC-presence → `updateConversationUi()` → `ConversationFragment.updateMujiCallBar()` only when a call is in the room — i.e. this user is in a conference (`isMujiConferenceActive(conversation)`) or any occupant advertises `<muji>` (`mujiParticipantCount` scans `MucOptions.getUsers(false)`).
- Shows "Video chat" + participant count and an accent-filled Join/Open button (`app:backgroundTint="?attr/colorAccent"`, white text, matching the `UnreadCountCustomView` accent convention) pinned to the right edge at 95% of the panel height (set in code); a dismiss "Close" `ImageButton` (`muji_call_bar_close`, X icon) hides the panel until the participant count changes or a conference becomes active (`dismissedMujiCallBarCount`). Tapping Join runs the same `toggleMujiConference()` flow.
- Latecomers who join the room *after* the call started also see the panel: `MujiConference.onOccupantPresence` re-sends our `<muji>` presence the first time it sees an occupant who is not in the call (so servers that do not replay custom elements still deliver it), and `PresenceParser` calls `updateConversationUi()` whenever an occupant's `<muji>` changes (previously only on user-count/online/status changes, so a call starting in a full room did not refresh the panel).
- If the conference can't start (`WebRTCResources.create` fails → conference removed from the registry) or the audio/video chooser dialog ends in a permission denial, the fragment **does not open `MujiConferenceActivity`** — instead `NotificationService.notifyMujiJoinFailed(res)` posts a heads-up on the dedicated `muji` channel (`MUJI_CHANNEL_ID`, `MUJI_NOTIFICATION_ID`) without raising a new window.

### Controls, self-view, mic/video mute
- Circular `MaterialButton` controls styled with the `MujiCircleButton` style: mic / camera / switch-camera / speakerphone / red hang-up; camera controls hidden for audio-only; the toolbar is hidden while the conference is active.
- **Self-view tile** (`SELF_KEY`, label "You") renders the shared local camera track (`MujiConference.getLocalVideoTrack()`, mirrored on the front camera) whenever the conference was joined with video. When nobody else has joined yet the UI shows the same grid + controls (mic/speaker visible; state taken from the self tracks via `updateControls`), so a solo call looks exactly like a call with two or more participants.
- The mic button works even with no participants: `MujiConferenceActivity.toggleMicrophone` delegates to `MujiConference.setMicrophoneEnabled`/`isMicrophoneEnabled`, which mute/unmute the **shared conference `AudioTrack`** (`WebRTCResources.audioTrack`) — the same track every peer session references, so the mute persists for participants joining later.
- **Camera off** falls back to the avatar: `MujiConferenceActivity.toggleVideo` removes `Media.VIDEO` from the `MujiConference` media (re-sent in the `<muji>` presence so remote tiles pick it up) while keeping the shared track alive; `bindVideo`/`bindSelfVideo` treat a participant/user that no longer advertises video as avatar-only. Turning video back on re-adds `Media.VIDEO` and re-enables the track/camera. The switch-camera button (`muji_switch_camera`) shows only while the camera is switchable; speaker (`muji_speaker`) toggles `AudioManager.setSpeakerphoneOn` in `MODE_IN_COMMUNICATION` (off by default; theme accent tint when on).

### Grid, tiles, expand
- One tile per participant (`MujiParticipantGridView`, a plain `ViewGroup` — deliberately not a `RecyclerView`, to avoid recycling the video renderers), each bound to that connection's `getRemoteVideoTrack()`.
- No `ScrollView`; `MujiParticipantGridView.onMeasure` fills the whole remaining area — it picks the column count whose cells come closest to the video's 16:9 aspect ratio (small penalty for a partial last row, which is stretched to full width), so tiles always tile the area with no empty space (video centre-cropped to the cell, avatars centred).
- **Tap-to-expand:** tapping any participant or self tile expands it to fill the grid; the remaining participants render as **floating round avatar thumbnails** (no frame/background) in a vertical column to the left, overlapping the expanded tile; a second tap collapses. `MujiParticipantGridView.setExpandedChild()` tracks the expanded child; thumb width = min(30% grid width, 180 dp), 16:9. `ParticipantView.setThumbnailStyle(boolean)` toggles full tile (label, 96 dp avatar, `color_background_secondary` background) vs thumbnail (label hidden, transparent, 48 dp avatar; renderer + speaking border clipped to a centred 48 dp circle via `setCircleDiameterPx(int)`). `MujiConferenceActivity.applyExpandedStyling()` is called from `refresh()` and the tap listener to keep styles in sync.
- **Z-order fix:** in expanded mode thumbnails (self is always child index 0) overlap the expanded tile, which is a later child and would otherwise be drawn on top of them, hiding the self tile. `applyExpandedStyling()` raises each thumbnail's `translationZ` (2 dp on the tile root; tiles use `TextureViewRenderer`, a normal view, so ordinary Z ordering applies) above the expanded tile; on collapse every tile's Z resets to 0.
- Muji tiles use a custom `TextureViewRenderer` (`ui/widget/TextureViewRenderer.java`, an `EglRenderer`+`SurfaceTexture` renderer modelled on Signal's) instead of `SurfaceViewRenderer`: a `SurfaceView` cannot be clipped to a circle and lives in its own compositor layer, so it could not render circular video thumbnails. It fills the view centre-cropped via `EglRenderer.setLayoutAspectRatio(viewAspect)` (no `setScalingType`/hardware-scaler, so the old `BLASTBufferQueue … rejecting buffer` `SurfaceViewRenderer` workaround no longer applies to Muji; `RtpSessionActivity` still uses `SurfaceViewRenderer` and keeps it).

### Avatars and speaking indicator
- Tiles without a remote video track show the occupant's **avatar** instead of a black tile (`MujiConferenceActivity.bindVideo` → `showAvatar`; circular `ShapeableImageView` styled with `ShapeAppearanceOverlay.PhotoRound`, loaded via `AvatarWorkerTask` from the matching `MucOptions.User`; tile background `?attr/color_background_secondary`). `participantAvatarable` also falls back to the roster `Contact` for the participant's real JID when no `MucOptions.User` matches.
- **Speaking indicator:** `MujiConferenceActivity` polls `JingleRtpConnection.getRemoteAudioLevel` every 10 ms (`WebRTCWrapper` reads the loudest `audioLevel` from per-peer stats, skipping our own `media-source`), and `ParticipantView.updateSpeakingIndicator()` runs a `ValueAnimator` — around the avatar (scale 1.0→1.05, stroke = 5% of the avatar size) when no video is shown, or an alpha pulse of a green `SpeakingBorderView` around the video tile (rounded rectangle for a full tile, circle for a thumbnail) when the renderer is visible. `setSpeaking`/`showAvatar`/`showRenderer` all call it, idempotently so a running animation is never restarted. Gated by the `play_gif_inside` preference ("play animated images").

### Video-capability fallback (avatar vs black tile)
- `MujiConferenceActivity.remoteProvidesVideo`: when the last `<muji>` a participant advertised is only a `<preparing/>` placeholder (or absent) — which happens for the party *already in the room* when the other joined, because the newcomer's active voice/video presence is not reliably delivered to it — the tile does **not** trust the capability element; it falls back to the resolved Jingle session, so a received remote video track (`JingleRtpConnection.getRemoteVideoTrack()` / `WebRTCWrapper.remoteVideoTrack`) is authoritative and video shows.
- For a **totally absent** `<muji>`, the two cases are told apart with `MucOptions.User.hasEverSeenMuji()` (`mujiSeen`, sticky set on the first non-null `setMuji`): if the occupant *ever* advertised a conference and now no longer does, they left the call — the stale renderer must fall back to the **avatar** (otherwise the dead session renders a black window); if we *never* received any muji presence from them (join-while-in-room), the session is authoritative and video shows. An explicit active `<muji>` without video (camera toggled off) still forces the avatar — do not "fix" that back.
- Independently of presence, a tile falls back to the avatar when its bound video track stops delivering frames: `framesLively` treats a track as dead after `FRAME_TIMEOUT_MS` (3 s) without a frame, and a track that never delivered a first frame after `FIRST_FRAME_GRACE_MS` (5 s) after (re)binding — covers a remote disabling the camera when its `<muji>` update is lost, and prevents a permanently black tile when a renegotiated track never renders (self tile uses the same check).
- Because WebRTC keeps producing **black frames** when a remote disables its camera (it does not stop the stream), `framesLively` also treats a track as dead after `BLACK_FRAME_TIMEOUT_MS` (2 s) of continuously black frames: the frame sink samples the I420 luma at most once per `BLACK_FRAME_CHECK_INTERVAL_MS` (1 s), and `isBlack()` compares average luma against `BLACK_FRAME_LUMA_THRESHOLD` (24). This is what makes the avatar appear on the *other* devices when the MUC server does not rebroadcast the camera-off `<muji>` presence.

### Audio routing, chime, wakelock, logging
- **Join/leave sounds are muted for Muji** (`CallIntegration.setAudioCuesEnabled(false)` on every Muji-tagged `JingleRtpConnection`, plus `CallIntegration.playMujiJoinSound()` for the one-off chime): the `connected.ogg` beep, ring-back, drop/busy/error tones no longer fire per peer session on every device. The only audible cue is a single join chime decided by `JingleRtpConnection.shouldPlayMujiJoinChime` → `MujiConference.shouldPlayJoinChime(locallyInitiated)`, gated by `existingMujiParticipantsAtCreation`: the **conference starter** (count 0) hears a chime when the **first** participant joins; a **later joiner** (count ≥ 2) hears one when their first locally-initiated session connects; the **first joiner** of an empty-looking call (count 1) stays silent — claimed once per conference via an `AtomicBoolean`. Established participants and non-joining MUC members stay silent; no drop tone on leave.
- **Bluetooth routing lives entirely in `MujiConference`** (`initAudioRouting()` from `join()`, teardown in `leave()`), so it **survives activity destruction** — leaving the voice-chat window keeps the call running in the background with BT headset audio/mic. On API 31+ it uses `AudioManager.setCommunicationDevice()` (legacy `startBluetoothSco()`+`setBluetoothScoOn()` only below S), requested when the speaker is off (delegated from `MujiConferenceActivity.toggleSpeaker()`), tracked via an `ACTION_SCO_AUDIO_STATE_CHANGED` receiver on the application context (unregistered in `leave()`), and released (`clearCommunicationDevice`) when the speaker is on or the conference ends — otherwise it falls back to the earpiece. A dropped SCO connection is **re-requested** while the call is active and speaker off (bounded `MAX_SCO_RETRIES`/`SCO_RETRY_DELAY`, reset on connect or a manual speaker toggle).
- A BT headset connecting *after* the conference starts triggers `ACTION_ACL_CONNECTED` → retry budget reset + `requestBluetoothRouteRetry` (delayed retries until the BT audio profile is up). On devices that do not deliver `ACTION_ACL_CONNECTED` (Android 13+/EMUI), a periodic `bluetoothRoutingRunnable` (every 2 s) calls `requestBluetoothSco()` — silent when no BT device is present, logs only on successful routing — so a mid-call headset is picked up within ~2 s. The poll also detects the headset disappearing and resets `scoRequested`, since the legacy `ACTION_SCO_AUDIO_STATE_CHANGED` broadcast does not fire on the `setCommunicationDevice()` path.
- A **partial wakelock** (`POWER_MANAGER.PARTIAL_WAKE_LOCK`, tag `monocles:MujiConference:<room>`) is acquired in `initAudioRouting()` and released in `leave()` so screen-off does not stall the WebRTC audio path.
- Debug builds write a stanza/camera trace to `files/muji.log` via `MujiLog.log(filesDir, msg)` (Huawei/EMUI strips `Log.d` from logcat; read with `adb shell run-as de.monocles.mod cat files/muji.log`). **Note:** `MujiLog.log()` is the *only* thing that reaches that file — plain `Log.d`/`Log.w` do not — and it is a no-op unless `BuildConfig.DEBUG` and `filesDir` is non-null. `MujiConference.logBt()` is the Bluetooth-route helper; it writes **only to `MujiLog`** (not to logcat).

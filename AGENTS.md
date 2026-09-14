# Monocles mod — AGENTS.md

## What this is

XMPP chat client for Android — a Conversations fork. Java + Kotlin, Gradle 8.8 / AGP 8.5.2.

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

APKs land in `build/outputs/apk/<flavor>/`. `git/` and `playstore/` `release/` folders hold local (gitignored) release artifacts.

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

**Namespace is `eu.siacs.conversations`** (so the generated `R` and `BuildConfig` live there) even though the applicationId is `de.monocles.mod`. New mod classes must still `import eu.siacs.conversations.R`.
**Launcher activity is flavor-specific:** git = `src/git/java/de/monocles/mod/ui/StartUI.java`; playstore = `src/playstore/java/de/monocles/chat/StartUI.java` (directory does not match its `de.monocles.mod.ui` package — leave it). The main manifest references `de.monocles.mod.ui.StartUI`, so touch both sources if you change the launcher.
**Core service:** `eu.siacs.conversations.services.XmppConnectionService`

## Key gotchas

- **No tests exist.** No `src/test/` or `src/androidTest/` directory. `libs:AXML` has a `// TODO UNIT TESTS` comment.
- **Conversations restored from DB have a null `account` until attached** (`XmppConnectionService.restoreFromDatabase`). During early connection setup (`switchOverToTls`), a live message can be processed against such a conversation. `Message.getContact()`/`Conversation.getContact()` must remain null-safe (they intentionally return `null`/short-circuit when `account` is null rather than NPE). Same rule applies to `Conversation.getBookmark()` and `MucOptions.getProposedNick()` (called from the `MucOptions` constructor via `MessageParser` → `getMucOptions()`), which previously NPE'd on `account.getBookmark(...)`.
- **Live 1:1 message dedup:** `MessageParser.onMessagePacketReceived` skips the duplicate check unless the message is a MUC-delay, private, or carries a serverMsgId/remoteMsgId, or catchup is in progress. Re-delivered live messages lacking a serverMsgId rely on `remoteMsgId` matching in `Message.similar()` to be dropped.
- **Data binding + view binding** are both enabled.
- **Translations are Crowdin-managed** (`crowdin.yml`): source is `src/main/res/values/strings.xml`, output `values-<two_letters_code>/`. Only `values-ru-rRU` is a real translation; `MissingTranslation` is disabled, so adding English-only strings builds fine.
- **Signing config** is conditional — requires `mStoreFile`, `mStorePassword`, `mKeyAlias`, `mKeyPassword` project properties. Without them, signing configs are omitted.
- **JVM target:** 17, with Java 8+ desugaring enabled (`coreLibraryDesugaringEnabled true`).
- **Gradle daemon is disabled** (`org.gradle.daemon=false` in `gradle.properties`). Parallel builds are on (`org.gradle.parallel=true`).
- **Lint:** `abortOnError false`; disables `ExtraTranslation`, `MissingTranslation`, `InvalidPackage`, `MissingQuantity`, `AppCompatResource`, `RestrictedApi`. But `StringFormatInvalid` and `StringFormatMatches` are escalated to errors — the only two checks that can fail the build.
- **Layout variants diverge.** `layout-land/fragment_conversation.xml` can differ significantly from portrait. Always update both variants when adding views to `fragment_conversation.xml`.
- **`settings.gradle` references `:libs:xmpp-addr`** but that directory does not exist — only `libs/AXML` is present. This is a stale reference.
- **CircleCI downloads a stale WebRTC `.aar`** (`libwebrtc-m85.aar`) that is no longer used — WebRTC is now a Maven dependency (`im.conversations.webrtc:webrtc-android:119.0.1`).

## CI references

- **CircleCI:** `test` job runs `lintGitDebug`, `build` job runs `assembleGit`. Uses F-Droid CI Docker image. Current.
- **GitLab CI:** uses `assembleStandard`, which does **not** exist in the current `build.gradle` (only `git`/`playstore` flavors) — the pipeline is broken/stale.
- **GitHub Actions:** builds `QuicksyFree*` and `ConversationsFree*` variants — these flavor names do **not** exist in the current `build.gradle`; the workflow is outdated for this fork.

## Mod-specific behaviors (do not "fix" back to upstream)

- **File downloads are redesigned:** HTTP transfers save to the **public Download folder** (`Download/<APP_DIRECTORY>/`) using the **original filename**. Name priority is the SIMS `<name>` element (`Message.FileParams.getName()`) first, then the last URL path segment (`HttpDownloadConnection.originalFilenameFromUrl`). Downloaded files show an "Open file" button in the chat.
- The chat shows the original filename under a downloaded/openable file — but **deliberately not** under image/video previews (`displayMediaPreviewMessage`). Do not "fix" the preview to show the name.
- OMEMO-encrypted HTTP downloads and background fetches (webxdc previews, link images) are **deliberately not** redirected — they keep private storage/temp paths.
- Legacy Jingle (XEP-0234) transfers intentionally keep the upstream private-storage behaviour.
- `ServiceManagementDialog` derives its action menu from disco#info; if `disco#info` fails (error/timeout) it falls back to showing all actions rather than collapsing to just "Copy JID".
- **MUC conversation name fallback** (`Conversation.getName()`): `muc_name` → bookmark name → **JID local part (before `@`)**. The room topic/subject is deliberately **not** used as a fallback — do not "fix" it back.
- **MUC topic strip** (preference `show_muc_topic`, default `false`): when enabled, `ConversationFragment` shows the room topic in a narrow `muc_topic` strip above the message list (added to **both** `layout/` and `layout-land/fragment_conversation.xml`); tapping it opens the conference details (`switchToMUCDetails`).
- **Image link previews in chat** (`displayTextMessage`): first tries `extractFirstImageUrl` (URL path must end in a literal image extension), then falls back to `extractFirstUrl` for any http(s) link that dominates the body. Failure to render hides the preview (`onLoadFailed`), leaving just the clickable link.
- **XEP-0272 Multiparty Jingle (Muji) — stage 3, audio/video mesh + conference UI.** `MujiConference`/`MujiConferenceManager` (`xmpp/jingle/`) track one conference per MUC room. The trigger is the MUC overflow item `action_muji_conference` (labeled "Start group call" / "Group call", `ConversationFragment.toggleMujiConference`), which shows an audio-only / audio+video icon chooser (`dialog_muji_call_type.xml`) and then joins and opens `MujiConferenceActivity` (circular `MaterialButton` controls styled with the `MujiCircleButton` style: mic / camera / switch-camera / red hang-up; camera controls are hidden for audio-only; the toolbar is hidden while the conference is active). Muji sessions deliberately **do not create a 1:1 conversation or call-log messages** per participant (`JingleRtpConnection` reuses the MUC `Conversation` and skips `writeLogMessage` when `isMuji()`), so participants don't show up in Chats. MUC presence carries `<muji>` (`Namespace.JINGLE_MUJI`), advertised in caps; per-occupant contents are parsed into `MucOptions.User.getMuji()`. An incoming Jingle `session-initiate` with `<muji room>` is only accepted if the sender is an occupant of an active local conference (`JingleConnectionManager.deliverMujiSessionInitiate`), then auto-accepted through a Muji-tagged `JingleRtpConnection` (`isMuji()`); such sessions are excluded from `isBusy()` and never create a Telecom call. All peer connections share one `PeerConnectionFactory`/`EglBase`/microphone track **and one camera track** via `WebRTCResources` (refcounted, `getOrCreateVideoTrack()`, owned by the conference), so the mesh opens one audio device and one camera, not N. Join requests `RECORD_AUDIO` (+`CAMERA` when available) with request code `REQUEST_START_MUJI_CONFERENCE` and advertises AUDIO+VIDEO; `MujiConferenceActivity` lays out one tile per participant (`MujiParticipantGridView`, a plain `ViewGroup` — deliberately not a `RecyclerView`, to avoid recycling `SurfaceViewRenderer`s) and binds each tile to that connection's `getRemoteVideoTrack()`. The grid has no `ScrollView`; it fills the remaining height and picks the smallest column count whose 16:9 tiles fit (i.e. full-width stacked tiles while they fit vertically, a mosaic only when they don't). Two non-obvious fixes are load-bearing: the tile renderer must use `setScalingType(SCALE_ASPECT_FILL, SCALE_ASPECT_FILL)` with hardware scaler off (otherwise it resizes its own layout and the OS rejects every video buffer with `BLASTBufferQueue … rejecting buffer`), and `XmppConnectionService.startForegroundOrCatch` must add `FOREGROUND_SERVICE_TYPE_CAMERA` for video calls/conferences (otherwise background capture fails with `Camera2Session … device policy`). Debug builds write a stanza/camera trace to `files/muji.log` via `MujiLog` (Huawei/EMUI strips `Log.d` from logcat; read it with `adb shell run-as de.monocles.mod cat files/muji.log`). Known limitations (stage 4): presence content descriptions still carry no payload-type mapping (codecs are negotiated per-session by WebRTC SDP), and remote audio playout is not explicitly mixed.

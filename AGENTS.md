# Monocles mod — AGENTS.md

## What this is

XMPP client for Android; a fork of Monocles Chat, itself a Conversations fork. Java + Kotlin, AGP 8.5.2 / Gradle 8.8 / Kotlin 1.9.10. `README.md` is the Russian feature changelog; `CHANGELOG.md` and `docs/` are upstream English. Most "mod" features are deliberate deviations from upstream — see **Deliberate behavior** below.

## Build & verify

Export JDK 21 (the build targets 17):

```bash
export JAVA_HOME=/home/eugene/.jdks/jbr-21.0.11
export PATH=$JAVA_HOME/bin:$PATH
```

- Fast compile check: `./gradlew compileGitDebugJavaWithJavac`
- Real verification (this is the CI `test` job — **there are no unit/instrumentation tests**): `./gradlew lintGitDebug`
- APKs: `./gradlew assembleGit` (F-Droid flavor) or `./gradlew assemblePlaystore`
- Output in `build/outputs/apk/<flavor>/`; local release artifacts are gitignored.
- The first `assembleGit` occasionally loses a compile/resource race — rerun the identical command.
- ProGuard + shrinking (`-dontobfuscate`) on **both** debug and release, and signing configs, exist only when all of `mStoreFile`, `mStorePassword`, `mKeyAlias`, `mKeyPassword` are set in `gradle.properties`. Otherwise default (unminified, unsigned) types apply.

## Flavors, namespace, entrypoints

- Flavors `git` and `playstore`, both applicationId `de.monocles.mod`. `playstore` hides migration info and has FCM; `git` shows migration info, no FCM.
- **namespace is `eu.siacs.conversations`**, so generated `R`/`BuildConfig` live there; mod classes still `import eu.siacs.conversations.R`.
- Launcher is flavor-specific: `src/git/java/de/monocles/mod/ui/StartUI.java` and `src/playstore/java/de/monocles/chat/StartUI.java` (the playstore directory does **not** match its `de.monocles.mod.ui` package — leave it). The manifest targets `de.monocles.mod.ui.StartUI`; touch both sources if it changes.
- Core service: `eu.siacs.conversations.services.XmppConnectionService`.
- Sources: `src/main` shared, `src/git` + `src/playstore` flavor overrides, `src/debug` debug resources. Packages: `de.monocles.mod.*` (mod), `eu.siacs.conversations.*` (upstream), `p32929.easypasscodelock` (3rd-party lockscreen).

## Repo / toolchain quirks

- SDK: compile/target 34, min 23 — gate newer APIs. JVM target 17 with Java 8+ desugaring. Multidex + ABI splits (`universalApk`, per-ABI version-code offsets).
- `org.gradle.daemon=false`, `org.gradle.parallel=true`.
- Lint: `abortOnError false`; `StringFormatInvalid` / `StringFormatMatches` are the **only** escalated errors, i.e. the only lint failures. `MissingTranslation` etc. are disabled.
- Data binding **and** view binding are both enabled. No formatter or static-analysis tasks (no ktlint/detekt/spotless/checkstyle/.editorconfig); Gradle lint is the only static check.
- Translations are Crowdin-managed (`crowdin.yml`: `values/strings.xml` → `values-%two_letters_code%/`). Only `values-ru-rRU/` is a real translation; English-only strings build fine.
- `settings.gradle` includes `:libs:xmpp-addr`, which does not exist (only `libs/AXML`) — stale.
- `layout-land/fragment_conversation.xml` diverges from portrait: update **both** when adding views there.
- CI: CircleCI is current (`lintGitDebug` / `assembleGit`) but downloads an unused WebRTC `libwebrtc-m85.aar` (WebRTC is now the Maven dependency `im.conversations.webrtc:webrtc-android:119.0.1`) and stores artifacts at a nonexistent `apk/standard` path. GitLab CI (`assembleStandard`) and GitHub Actions (`QuicksyFree*` / `ConversationsFree*` variants) reference flavors that don't exist — stale, don't trust them.

## Runtime invariants (crash / regression guardrails)

- **Null `account` window:** conversations restored from DB are account-less until attached, so a live message can be processed against one during `switchOverToTls`. Keep `Message.getContact()`, `Conversation.getContact()`, `Conversation.getBookmark()`, `MucOptions.getProposedNick()`, `Conversation.getName()`, `NotificationService.push` and `MyLinkify.addLinks` null-safe (short-circuit/skip, never NPE). Background HTTP file-size checks can call `push` on such a conversation: for catchup it uses `pushFromBacklog`, otherwise it deliberately skips the notification (the builder derefs the account).
- **No `SYSTEM_ALERT_WINDOW`** in any manifest. Nothing creates an overlay; declaring it makes the system permission dialog's buttons unresponsive on EMUI/MIUI (Android tapjacking protection). Do not add it back.
- Return-to-call: `getOngoingRtpConnection()` returns an `OngoingRtpSession` — a `JingleRtpConnection` (`AbstractJingleConnection`) or an `RtpSessionProposal`. Test `instanceof AbstractJingleConnection`, never a Jingle `Id` type; otherwise the hang-up button gets no click listener.
- **Retraction (XEP-0424):** keep retracted rows with `RETRACTED_MESSAGE_BODY` and the exact `retractedAt` timestamp — never `setMessageDeleted(true)` (that flag drops the row from queries). `retractedAt` is added **only** by `monoclesMigrate()` on the monocles side table (`user_version 11`), never in main `onUpgrade` (duplicate-column crash in 1.9.1.55); keep `DATABASE_VERSION = 62` so affected devices upgrade. Send v1 `<retract>` (accept v0.3 `<apply-to>` too), match the stanza-id used by peers (MUC), and verify the sender.
- Live 1:1 dedup: `MessageParser` skips the duplicate check unless MUC-delay/private/has serverMsgId or remoteMsgId, or during catchup; re-deliveries rely on `remoteMsgId` matching in `Message.similar()`.

## Deliberate behavior — do NOT "fix" back to upstream

Full history lives in `README.md`, `CHANGELOG.md` and git log; these are the operative rules.

- **Downloads:** HTTP transfers go to public `Download/<APP_DIRECTORY>/` with the original filename (SIMS `<name>` first, then URL path segment) + an "Open file" action. OMEMO/PGP downloads, background link-image fetches and legacy Jingle (XEP-0234) keep private/temp storage. No filename under image/video previews.
- **Chats / MUC:** name fallback is `muc_name` → bookmark name → JID local part, never the topic. `alwaysNotify()` defaults for all rooms, independent of privacy/who-is settings — only the per-room notify status controls notifications. MUC topic strip (`show_muc_topic`, default false) exists in both layout variants. Chat list sections: contacts / transports / conferences / private messages; MUC PMs get standalone conversations; contacts group by roster labels.
- **Presence priorities** are edited in Expert settings → Presence → "Presence priorities" (not the account editor): online/away/xa/dnd values, emitted as `<priority>` for the current status.
- **Reactions (XEP-0444):** hidden `<reactions/>` carriers never render, notify, mark unread or affect sorting; there is deliberately no reaction notification (in-chat heart button only). Aggregate per reactor, latest carrier only. `urn:xmpp:reactions:0` is not advertised in caps.
- **Styling / markup:** XEP-0393 in-band directives (`_em_`, `~del~`, `` `mono` ``, fences, `>` quotes) and XEP-0394 `<markup/>` coexist; **bold is unsupported** (`*` is literal); `/me` is expanded only in `MessageAdapter`; fences are stripped at render; blocks use the shared `CodeBlockSpan`; only code blocks can be authored; XHTML-IM is stored but never rendered.
- **SIMS (XEP-0385):** pick the best source across all `<sources>` (https > aesgcm > cid > xmpp), type messages before download, and verify CIDs after HTTP download — but skip integrity checks for encrypted transfers.
- **vCard:** dual vcard-temp / vCard-4 support; fetch `room/nick` for occupant cards (the bare room returns the room card); resolve the account in `onBackendConnected`, not `onCreate`.
- **Private notes:** XEP-0049 single `<storage>` blob, IQ id `strnotes_1`, content-derived SHA-1 keys. Never push before a successful fetch (it would wipe the server's notes) — mutations queue until the fetch completes.
- **Whiteboard** (1:1 + MUC, Jingle app + XEP-0284 SXE): XEP-0353 `<propose/>` to the bare JID, then `session-initiate` to the answering **full** JID (never `Contact.getMostAvailableResource()` — it adds parentheses). In `deliverPacket`, ack the IQ **before** `dispatchJingle` (Iris/Psi reject an unacked initiate); `isWhiteboardJingle` must use `getJingleChild("content")`. Host = Jingle initiator, never a JID comparison; `senders="none"` means no editing until a `content-modify` to `both`. Snapshots merge on a shared root (never `clearShapes()`); payloads carry no body; the creator owns board lifetime; shared 600×400 `viewBox` with uniform cover scaling; `fill-opacity=0` means unfilled; render `<polygon>` etc. and remote `transform`; a new attribute → `<new>` not `<set>`; edits use `setRequestAck(false)`; the text tool publishes its shape once.
- **Muji (XEP-0272):** mesh conference per room over one shared `WebRTCResources` (mic + lazy camera) — mute/camera/switch work with zero participants (fallback to `deviceHasMultipleCameras`). No per-participant 1:1 conversations or call logs. Centred control bar (mic / camera / hang-up / speaker / More); the More popup sits above its button with round buttons. Bluetooth routing and partial wakelock live in `MujiConference` so they survive activity destruction. Debug stanza trace only via `MujiLog.log()` → `files/muji.log` (plain `Log.*` does not write it; `adb shell run-as de.monocles.mod cat files/muji.log`).
- **Quick responses (XEP-0439):** `<response>`/`<action>` children are kept as message payloads and render as chips under the bubble. Response chips appear **only on the most recently received text message**; action chips on every message carrying them. Selecting a response sends its `value` as a normal body with the element's `xml:lang` copied onto `<body>`; selecting an action sends a bodyless message carrying only `<action-selected id="…"/>`. Namespace `urn:xmpp:tmp:quick-response` is advertised in caps.
- **Media browser multi-select:** long press in `MediaBrowserActivity` enters selection mode (inline previews in contact/room details keep the old single-file popup). Action mode saves selected files to public `Download/<APP_DIRECTORY>/`, deletes only app-owned files, and keeps open/share available for a single selection. `MediaAdapter` owns selection state; the activity removes deleted attachments from `allAttachments`/`filteredAttachments` so they do not reappear after a filter/resume. `SquareFrameLayout` must stay the **root** of `res/layout/media.xml` (it measures height from width); put selection overlay/check inside it, never in a wrapper parent.
- Misc: custom "smiles" and client icons are on by default (upstream stickers removed); client software versions are hidden in roster/participant lists (details only); `ServiceManagementDialog` falls back to all actions when disco#info fails; animated-avatar "blink" fix uses a synchronous cache lookup first.

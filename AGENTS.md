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

**Launcher activity:** `de.monocles.mod.ui.StartUI`
**Core service:** `eu.siacs.conversations.services.XmppConnectionService`

## Key gotchas

- **No tests exist.** No `src/test/` or `src/androidTest/` directory. `libs:AXML` has a `// TODO UNIT TESTS` comment.
- **Conversations restored from DB have a null `account` until attached** (`XmppConnectionService.restoreFromDatabase`). During early connection setup (`switchOverToTls`), a live message can be processed against such a conversation. `Message.getContact()`/`Conversation.getContact()` must remain null-safe (they intentionally return `null`/short-circuit when `account` is null rather than NPE).
- **Live 1:1 message dedup:** `MessageParser.onMessagePacketReceived` skips the duplicate check unless the message is a MUC-delay, private, or carries a serverMsgId/remoteMsgId, or catchup is in progress. Re-delivered live messages lacking a serverMsgId rely on `remoteMsgId` matching in `Message.similar()` to be dropped.
- **Data binding + view binding** are both enabled.
- **Signing config** is conditional — requires `mStoreFile`, `mStorePassword`, `mKeyAlias`, `mKeyPassword` project properties. Without them, signing configs are omitted.
- **JVM target:** 17, with Java 8+ desugaring enabled (`coreLibraryDesugaringEnabled true`).
- **Gradle daemon is disabled** (`org.gradle.daemon=false` in `gradle.properties`). Parallel builds are on (`org.gradle.parallel=true`).
- **Lint:** `abortOnError false`; disables `ExtraTranslation`, `MissingTranslation`, `InvalidPackage`, `MissingQuantity`, `AppCompatResource`, `RestrictedApi`.
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
- **Image link previews in chat** (`displayTextMessage`): first tries `extractFirstImageUrl` (URL path must end in a literal image extension), then falls back to `extractFirstUrl` for any http(s) link that dominates the body. Failure to render hides the preview (`onLoadFailed`), leaving just the clickable link.

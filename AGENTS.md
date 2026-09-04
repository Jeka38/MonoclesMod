# Monocles mod — AGENTS.md

## What this is

XMPP chat client for Android — a Conversations fork. Java + Kotlin, Gradle 8.8 / AGP 8.5.2.

## Build

Requires JDK 17 (JVM target is 17). Export `JAVA_HOME` first, e.g.:

```bash
export JAVA_HOME=/home/eugene/.jdks/jbr-17.0.14
export PATH=$JAVA_HOME/bin:$PATH
```

WebRTC is a Maven dependency (`im.conversations.webrtc:webrtc-android:119.0.1`) — no local `.aar` download needed.

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

Both release and debug builds have ProGuard + shrinking enabled. `-dontobfuscate` is set.

## Source layout

- `src/main/` — shared code (upstream Conversations + mod additions)
- `src/playstore/` — Play Store flavor overrides (FCM, manifest)
- `src/git/` — Git flavor overrides (manifest, migration info)
- `de.monocles.mod.*` — mod-specific additions; `eu.siacs.conversations.*` — upstream core

**Launcher activity:** `de.monocles.mod.ui.StartUI`
**Core service:** `eu.siacs.conversations.services.XmppConnectionService`

## Key gotchas

- **No tests exist.** No test directory. `libs:AXML` has a `// TODO UNIT TESTS` comment.
- **Data binding + view binding** are both enabled.
- **Signing config** is conditional — requires `mStoreFile`, `mStorePassword`, `mKeyAlias`, `mKeyPassword` project properties. Without them, signing configs are omitted.
- **JVM target:** 17, with Java 8+ desugaring enabled.
- **Gradle daemon is disabled** (`org.gradle.daemon=false` in `gradle.properties`).
- **Lint:** `abortOnError false`; disables `MissingTranslation`, `ExtraTranslation`, etc.
- **Layout variants diverge.** `layout-land/fragment_conversation.xml` can differ significantly from portrait (e.g. was missing `live_location_banner` causing a crash on HONOR foldable). Always update both variants when adding views to `fragment_conversation.xml`.

## CI references

- **CircleCI:** `test` job runs `lintGitDebug`, build job runs `assembleGit`. Uses F-Droid CI Docker image. Current.
- **GitLab CI:** uses `assembleStandard`, which does **not** exist in the current `build.gradle` (only `git`/`playstore` flavors) — the pipeline is broken/stale.
- **GitHub Actions:** builds `QuicksyFree*` and `ConversationsFree*` variants — these flavor names do **not** exist in the current `build.gradle`; the workflow is outdated for this fork.

## Mod-specific behaviors (do not "fix" back to upstream)

- **File downloads are redesigned:** HTTP transfers save to the **public Download folder** (`Download/<APP_DIRECTORY>/`) using the **original filename**. Name priority is the SIMS `<name>` element (`Message.FileParams.getName()`) first, then the last URL path segment (`HttpDownloadConnection.originalFilenameFromUrl`). Downloaded files show an "Open file" button in the chat.
- The chat shows the original filename under a downloaded/openable file — but **deliberately not** under image/video previews (`displayMediaPreviewMessage`). Do not "fix" the preview to show the name.
- OMEMO-encrypted HTTP downloads and background fetches (webxdc previews, link images) are **deliberately not** redirected — they keep private storage/temp paths.
- Legacy Jingle (XEP-0234) transfers intentionally keep the upstream private-storage behaviour.
- `ServiceManagementDialog` derives its action menu from disco#info; if `disco#info` fails (error/timeout) it falls back to showing all actions rather than collapsing to just "Copy JID".
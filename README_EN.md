# ChatMate AI

ChatMate AI is an Android AI chat companion. It reads the current conversation, falls back to screenshot recognition when needed, and generates personalized reply suggestions using global and per-conversation prompts.

## Features

- Chat reading: WeChat, Xiaohongshu and Douyin DM profiles filter controls, follow status and navigation; QQ, Telegram, WhatsApp, SMS and other apps use shared layout handling
- Hybrid recognition: checks reading quality before falling back to screenshots; composer drafts and keyboard content are excluded
- Natural replies: context-aware natural, playful, warm, invitation and polite refusal styles; regeneration includes the previous draft to encourage a different approach
- Dedicated modes: screen reading only or screenshot only
- Continuous conversations: the same chat in the same app is grouped automatically
- Multiline messages: line breaks inside one chat bubble remain one message
- Personalized configuration: each conversation can inherit or override the global system prompt
- WeChat-style history: left/right bubbles, initials and capture-time separators, search and app filters; long-press to correct, copy or delete
- Contact isolation: separate apps and differently named contacts stay separate; remarks preserve recognition keys and manual merging uses a contact picker
- Quick Settings tile: start or stop the assistant from Android Quick Settings

## Usage

1. Configure the API, model, and global system prompt on Home.
2. Select a recognition mode; Hybrid is recommended.
3. Grant overlay, accessibility, or screen-capture permissions when prompted.
4. Turn on the Chat Assistant switch at the bottom of Home.
5. Open a conversation with its title and recent messages visible, then tap the floating button. A draft starts automatically when a model is configured, the latest message is incoming and no speaker is unresolved.
6. Tap a bubble to correct text or speaker when needed, select a reply style, or add an optional intent.
7. Edit the draft and choose Copy and return to chat, then paste and send in the original app. Choose another reply to regenerate.

Feeds, comments and inbox lists are not treated as private conversations. Reading covers the visible screen and previously captured local history; it does not scroll automatically. OCR, groups, identical contact names and app-version differences may still need manual correction. Unseen image, video and voice content must not be invented by the model.

See [interaction refactor notes](docs/chat-experience.md) for design, validation and limitations.

On Samsung One UI, expand Quick Settings, choose Edit buttons, and drag Chat Assistant from Available buttons into the panel. The app does not show a tile-add popup automatically.

## Build

JDK 17 and the Android SDK are required. Copy `local.properties.template` to `local.properties` and set `sdk.dir`.

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

You can also run the build helper and select Debug, Release, or both:

```bash
./build-apk.sh
```

For non-interactive selection, use `./build-apk.sh debug`, `./build-apk.sh release`, or `./build-apk.sh all`. The first Release build creates a signing key at `~/.android/chatmate-ai-release.jks`; back up the key and its password securely.

## Privacy

Configuration is stored locally and API keys use encrypted storage. Recognized chat content is sent only to the AI provider you configure. Tapping the assistant may start generation automatically; stopping or leaving the reply screen cancels the current request. Drafts are copied, never automatically sent. Capture is triggered by user action, not by accessibility event callbacks; password nodes, unsent drafts and keyboard windows are excluded. No new system permissions are added. Use the app with participants' consent and in accordance with applicable laws and platform policies.

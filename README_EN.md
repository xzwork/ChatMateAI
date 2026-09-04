# ChatMate AI

ChatMate AI is an Android AI chat companion. It reads the current conversation, falls back to screenshot recognition when needed, and generates personalized reply suggestions using global and per-conversation prompts.

## Features

- Hybrid recognition: reads the screen first, then uses an accessibility screenshot, requesting system screen sharing only as a final fallback
- Dedicated modes: screen reading only or screenshot only
- Continuous conversations: the same chat in the same app is grouped automatically
- Multiline messages: line breaks inside one chat bubble remain one message
- Personalized configuration: each conversation can inherit or override the global system prompt
- Conversation management: inspect, edit, or delete a conversation or all conversations from one app
- Quick Settings tile: start or stop the assistant from Android Quick Settings

## Usage

1. Configure the API, model, and global system prompt on Home.
2. Select a recognition mode; Hybrid is recommended.
3. Grant overlay, accessibility, or screen-capture permissions when prompted.
4. Turn on the Chat Assistant switch at the bottom of Home.
5. Open a conversation, tap the floating button, confirm the recognized content, and generate a reply.

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

Configuration is stored locally and API keys use encrypted storage. Recognized chat content is sent only to the AI provider you configure. Use the app with participants' consent and in accordance with applicable laws and platform policies.

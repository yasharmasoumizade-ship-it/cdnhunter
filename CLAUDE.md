# Project Rules

## Build Policy
- NEVER run `./gradlew build`, `./gradlew assembleRelease`, or any local Gradle build on this VPS — RAM is limited (~1GB) and it will crash or hang.
- Only edit source files, then `git add`, `git commit`, `git push` to the `main` branch.
- The actual APK build happens exclusively via GitHub Actions (workflow: build-unified.yml). Verify results there, not locally.
- If a build check is truly needed, use `./gradlew compileDebugKotlin` at most (lint/compile check only, no full assembly).

## Token Efficiency
- Keep responses terse and direct. No filler, no repeated summaries, no restating what was just done.
- Prefer concise bullet outputs over long prose explanations unless explicitly asked for detail.

## Security & Design Skills
- Use trailofbits-security and android-skills for any security review or hardening request.
- Use ui-ux-pro-max, taste-skill, impeccable, and mobile-app-ui-design for any UI/UX/design request.
- Never touch geoip:ir / geosite:ir split-tunnel routing logic without explicit confirmation.

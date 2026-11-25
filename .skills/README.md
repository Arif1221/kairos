# Android skills

Official skills from [android/skills](https://github.com/android/skills) (Apache 2.0, see
`LICENSE-android-skills.txt`), copied in for agents working on Kairos (Android Studio's agent,
Gemini CLI, Claude Code). Only the ones that apply to this app:

| Skill | Use it for |
|---|---|
| jetpack-compose-performance | Recomposition, lazy lists, animation and drawing performance |
| r8-analyzer | Keep rules and app size |
| anr-audit-and-fix | Main-thread work and ANRs |
| edge-to-edge | System bars and insets |
| adaptive | Tablets, foldables, landscape |
| android-intent-security | Share intents, exported components |
| android-permissions-security | Media and notification permissions |
| play-policy-insights | Play Store policy checks before release |
| testing-setup | Unit, UI and screenshot tests |

Update with the Android CLI: `android skills add --skill=<name> --project=.`

## Community skills

| Skill | Source | Licence | Use it for |
|---|---|---|---|
| claude-android-ninja | [Drjacky/claude-android-ninja](https://github.com/Drjacky/claude-android-ninja) | Apache 2.0 | Compose patterns, performance, media/Media3, accessibility, permissions, security, target SDK migrations |
| android-development | [dpconde/claude-android-skill](https://github.com/dpconde/claude-android-skill) | MIT | Google's architecture guidance (Now in Android): layers, repositories, testing |

Both assume Hilt, multi-module Gradle and (ninja) Navigation 3. Kairos is a single module with a
small manual container on purpose: at this size that is simpler and builds faster. Use these
skills for their Compose, media, performance and testing advice; don't migrate the architecture
just because a skill's template does it differently.

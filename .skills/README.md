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

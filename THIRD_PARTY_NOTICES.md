# Third-Party Notices

Doudizhu LAN Party itself is released under the MIT License (see [LICENSE](LICENSE)).
The project is built with the following open-source components, each under its own
license. This summary is provided for attribution and is not a substitute for the
full license text distributed with each dependency.

| Component | Version | License | Project |
|---|---|---|---|
| Kotlin / Kotlin Standard Library | 2.1.0 | Apache License 2.0 | https://kotlinlang.org/ |
| Compose Multiplatform (incl. Material 3) | 1.7.3 | Apache License 2.0 | https://www.jetbrains.com/compose-multiplatform/ |
| AndroidX Activity Compose | 1.9.3 | Apache License 2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Lifecycle ViewModel Compose | 2.8.7 | Apache License 2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Core KTX | 1.13.1 | Apache License 2.0 | https://developer.android.com/jetpack/androidx |
| kotlinx.coroutines | 1.9.0 | Apache License 2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx.serialization | 1.7.3 | Apache License 2.0 | https://github.com/Kotlin/kotlinx.serialization |
| Gradle Wrapper (build only) | 8.9 | Apache License 2.0 | https://gradle.org/ |
| Android SDK Platform / Build Tools (build only) | 35 / 35.0.0 | Android SDK Terms | https://developer.android.com/studio/terms |

Notes:

- No third-party card images, audio, fonts, or other art assets are bundled. Card
  faces, the table, and animations are drawn at runtime with Compose primitives.
- The Android SDK is used for building and is not redistributed in the source
  repository. Review the Android SDK terms before distributing an APK.
- "Dou Dizhu" / "斗地主" is a traditional card game. This project does not include
  art, audio, trademarks, or decompiled code from Tencent's "Happy Dou Dizhu" or any
  other commercial game. Third-party names and trademarks belong to their owners.
- If you redistribute a built APK, include this notice and the applicable dependency
  license texts with the distribution.

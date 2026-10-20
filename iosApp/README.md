# iosApp (M5 preview)

`shared` exposes iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`) when built
on a macOS host. The SwiftUI shell is a compile-only placeholder: the game rules,
AI, protocol and session state machines are already shared Kotlin.

iOS networking is **not** implemented yet — `iosMain` actuals throw / return empty
flows until M6 wires Network.framework + NSNetService. Do not ship this shell.

To create the Xcode project on macOS:

1. Open `iosApp/iosApp` in Xcode and create an iOS App target named `iosApp`.
2. Add a Run Script build phase invoking:
   `cd "$SRCROOT/.." && ./gradlew :shared:embedAndSignAppleFrameworkForXcode`
3. Add the generated `Shared.framework` to "Frameworks, Libraries, and Embedded Content".
4. Build for an iOS 16+ simulator.

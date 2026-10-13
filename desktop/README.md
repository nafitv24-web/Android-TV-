# Windows PC (.exe) Package Information for NAFI TV 24

NAFI TV 24 Windows Desktop Edition uses Compose Multiplatform Desktop with Kotlin and Gradle.

## Architecture
- **Language**: Kotlin 2.2.x JVM
- **Framework**: Compose Multiplatform Desktop
- **Packaging**: Windows Installer (.exe & .msi) using JetBrains Compose Desktop plugin
- **Live Stream Engine**: Supports HLS/M3U8 streams, Live Events JSON API, and M3U playlists

## Direct Execution / Build:
```bash
./gradlew :desktop:packageExe
```
The generated `.exe` installer will be located at:
`desktop/build/compose/binaries/main/exe/NAFITV24.exe`

# Kodland Minecraft mods

Fabric mods for the Kodland course pack (Minecraft 26.2). Each folder is one mod; the
Kodland Launcher downloads the released jars through the pack manifest.

| Mod | Folder | Manifest id | What it does |
|---|---|---|---|
| Camera | — | `kodland-camera` | Take photos in the game; they appear in the launcher's Albums Studio |

## Build and run

Java 25. Gradle comes with the wrapper.

```bash
./run-dev.sh camera        # dev client with one mod, no launcher and no account
./gradlew build            # every mod -> <mod>/build/libs/*.jar
./gradlew :camera:build    # one mod
```

## Release

Push a tag `<mod>-v<version>`, for example `camera-v0.1.0`. The `release` workflow checks the
version against `<mod>/gradle.properties`, builds the mod and attaches the jar (and its sha512)
to a GitHub Release. That release url goes into the pack in the Sparks admin.

## Adding a mod

A new folder with `build.gradle`, `gradle.properties` (`mod_version`, `archives_base_name`) and
`src/`. `settings.gradle` picks it up by itself. Game and Fabric versions are shared in the root
`gradle.properties`.

## License

All rights reserved, see `LICENSE`.

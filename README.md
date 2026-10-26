# CORZ Builder — Minecraft Java 1.21.11 (Fabric)

A client-side Fabric project targeting Minecraft **1.21.11** and Java 21. The CORZ purple/black dashboard opens with **Right Shift**.

## AutoBuilder browser (early implementation)

- Opens the Minecraft instance's `schematics/donut/` folder (create it if needed).
- Lists `.litematic` files and includes Open Folder, Refresh, selection, Previous/Next, and a Preview/Position information panel.
- Performs basic file checks (size limit and gzip header) and reports errors instead of silently accepting unreadable files.
- Includes a Materials panel that explicitly does not invent totals before a full schematic decoder is connected.

## Important implementation status

This ZIP is a **starter implementation**, not the complete automated builder described in the requested spec. Full compressed-NBT/Litematica decoding (including multiple regions), accurate block/material totals, a rotatable 3D render, world ghost rendering, transforms, dependency-aware placement, and per-placement world confirmation are not implemented yet. The UI labels this honestly; it does not claim to build a schematic or place blocks. Other dashboard module switches are visual toggles only.

## Build

1. Install a Java 21 JDK.
2. Extract the whole ZIP so `settings.gradle`, `build.gradle`, `gradle.properties`, `.github/`, and `src/` are at the repository root.
3. In that folder run `gradlew build` (Windows: `gradlew.bat build`; if the wrapper isn't present, open the project in IntelliJ IDEA with Gradle support).
4. The JAR is written to `build/libs/`.

## Modrinth

Upload the built JAR as a client-side Fabric mod for Minecraft 1.21.11. Do not upload the source ZIP as the mod file. The included GitHub Actions workflows are templates; configure repository secrets before using automated publishing.

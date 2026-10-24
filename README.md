# CORZ Builder — Fabric 1.21.11

A small client-side Fabric mod project with a CORZ-inspired purple/black menu.

## Current features
- Press **Right Shift** in Minecraft to open the menu.
- Four visual toggles: Auto Schematic Builder, Aim Assist, Auto Crystal, Auto Anchors.
- GitHub Actions builds a JAR on each push.

**Important:** This starter project is a UI prototype. The four toggles only change their ON/OFF label; they do not implement automatic building or combat actions.

## Build on GitHub
1. Create a new GitHub repository.
2. Extract this ZIP and upload the *contents* of this folder to the repository (so `build.gradle` and `gradle.properties` are at the repository root).
3. Open the repository's **Actions** tab. The `Build Fabric mod` workflow should run.
4. Open the completed workflow run and download the `corz-builder-jar` artifact.
5. The actual mod JAR is inside that downloaded artifact.

## Publish to Modrinth
1. Create a project on Modrinth and choose **Client-side**, **Fabric**, and Minecraft **1.21.11**.
2. In GitHub repository Settings → Secrets and variables → Actions, add:
   - `MODRINTH_TOKEN`: your Modrinth personal access token with permission to upload versions.
   - `MODRINTH_PROJECT_ID`: the project ID from your Modrinth project.
3. Run **Actions → Publish to Modrinth → Run workflow**.

Publishing may require completing Modrinth's project checks. Review the file and metadata before publishing.

## Local build (optional)
Install Java 21 and Gradle 9.2.1, open a terminal in the project folder, then run:

```powershell
gradle build
```

The built JAR will be in `build/libs/`.

## Notes
- Minecraft: Java Edition 1.21.11
- Loader: Fabric
- Requires Fabric API
- Menu key: Right Shift

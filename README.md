# AutoBuilder Mod (Minecraft 1.21.1)

Fabric Client Mod for Minecraft supporting `.litematic` schematics, interactive 3D preview, and automated dependency placement order.

## Building the `.jar` via GitHub Actions
1. Push this repository to GitHub.
2. Go to the **Actions** tab on your GitHub repository.
3. Once the build workflow finishes, download the generated `.jar` file directly from the **Artifacts** section!

## Building Locally
Run the following command in your terminal:
```bash
./gradlew build
```
The compiled mod JAR will be output to `build/libs/autobuilder-1.0.0.jar`.

# Minecraft Fabric Mod Scaffolded

The `FireballPredictor` Minecraft Fabric mod has been successfully scaffolded and verified. The project is now ready for you to start adding your custom logic!

## Changes Made
- **Initialized Git Repository**: The repository was initialized, and a robust `.gitignore` was added.
- **Gradle Build System**: 
  - Set up a Gradle wrapper compatible with Java 21 (Gradle 9.6.1).
  - Updated the Fabric Loom plugin to `1.17-SNAPSHOT` to ensure compatibility.
  - Centralized Minecraft, Java, Fabric, YACL, ModMenu, and plugin dependency versions in Gradle Version Catalog [libs.versions.toml](../gradle/libs.versions.toml).
  - Configured `me.modmuss50.mod-publish-plugin` in `build.gradle` to automatically publish mod binaries to Modrinth (`fireball-predictor`), CurseForge (`1613094`), and GitHub Releases.
  - Added automatic release note parsing from [CHANGELOG.md](../CHANGELOG.md).
- **Project Structure**:
  - Created the root `ModInitializer` class: [FireballPredictor.java](../src/main/java/com/simonconrad/fireballpredictor/FireballPredictor.java).
  - Added mod metadata configurations in [fabric.mod.json](../src/main/resources/fabric.mod.json) and Mixin configurations in [fireballpredictor.mixins.json](../src/main/resources/fireballpredictor.mixins.json).
  - Created standard assets and data directories for resources.
- **CI/CD setup**:
  - Added a GitHub Actions workflow in [.github/workflows/build.yml](../.github/workflows/build.yml) to test builds on Push and PR using Java 21 with Gradle caching (`setup-gradle@v3`).
  - Added [.github/workflows/publish.yml](../.github/workflows/publish.yml) to automatically publish new versions when a git tag (`v*`) is pushed.

## Validation Results
- Verified that `.\gradlew build` completes successfully.
- All Fabric Loader API libraries and the Minecraft `1.21.11` mappings correctly downloaded and de-obfuscated.

## Next Steps
You can now start implementing your logic inside [FireballPredictor.java](../src/main/java/com/simonconrad/fireballpredictor/FireballPredictor.java). If you intend to use Mixins or other specific Fabric features, you can expand upon the mixins and metadata configurations.

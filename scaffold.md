# Minecraft Fabric Mod Scaffolded

The `FireballPredictor` Minecraft Fabric mod has been successfully scaffolded and verified. The project is now ready for you to start adding your custom logic!

## Changes Made
- **Initialized Git Repository**: The repository was initialized, and a robust `.gitignore` was added.
- **Gradle Build System**: 
  - Set up a Gradle wrapper compatible with Java 25 (Gradle 9.6.1).
  - Updated the Fabric Loom plugin to `1.9-SNAPSHOT` to ensure compatibility.
  - Abstracted Minecraft, Java, and Fabric dependency versions into `gradle.properties`.
- **Project Structure**:
  - Created the root `ModInitializer` class: [FireballPredictor.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/FireballPredictor.java).
  - Added mod metadata configurations in [fabric.mod.json](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/resources/fabric.mod.json) and Mixin configurations in [fireballpredictor.mixins.json](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/resources/fireballpredictor.mixins.json).
  - Created empty `assets` and `data` directories for future resources.
- **CI/CD setup**:
  - Added a GitHub Actions workflow to automatically test your builds on Push and PR.

## Validation Results
- Verified that `.\gradlew build` completes successfully.
- All Fabric Loader API libraries and the Minecraft `1.21.1` mappings correctly downloaded and de-obfuscated.

## Next Steps
You can now start implementing your logic inside `FireballPredictor.java`. If you intend to use Mixins or other specific Fabric features, you can expand upon the `mixins.json` and `fabric.mod.json` files as needed.

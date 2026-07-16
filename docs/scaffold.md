# Minecraft Fabric Mod Scaffolded

The `FireballPredictor` Minecraft Fabric mod has been successfully scaffolded and verified. The project is now ready for you to start adding your custom logic!

## Changes Made
- **Initialized Git Repository**: The repository was initialized, and a robust `.gitignore` was added.
- **Gradle Build System**: 
  - Set up a Gradle wrapper compatible with Java 25 (Gradle 9.6.1).
  - Updated the Fabric Loom plugin to `1.17-SNAPSHOT` to ensure compatibility.
  - Abstracted Minecraft, Java, and Fabric dependency versions into [gradle.properties](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/gradle.properties).
- **Project Structure**:
  - Created the root `ModInitializer` class: [FireballPredictor.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/FireballPredictor.java).
  - Added mod metadata configurations in [fabric.mod.json](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/resources/fabric.mod.json) and Mixin configurations in [fireballpredictor.mixins.json](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/resources/fireballpredictor.mixins.json).
  - Created standard assets and data directories for resources.
- **CI/CD setup**:
  - Added a GitHub Actions workflow in [.github/workflows/build.yml](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/.github/workflows/build.yml) to automatically test your builds on Push and PR using Java 25.

## Validation Results
- All Fabric Loader API libraries and the Minecraft `26.2` official Mojang mappings correctly downloaded and configured.

## Next Steps
You can now start implementing your logic inside [FireballPredictor.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/FireballPredictor.java). If you intend to use Mixins or other specific Fabric features, you can expand upon the mixins and metadata configurations.

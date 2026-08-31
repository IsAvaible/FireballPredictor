# Agent Guide: Navigating & Reverse Engineering YACL3

This document provides a guide for AI agents and developers working on this codebase on how to explore, inspect, and reverse-engineer the **YetAnotherConfigLib (YACL v3)** library directly from the local development environment.

---

## 1. Locating the YACL3 Dependency Jars

In Fabric/Loom development environments, third-party libraries like YACL3 are stored as cached JAR files in the Gradle user home cache.

### PowerShell Command to Locate YACL Jars
```powershell
Get-ChildItem -Path $env:USERPROFILE\.gradle\caches -Recurse -Filter '*yacl*.jar'
```
*Key Jar File*: `yacl-3.9.5+26.2-fabric.jar` (or version specified in `gradle/libs.versions.toml`).

---

## 2. Listing Packages and Classes (`jar tf`)

Use `jar tf` to inspect the package layout of the YACL3 library:

```bash
jar tf "C:\Users\simon\.gradle\caches\modules-2\files-2.1\...\yacl-3.9.5+26.2-fabric.jar"
```

### Key Package Map

| Package Path | Purpose & Key Classes |
| :--- | :--- |
| `dev/isxander/yacl3/api/` | High-level API interfaces (`YetAnotherConfigLib`, `ConfigCategory`, `OptionGroup`, `Option`, `NameableEnum`). |
| `dev/isxander/yacl3/api/controller/` | Controller builders (`FloatFieldControllerBuilder`, `ColorControllerBuilder`, `EnumControllerBuilder`, etc.). |
| `dev/isxander/yacl3/config/v2/api/` | Config V2/V3 API (`ConfigClassHandler`, `@SerialEntry`, `@AutoGen`). |
| `dev/isxander/yacl3/config/v2/api/autogen/` | Autogen annotations (`@TickBox`, `@ColorField`, `@FloatField`, `@EnumCycler`, `@Dropdown`, `@MasterTickBox`). |
| `dev/isxander/yacl3/config/v2/impl/` | Implementation classes (`ConfigClassHandlerImpl`, `ConfigFieldImpl`, `ReflectionFieldAccess`). |
| `dev/isxander/yacl3/config/v2/impl/autogen/` | Annotation processor factories (`AutoGenUtils`, `EnumCyclerImpl`, `TickBoxImpl`, `DropdownImpl`, `OptionFactoryRegistry`). |

---

## 3. Decompiling & Inspecting Bytecode (`javap`)

To discover exact translation key formats, method signatures, or internal implementation details without source code, use `javap`.

### Disassembling Class Signatures
```bash
javap -cp "path/to/yacl.jar" dev.isxander.yacl3.config.v2.api.autogen.AutoGen
```

### Decompiling Implementation Logic & Constants
```bash
javap -c -p -cp "path/to/yacl.jar" dev.isxander.yacl3.config.v2.impl.ConfigClassHandlerImpl
```

---

## 4. Reverse Engineering Recipes

### A. Discovering Group Localization Key Formats
Inspect `dev.isxander.yacl3.config.v2.impl.ConfigClassHandlerImpl`:
- Look for string format templates in `lambda$generateGui`:
  - `yacl3.config.%s.category.%s.group.%s`
  - **Formula**: `yacl3.config.<modid>:<configid>.category.<category_id>.group.<group_id>`

### B. Discovering Enum Localization Key Formats
Inspect `dev.isxander.yacl3.config.v2.impl.autogen.EnumCyclerImpl`:
- Look for string format templates in `lambda$createController`:
  - `yacl3.config.enum.%s.%s`
  - **Formula**: `yacl3.config.enum.<EnumSimpleName>.<value_name_lowercase>`

### C. Discovering Option & Dropdown Key Formats
Inspect `dev.isxander.yacl3.config.v2.impl.autogen.DropdownImpl` or `ConfigFieldImpl`:
- Tooltip/Desc format: `yacl3.config.<modid>:<configid>.<field_name>.desc`
- Field title format: `yacl3.config.<modid>:<configid>.<field_name>`
- Dropdown value format: `yacl3.config.<modid>:<configid>.<field_name>.<value>`

---

## 5. Recommended Workflow for AI Agents

1. **Locate Jar**: Find the cached `yacl-*.jar` under `$HOME/.gradle/caches/`.
2. **Scan Structure**: Run `jar tf` filtered with ripgrep/select-string to find target classes.
3. **Decompile Bytecode**: Use `javap -c -p` to extract string literals, format specifiers, and internal method contracts.
4. **Implement & Validate**: Apply changes to `ModConfig.java` or `lang/*.json` and run `.\gradlew compileJava --rerun-tasks` to verify clean compilation.

# Fabric API Stub

Stub for [Fabric API](https://github.com/FabricMC/fabric-api) for remapping using [Ravel](https://github.com/badasintended/ravel).

1. Remap your project to Mojang Mappings first and update to 26.1. [See the Fabric Docs for more info.](https://docs.fabricmc.net/develop/porting/next).
2. Replace your Fabric API to the stub:
   ```gradle
   // build.gradle
   repositories {
       maven { url 'https://jitpack.io' }
   }

   dependencies {
       // implementation "net.fabricmc.fabric-api:fabric-api:${project.fapi_version}"
       implementation "lol.bai:fabric-api-stub:0.0.1"
   }
   ```
   ```kt
   // build.gradle.kts
   repositories {
       maven("https://jitpack.io")
   }

   dependencies {
       // implementation "net.fabricmc.fabric-api:fabric-api:${project.fapi_version}"
       implementation "lol.bai:fabric-api-stub:0.0.1"
   }
   ```
3. Remap using Ravel using the [`renames.tiny` mapping](https://raw.githubusercontent.com/badasintended/fabric-api-stub/refs/heads/master/renames.tiny).
4. Undo the changes from step 2, switch back to actual Fabric API.

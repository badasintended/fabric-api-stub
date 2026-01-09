import com.google.common.collect.ArrayListMultimap;
import com.google.gson.*;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.jvm.tasks.Jar;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public abstract class CollectMetadataTask extends Jar {

    @InputFiles
    abstract ConfigurableFileCollection getMods();

    @Override
    protected void copy() {
        super.copy();

        var gson = new GsonBuilder().setPrettyPrinting().create();
        var outFile = getArchiveFile().get().getAsFile();
        try (var outJar = new JarOutputStream(new FileOutputStream(outFile))) {
            var outFmj = new JsonObject();
            outFmj.addProperty("schemaVersion", 1);
            outFmj.addProperty("id", "fabric-api-stub");
            outFmj.addProperty("version", "0.0.1");

            var injectedInterfaces = ArrayListMultimap.<String, String>create();
            var classTweaker = new StringBuilder();
            classTweaker.append("classTweaker\tv1\tofficial\n");

            for (var modFile : getMods()) {
                if (!modFile.isFile()) continue;
                if (!modFile.toString().endsWith(".jar")) continue;

                try (var modJar = new JarFile(modFile)) {
                    var entries = modJar.entries();
                    while (entries.hasMoreElements()) {
                        var inEntry = entries.nextElement();
                        var inEntryName = inEntry.getName();

                        try (var is = modJar.getInputStream(inEntry)) {
                            if (!inEntryName.equals("fabric.mod.json")) continue;

                            try (var ir = new InputStreamReader(is)) {
                                var inFmj = JsonParser.parseReader(ir).getAsJsonObject();
                                var inFmjId = inFmj.get("id").getAsString();

                                if (inFmj.has("accessWidener")) {
                                    var awEntry = modJar.getJarEntry(inFmj.get("accessWidener").getAsString());

                                    try (var awIs = modJar.getInputStream(awEntry)) {
                                        var aw = new String(awIs.readAllBytes(), StandardCharsets.UTF_8);
                                        var lines = aw.split("\n");

                                        classTweaker.append("\n# ").append(inFmjId).append('\n');
                                        for (int i = 1; i < lines.length; i++) {
                                            classTweaker.append(lines[i]).append('\n');
                                        }
                                    }
                                }

                                if (inFmj.has("custom")) {
                                    var inFmjCustom = inFmj.getAsJsonObject("custom");
                                    if (inFmjCustom.has("loom:injected_interfaces")) {
                                        var inFmjInjectedIface = inFmjCustom.getAsJsonObject("loom:injected_interfaces");
                                        inFmjInjectedIface.asMap().forEach((k, v) ->
                                            v.getAsJsonArray().forEach(it ->
                                                injectedInterfaces.put(k, it.getAsString())));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            var outClassTweakerEntry = new JarEntry("fabric-api-stub.classtweaker");
            outJar.putNextEntry(outClassTweakerEntry);
            outJar.write(classTweaker.toString().getBytes(StandardCharsets.UTF_8));
            outJar.closeEntry();
            outFmj.addProperty("accessWidener", "fabric-api-stub.classtweaker");

            var outFmjInjectedIface = new JsonObject();
            injectedInterfaces.asMap().forEach((k, v) -> {
                var json = new JsonArray();
                v.forEach(json::add);
                outFmjInjectedIface.add(k, json);
            });
            var outFmjCustom = new JsonObject();
            outFmjCustom.add("loom:injected_interfaces", outFmjInjectedIface);
            outFmj.add("custom", outFmjCustom);

            var outFmjEntry = new JarEntry("fabric.mod.json");
            outJar.putNextEntry(outFmjEntry);
            outJar.write(gson.toJson(outFmj).getBytes(StandardCharsets.UTF_8));
            outJar.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

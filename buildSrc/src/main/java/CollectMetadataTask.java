import com.google.common.collect.ArrayListMultimap;
import com.google.common.io.Files;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

public abstract class CollectMetadataTask extends DefaultTask {

    @Input
    abstract Property<String> getModId();

    @InputFiles
    abstract ConfigurableFileCollection getMods();

    @OutputFile
    abstract RegularFileProperty getFmj();

    @OutputFile
    abstract RegularFileProperty getClassTweaker();

    public CollectMetadataTask() {
        var buildDir = getProject().getLayout().getBuildDirectory();
        getFmj().convention(buildDir.file(getName() + "/fabric.mod.json"));
        getClassTweaker().convention(getModId().map(it -> buildDir.get().file(getName() + "/" + it + ".classtweaker")));
    }

    @TaskAction
    public void collect() throws IOException {
        var modId = getModId().get();

        var outFmj = new JsonObject();
        outFmj.addProperty("schemaVersion", 1);
        outFmj.addProperty("id", modId);
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
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Files.asCharSink(getClassTweaker().get().getAsFile(), StandardCharsets.UTF_8).write(classTweaker.toString());
        outFmj.addProperty("accessWidener", modId + ".classtweaker");

        var outFmjInjectedIface = new JsonObject();
        injectedInterfaces.asMap().forEach((k, v) -> {
            var json = new JsonArray();
            v.forEach(json::add);
            outFmjInjectedIface.add(k, json);
        });
        var outFmjCustom = new JsonObject();
        outFmjCustom.add("loom:injected_interfaces", outFmjInjectedIface);
        outFmj.add("custom", outFmjCustom);

        writeFmj(outFmj);
    }

    protected void writeFmj(JsonObject fmj) throws IOException {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var json = gson.toJson(fmj);
        json = json.replace("$", "\\u0024");

        Files.asCharSink(getFmj().getAsFile().get(), StandardCharsets.UTF_8).write(json);
    }

}

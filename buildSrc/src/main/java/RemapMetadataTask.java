import com.google.common.io.Files;
import com.google.gson.*;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class RemapMetadataTask extends CollectMetadataTask {

    @InputFile
    abstract RegularFileProperty getMapping();

    @Input
    abstract Property<String> getSourceNamespace();

    @Input
    abstract Property<String> getTargetNamespace();

    @Override
    public void collect() throws IOException {
        super.collect();

        var mapping = new MemoryMappingTree();
        MappingReader.read(getMapping().get().getAsFile().toPath(), new MappingSourceNsSwitch(mapping, getSourceNamespace().get(), false));
        var dst = getTargetNamespace().get();

        JsonObject fmj;
        try (var ir = Files.newReader(getFmj().getAsFile().get(), StandardCharsets.UTF_8)) {
            fmj = JsonParser.parseReader(ir).getAsJsonObject();
        }

        if (!fmj.has("custom")) return;
        var custom = fmj.getAsJsonObject("custom");

        if (!custom.has("loom:injected_interfaces")) return;
        var injectedInterfaces = custom.getAsJsonObject("loom:injected_interfaces");
        var newInjectedInterfaces = new JsonObject();

        injectedInterfaces.asMap().forEach((k, v) -> {
            var newArray = new JsonArray();
            v.getAsJsonArray().asList().stream().map(it -> {
                var s = it.getAsString().split("\\u003c", 2);
                var mClass = mapping.getClass(s[0]);
                if (mClass == null) return it;
                var newName = mClass.getName(dst);
                if (newName == null) return it;
                if (s.length == 2) newName = newName + "<" + s[1];
                return new JsonPrimitive(newName);
            }).forEach(newArray::add);
            newInjectedInterfaces.add(k, newArray);
        });

        custom.add("loom:injected_interfaces", newInjectedInterfaces);
        writeFmj(fmj);
    }

}

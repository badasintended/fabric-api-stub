import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.jvm.tasks.Jar;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public abstract class StubJar extends Jar {

    @InputFile
    public abstract RegularFileProperty getInputJar();

    @Override
    protected void copy() {
        var inFile = getInputJar().get().getAsFile();
        var outFile = getArchiveFile().get().getAsFile();

        super.copy();

        try (var inJar = new JarFile(inFile)) {
            var outJar = new JarOutputStream(new FileOutputStream(outFile));

            var entries = inJar.entries();
            while (entries.hasMoreElements()) {
                var inEntry = entries.nextElement();
                var outEntry = new JarEntry(inEntry.getName());
                outJar.putNextEntry(outEntry);

                try (var is = inJar.getInputStream(inEntry)) {
                    if (!inEntry.getName().endsWith(".class")) {
                        copyStream(is, outJar);
                        outJar.closeEntry();
                        continue;
                    }

                    var cr = new ClassReader(readAllBytes(is));
                    var node = new ClassNode();
                    cr.accept(node, 0);

                    for (var method : node.methods) {
                        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;

                        method.instructions.clear();
                        method.tryCatchBlocks.clear();
                        method.localVariables = null;

                        var insns = new InsnList();
                        insns.add(new TypeInsnNode(Opcodes.NEW, "java/lang/UnsupportedOperationException"));
                        insns.add(new InsnNode(Opcodes.DUP));
                        insns.add(new LdcInsnNode("stub"));
                        insns.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false));
                        insns.add(new InsnNode(Opcodes.ATHROW));

                        method.instructions.add(insns);
                        method.maxStack = Math.max(2, method.maxStack);
                    }

                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                    node.accept(cw);
                    outJar.write(cw.toByteArray());
                    outJar.closeEntry();
                }
            }

            outJar.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        var buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        var baos = new ByteArrayOutputStream();
        copyStream(in, baos);
        return baos.toByteArray();
    }

}

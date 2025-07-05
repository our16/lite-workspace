package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class CompileFileRecorder {
    private final File targetFile;
    private final Set<String> recorded = new HashSet<>();

    public CompileFileRecorder(Project project) {
        String base = project.getBasePath();
        this.targetFile = new File(base + "/src/test/resources/lite-workspace/compile-list.txt");
        this.targetFile.getParentFile().mkdirs();
        if (this.targetFile.exists()) {
            this.targetFile.delete(); // 每次构建新列表
        }
    }

    public void tryRecord(PsiClass clazz) {
        if (clazz == null || clazz.getQualifiedName() == null) return;
        String qName = clazz.getQualifiedName();
        if (recorded.contains(qName)) return;

        File f = new File(clazz.getContainingFile().getVirtualFile().getPath());
        try (FileWriter fw = new FileWriter(targetFile, true)) {
            fw.write(f.getAbsolutePath() + "\n");
            recorded.add(qName);
        } catch (IOException ignored) {}
    }
}

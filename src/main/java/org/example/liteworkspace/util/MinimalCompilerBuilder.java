package org.example.liteworkspace.util;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MinimalCompilerBuilder {

    public static void compile(Project project, Set<PsiClass> classSet) {
        Set<VirtualFile> files = classSet.stream()
                .map(PsiClass::getContainingFile)
                .filter(Objects::nonNull)
                .map(PsiFile::getVirtualFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (files.isEmpty()) {
            Messages.showInfoMessage(project, "未发现需要编译的类文件", "编译跳过");
            return;
        }

        CompilerManager compilerManager = CompilerManager.getInstance(project);
        CompileScope scope = compilerManager.createFilesCompileScope(files.toArray(new VirtualFile[0]));
        compilerManager.make(scope, (aborted, errors, warnings, compileContext) -> {
            String message = aborted
                    ? "编译被中止"
                    : (errors == 0
                    ? "最小依赖编译成功"
                    : "编译完成，有 " + errors + " 个错误和 " + warnings + " 个警告");
            Messages.showInfoMessage(project, message, "编译结果");
        });
    }

}

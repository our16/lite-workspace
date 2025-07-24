//package org.example.liteworkspace.util;
//
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.roots.ProjectFileIndex;
//import com.intellij.openapi.roots.SourceFolder;
//import com.intellij.openapi.vfs.VirtualFile;
//import com.intellij.psi.*;
//import com.intellij.psi.search.GlobalSearchScope;
//import com.intellij.psi.search.searches.ClassInheritorsSearch;
//import com.intellij.psi.util.PsiTreeUtil;
//
//import java.io.File;
//import java.util.*;
//
///**
// * 递归扫描某个 PsiClass 依赖的其他类，并找出它们在当前项目中的 .java 源码文件
// */
//public class DependencyClassScanner {
//
//    /**
//     * 从指定的主类开始，递归分析它依赖的所有项目内（非JDK/非第三方）类，
//     * 并返回这些类对应的 .java 源码文件路径列表
//     *
//     * @param project     当前 IntelliJ 项目
//     * @param mainClass   用户选中的主类（PsiClass）
//     * @return List<File> 需要编译的 .java 源码文件列表（属于当前项目）
//     */
//    public static List<File> findDependentJavaSourceFiles(Project project, PsiClass mainClass) {
//        Set<String> visitedQualifiedNames = new HashSet<>(); // 避免重复处理同一个类
//        List<File> sourceFilesToCompile = new ArrayList<>();
//
//        if (mainClass == null) {
//            return sourceFilesToCompile;
//        }
//
//        // 从主类开始递归
//        scanClassDependencies(project, mainClass, visitedQualifiedNames, sourceFilesToCompile);
//
//        return sourceFilesToCompile;
//    }
//
//    /**
//     * 递归扫描一个 PsiClass 的所有依赖类，并收集它们的 .java 源码文件
//     */
//    private static void scanClassDependencies(
//            Project project,
//            PsiClass psiClass,
//            Set<String> visited,
//            List<File> sourceFiles
//    ) {
//        if (psiClass == null || visited.contains(psiClass.getQualifiedName())) {
//            return;
//        }
//
//        // 标记当前类已访问
//        String qualifiedName = psiClass.getQualifiedName();
//        if (qualifiedName == null) {
//            return; // 匿名类 / 本地类，跳过
//        }
//        visited.add(qualifiedName);
//
//        System.out.println("[INFO] 正在分析类: " + qualifiedName);
//
//        // --- 1. 找到该类对应的 .java 源码文件，并加入待编译列表 ---
//        PsiFile containingFile = psiClass.getContainingFile();
//        if (containingFile != null && isProjectSourceFile(project, containingFile.getVirtualFile())) {
//            File sourceFile = new File(containingFile.getVirtualFile().getPath());
//            if (sourceFile.exists() && sourceFile.getName().endsWith(".java")) {
//                sourceFiles.add(sourceFile);
//                System.out.println("[INFO] 添加待编译源码文件: " + sourceFile.getAbsolutePath());
//            }
//        }
//
//        // --- 2. 递归分析：父类（extends）---
//        PsiClass superClass = psiClass.getSuperClass();
//        if (superClass != null) {
//            scanClassDependencies(project, superClass, visited, sourceFiles);
//        }
//
//        // --- 3. 递归分析：实现的接口（implements）---
//        for (PsiClass iface : psiClass.getInterfaces()) {
//            scanClassDependencies(project, iface, visited, sourceFiles);
//        }
//
//        // --- 4. 递归分析：字段类型 ---
//        for (PsiField field : psiClass.getFields()) {
//            PsiType fieldType = field.getType();
//            PsiClass resolvedFieldTypeClass = resolveClassFromType(project, fieldType);
//            if (resolvedFieldTypeClass != null) {
//                scanClassDependencies(project, resolvedFieldTypeClass, visited, sourceFiles);
//            }
//        }
//
//        // --- 5. 递归分析：方法参数类型 & 返回类型 ---
//        for (PsiMethod method : psiClass.getMethods()) {
//            // 方法返回类型
//            PsiType returnType = method.getReturnType();
//            PsiClass returnTypeClass = resolveClassFromType(project, returnType);
//            if (returnTypeClass != null) {
//                scanClassDependencies(project, returnTypeClass, visited, sourceFiles);
//            }
//
//            // 方法参数类型
//            for (PsiParameter param : method.getParameters()) {
//                PsiType paramType = param.getType();
//                PsiClass paramClass = resolveClassFromType(project, paramType);
//                if (paramClass != null) {
//                    scanClassDependencies(project, paramClass, visited, sourceFiles);
//                }
//            }
//        }
//    }
//
//    /**
//     * 判断一个 VirtualFile 是否属于当前项目的源码目录（而不是测试、外部库、生成的代码等）
//     */
//    private static boolean isProjectSourceFile(Project project, VirtualFile file) {
//        if (file == null) return false;
//        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
//        return fileIndex.isInSourceContent(file) && !fileIndex.isInLibraryClasses(file) && !fileIndex.isInLibrarySource(file);
//    }
//
//    /**
//     * 从 PsiType 解析出对应的 PsiClass（比如解析 "com.example.UserService"）
//     */
//    private static PsiClass resolveClassFromType(Project project, PsiType type) {
//        if (type == null) return null;
//
//        // 原始类型 / void / null 类型，没有对应类
//        if (type instanceof PsiPrimitiveType || type.equals(PsiType.VOID) || type.equals(PsiType.NULL)) {
//            return null;
//        }
//
//        // 获取类型对应的类（比如 PsiClassType -> resolve() 得到 PsiClass）
//        PsiClassType classType = PsiTypesUtil.getPsiClassType(type);
//        if (classType == null) {
//            return null;
//        }
//
//        PsiClass resolvedClass = classType.resolve();
//        if (resolvedClass == null || resolvedClass.getQualifiedName() == null) {
//            return null; // 无法解析，或匿名类 / 本地类
//        }
//
//        // 只处理当前项目内的类，不处理 JDK / 第三方库类
//        String qName = resolvedClass.getQualifiedName();
//        if (qName == null || qName.startsWith("java.") || qName.startsWith("javax.") || qName.startsWith("kotlin.")) {
//            return null;
//        }
//
//        return resolvedClass;
//    }
//}
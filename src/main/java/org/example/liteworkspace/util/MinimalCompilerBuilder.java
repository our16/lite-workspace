package org.example.liteworkspace.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MinimalCompilerBuilder {

    public static void compile(Project project, Set<PsiClass> classes) {
        Set<VirtualFile> vFiles = new HashSet<>();
        for (PsiClass psiClass : classes) {
            PsiFile psi = psiClass.getContainingFile();
            if (psi != null) {
                VirtualFile vf = psi.getVirtualFile();
                if (vf != null && vf.isValid()) vFiles.add(vf);
            }
        }
        if (vFiles.isEmpty()) return;

        VirtualFile[] toCompile = vFiles.toArray(VirtualFile[]::new);
        CompilerManager compiler = CompilerManager.getInstance(project);
        compiler.compile(toCompile, new CompileStatusNotification() {
            @Override
            public void finished(boolean aborted, int errors, int warnings, CompileContext context) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (aborted) {
                        Messages.showInfoMessage(project,
                                "Minimal build aborted.",
                                "Lite‑Workspace");
                    } else if (errors > 0) {
                        Messages.showErrorDialog(project,
                                "Compilation finished with " + errors + " errors, " + warnings + " warnings.",
                                "Lite‑Workspace");
                    } else {
                        Messages.showInfoMessage(project,
                                "Minimal build complete: " + toCompile.length + " source file(s) compiled",
                                "Lite‑Workspace");
                    }
                });
            }
        });
    }

    /** 解析 XML 获取 Java Bean 依赖和 mapperLocations 资源 */
    public static void collectDependenciesFromXml(Project project,
                                                  VirtualFile xmlVF,
                                                  Set<PsiClass> classes,
                                                  Set<VirtualFile> xmlResources) {
        try (InputStream in = xmlVF.getInputStream()) {
            Document doc = new SAXBuilder().build(in);
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

            // 1. <bean class="...">
            List<?> beanEls = XPath.selectNodes(doc, "//bean[@class]");
            for (Object o : beanEls) {
                org.jdom.Element el = (org.jdom.Element) o;
                String clsName = el.getAttributeValue("class");
                PsiClass cls = facade.findClass(clsName, scope);
                if (cls != null) classes.add(cls);
            }

            // 2. <bean><property name="mapperInterface" value="..." />
            List<?> propEls = XPath.selectNodes(doc, "//bean/property[@name='mapperInterface']");
            for (Object o : propEls) {
                org.jdom.Element prop = (org.jdom.Element) o;
                String iface = prop.getAttributeValue("value");
                PsiClass cls = facade.findClass(iface, scope);
                if (cls != null) classes.add(cls);
            }

            // 3. mapperLocations -> XML 文件资源
            List<?> valEls = XPath.selectNodes(doc, "//mapperLocations//value");
            for (Object o : valEls) {
                String path = ((org.jdom.Element) o).getTextTrim();
                if (path.startsWith("classpath:")) {
                    String rel = path.substring("classpath:".length());
                    VirtualFile vf = project.getBaseDir()
                            .findFileByRelativePath("src/main/resources/" + rel);
                    if (vf != null) {
                        xmlResources.add(vf);
                        copyToOutput(vf, project); // ✅ 复制 mapper XML
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 将 XML mapper 文件复制到测试编译输出路径 */
    private static void copyToOutput(VirtualFile vf, Project project) {
        Module m = ModuleUtilCore.findModuleForFile(vf, project);
        if (m == null) return;
        String rel = VfsUtil.getRelativePath(vf, project.getBaseDir());
        if (rel == null) return;
        String outPath = project.getBasePath() + "/out/test/" + m.getName() + "/";
        File dest = new File(outPath + rel.replaceFirst("src/(main|test)/resources/", ""));
        dest.getParentFile().mkdirs();
        try (InputStream in = vf.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
            in.transferTo(out);
        } catch (IOException ignored) {}
    }
}

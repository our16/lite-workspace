package org.example.liteworkspace.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.example.liteworkspace.bean.core.*;
import org.example.liteworkspace.bean.resolver.*;
import org.example.liteworkspace.bean.dependency.*;
import org.example.liteworkspace.bean.builder.*;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;

public class LiteScanAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || !(psiFile instanceof PsiJavaFile)) {
            return;
        }

        PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
        if (classes.length == 0) return;

        PsiClass targetClass = classes[0];

        if (targetClass == null) {
            Messages.showDialog(
                    project,
                    "请右键在类名上运行LiteWorkspace",
                    "LiteWorkspace 工具",
                    new String[]{"确定"},
                    0,
                    Messages.getInformationIcon()
            );
            return;
        }

        // 初始化组件
        BeanRegistry registry = new BeanRegistry();
        XmlBeanResolver xmlResolver = new XmlBeanResolver(project);

        BeanScanCoordinator coordinator = new BeanScanCoordinator(
                List.of(
                        new AnnotationBeanResolver(),
                        new BeanMethodResolver(project),
                        xmlResolver
                ),
                List.of(new DefaultDependencyResolver(project)),
                List.of(
                        new AnnotationBeanBuilder(),
                        new XmlBeanBuilder(xmlResolver),
                        new MyBatisMapperBuilder()
                )
        );
        // 扫描和注册
        coordinator.scanAndBuild(targetClass, registry);
        // 写入到文件
        writeBeanXmlToTestResources(project, targetClass, registry.getBeanXmlMap());
    }

    public void writeBeanXmlToTestResources(Project project, PsiClass clazz, Map<String, String> beanXmlMap) {
        try {
            // ✅ 获取所属 Module
            Module module = ModuleUtilCore.findModuleForPsiElement(clazz);
            if (module == null) {
                Messages.showErrorDialog(project, "未能找到类所属的 Module", "LiteWorkspace 错误");
                return;
            }

            // ✅ 查找 test/resources 根目录
            VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
            VirtualFile testResourceDir = null;

            for (VirtualFile root : sourceRoots) {
                String path = root.getPath().replace("\\", "/");
                if (path.contains("/src/test/resources")) {
                    testResourceDir = root;
                    break;
                }
            }

            if (testResourceDir == null) {
                Messages.showErrorDialog(project, "未找到 test/resources 目录，请确认项目结构", "LiteWorkspace 错误");
                return;
            }

            // ✅ 构造 XML 内容
            StringBuilder result = new StringBuilder();
            result.append("<beans xmlns=\"http://www.springframework.org/schema/beans\"\n");
            result.append("       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            result.append("       xsi:schemaLocation=\"http://www.springframework.org/schema/beans\n");
            result.append("        http://www.springframework.org/schema/beans/spring-beans.xsd\">\n\n");

            for (String xml : beanXmlMap.values()) {
                result.append(xml).append("\n");
            }
            result.append("</beans>");

            // ✅ 写入文件
            File targetFile = new File(testResourceDir.getPath(), "spring.xml");
            try (FileWriter writer = new FileWriter(targetFile)) {
                writer.write(result.toString());
            }

            Messages.showInfoMessage(project,
                    "✅ Spring XML 生成成功：\n" + targetFile.getAbsolutePath(),
                    "LiteWorkspace");

            // ✅ 刷新 VirtualFileSystem，让 IDEA 识别新文件
            VfsUtil.findFileByIoFile(targetFile, true).refresh(false, false);

        } catch (Exception ex) {
            Messages.showErrorDialog(project,
                    "❌ 写入 spring.xml 失败：" + ex.getMessage(),
                    "LiteWorkspace 错误");
        }
    }

}

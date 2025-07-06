package org.example.liteworkspace.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.example.liteworkspace.bean.core.*;
import org.example.liteworkspace.bean.resolver.*;
import org.example.liteworkspace.bean.dependency.*;
import org.example.liteworkspace.bean.builder.*;

import java.util.List;

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

        coordinator.scanAndBuild(targetClass, registry);

        // 构造输出内容
        StringBuilder result = new StringBuilder();
        for (String xml : registry.getBeanXmlMap().values()) {
            result.append(xml).append("\n");
        }
        Messages.showDialog(
                project,
                result.toString(),
                "生成的 Spring Bean XML",
                new String[]{"确定"},
                0,
                Messages.getInformationIcon()
        );

    }
}

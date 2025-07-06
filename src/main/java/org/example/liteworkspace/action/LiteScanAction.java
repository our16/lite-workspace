package org.example.liteworkspace.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.example.liteworkspace.bean.core.*;
import org.example.liteworkspace.bean.resolver.*;
import org.example.liteworkspace.bean.dependency.*;
import org.example.liteworkspace.bean.builder.*;

import java.util.List;

public class LiteScanAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // 获取当前选中的 PsiClass（类）
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);

        PsiClass targetClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (targetClass == null) {
            Messages.showInfoMessage(project, "请在 Java 类上右键使用此操作。", "LiteWorkspace");
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

        Messages.showMultilineInputDialog(
                project,
                result.toString(),                     // 内容
                "生成的 Spring Bean XML",             // 弹窗标题
                "",                                    // 提示信息
                null,                                  // icon
                null                                   // validator
        );

    }
}

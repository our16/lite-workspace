package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;

import java.io.File;

public class TestXmlLocator {

    public static VirtualFile findTestXmlForClass(Project project, PsiClass psiClass) {
        String qName = psiClass.getQualifiedName(); // com.whu.cloudstudy_server.controller.UserController
        if (qName == null) {
            return null;
        }
        // D:\project\springboot-mybatis-demo\src\test\resources\com\whu\cloudstudy_server\controller\UserController.xml
        String relPath = "src/test/resources/" + qName.replace('.', '/') + ".xml";
        File xmlFile = new File(project.getBasePath(), relPath);
        if (!xmlFile.exists()) {
            return null;
        }

        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(xmlFile);
    }
}

package org.example.liteworkspace.bean.builder;

import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.example.liteworkspace.bean.BeanDefinitionBuilder;
import org.example.liteworkspace.bean.core.BeanOrigin;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.resolver.XmlBeanResolver;

public class XmlBeanBuilder implements BeanDefinitionBuilder {
    private final XmlBeanResolver resolver;

    public XmlBeanBuilder(XmlBeanResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void buildBean(PsiClass clazz, BeanRegistry registry) {
        String fqcn = clazz.getQualifiedName();
        if (fqcn == null) return;

        XmlFile xmlFile = resolver.findXmlFile(clazz);
        if (xmlFile == null) return;

        XmlTag root = xmlFile.getRootTag();
        if (root == null) return;

        for (XmlTag bean : root.findSubTags("bean")) {
            if (fqcn.equals(bean.getAttributeValue("class"))) {
                String id = bean.getAttributeValue("id");
                if (id == null || id.isEmpty()) {
                    id = decapitalize(clazz.getName());
                }
                registry.register(id, "    " + bean.getText() + "\n", BeanOrigin.XML);
                return;
            }
        }
    }

    private String decapitalize(String name) {
        return name == null || name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
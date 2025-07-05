package org.example.liteworkspace.util;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Set;

public class XmlBeanParser {

    private final Document document;

    public XmlBeanParser(File xmlFile) {
        this.document = parseXml(xmlFile);
    }

    private Document parseXml(File file) {
        if (file == null || !file.exists()) return null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setIgnoringComments(true);
            factory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(file);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean containsClass(Set<String> classNames) {
        if (document == null) return false;

        NodeList beanList = document.getElementsByTagName("bean");
        for (int i = 0; i < beanList.getLength(); i++) {
            Node node = beanList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element bean = (Element) node;
                String clazz = bean.getAttribute("class");
                if (clazz != null && classNames.contains(clazz.trim())) {
                    return true;
                }
            }
        }
        return false;
    }
}

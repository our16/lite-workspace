package org.example.liteworkspace.bean.core;

import java.util.*;

public class BeanRegistry {
    private final Set<String> visited = new HashSet<>();
    private final Map<String, String> beanXmlMap = new LinkedHashMap<>();
    private final Map<String, BeanOrigin> origins = new HashMap<>();

    public boolean isVisited(String fqcn) {
        return visited.contains(fqcn);
    }

    public void markVisited(String fqcn) {
        visited.add(fqcn);
    }

    public void register(String id, String beanXml, BeanOrigin origin) {
        beanXmlMap.putIfAbsent(id, beanXml);
        origins.putIfAbsent(id, origin);
    }

    public Map<String, String> getBeanXmlMap() {
        return beanXmlMap;
    }

    public Map<String, BeanOrigin> getOrigins() {
        return origins;
    }
}
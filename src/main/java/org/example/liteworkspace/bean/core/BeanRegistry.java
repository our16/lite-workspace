package org.example.liteworkspace.bean.core;

import java.util.*;

public class BeanRegistry {

    private final Map<String, BeanDefinition> beanMap = new LinkedHashMap<>();

    public void register(BeanDefinition bean) {
        beanMap.putIfAbsent(bean.getBeanName(), bean);
    }

    public Collection<BeanDefinition> getAllBeans() {
        return beanMap.values();
    }

    public boolean contains(String beanName) {
        return beanMap.containsKey(beanName);
    }

    public BeanDefinition get(String beanName) {
        return beanMap.get(beanName);
    }

    public void clear() {
        beanMap.clear();
    }
}
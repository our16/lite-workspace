package org.example.liteworkspace.bean.core.enums;

public enum BeanType {
    ANNOTATION,
    XML,
    MYBATIS,
    JAVA_CONFIG,
    MAPPER,
    // 新增：表示普通 Java 类，不是 Spring / MyBatis 管理的 Bean
    PLAIN,
    MAPPER_STRUCT,
    ;
}
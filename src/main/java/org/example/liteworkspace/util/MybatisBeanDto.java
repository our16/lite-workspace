package org.example.liteworkspace.util;

public class MybatisBeanDto {

    private String mapperInterface;

    private String xmlFilePath;

    private String  sqlSessionFactory;

    public MybatisBeanDto(String mapperInterface, String xmlFilePath, String sqlSessionFactory) {
        this.mapperInterface = mapperInterface;
        this.xmlFilePath = xmlFilePath;
        this.sqlSessionFactory = sqlSessionFactory;
    }

    public String getMapperInterface() {
        return mapperInterface;
    }

    public String getXmlFilePath() {
        return xmlFilePath;
    }

    public String getSqlSessionFactory() {
        return sqlSessionFactory;
    }
}

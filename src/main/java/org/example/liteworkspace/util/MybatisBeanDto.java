package org.example.liteworkspace.util;


/**
 * 定义mybatis 对象，和对应的数据源
 */
public class MybatisBeanDto {

    /**
     * dao 包路径
     */
    private String mapperInterface;

    /**
     * dao 对应的mapper路径
     */
    private String xmlFilePath;

    /**
     * 对应的数据源
     */
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

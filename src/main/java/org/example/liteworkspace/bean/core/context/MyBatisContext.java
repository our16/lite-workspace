package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.datasource.SqlSessionConfig;
import org.example.liteworkspace.util.JSONUtil;
import org.example.liteworkspace.util.LogUtil;
import org.example.liteworkspace.util.MyBatisXmlFinder;
import org.example.liteworkspace.util.MybatisBeanDto;

import java.util.*;

/**
 * mybatis上下文内容：
 * 1、xml定义的mapper，和对应的dao
 * 2、包含哪些数据源以及每个数据源对应的扫描范围
 */
public class MyBatisContext {

    private final MyBatisXmlFinder myBatisXmlFinder;

    private final List<SqlSessionConfig> sqlSessionConfigList;

    private final Map<String, MybatisBeanDto> namespace2XmlFileMap = new HashMap<>();

    public MyBatisContext(Project project, List<SqlSessionConfig> sqlSessionConfigList) {
        this.myBatisXmlFinder = new MyBatisXmlFinder(project);
        this.sqlSessionConfigList = sqlSessionConfigList;
    }

    public void refresh() {
        LogUtil.info("start refresh myBatisContext, sql session config list{}", JSONUtil.toJsonStr(sqlSessionConfigList));
        Map<String, MybatisBeanDto> namespace2dao = myBatisXmlFinder.scanAllMapperXml(sqlSessionConfigList);
        namespace2XmlFileMap.putAll(namespace2dao);
        LogUtil.info("end refresh myBatisContext, namespace map size:{}", namespace2dao.size());
    }

    public Map<String, MybatisBeanDto> getNamespace2XmlFileMap() {
        return namespace2XmlFileMap;
    }

    public boolean hasMatchingMapperXml(PsiClass clazz) {
        return namespace2XmlFileMap.containsKey(clazz.getQualifiedName());
    }
}


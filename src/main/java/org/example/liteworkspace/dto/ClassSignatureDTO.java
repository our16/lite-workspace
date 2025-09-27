package org.example.liteworkspace.dto;

import java.util.List;
import java.util.Objects;

/**
 * 类签名DTO，用于轻量级存储类信息而不保存PSI对象
 */
public class ClassSignatureDTO {
    private final String qualifiedName; // 类的全限定名
    private final String simpleName;   // 类的简单名称
    private final List<String> interfaceNames; // 实现的接口全限定名列表
    private final String superClassName; // 父类的全限定名
    private final boolean isInterface;  // 是否是接口
    private final boolean isAnnotation; // 是否是注解
    private final boolean isEnum;       // 是否是枚举
    private final String packageName;   // 包名

    public ClassSignatureDTO(String qualifiedName, String simpleName, 
                            List<String> interfaceNames, String superClassName,
                            boolean isInterface, boolean isAnnotation, 
                            boolean isEnum, String packageName) {
        this.qualifiedName = qualifiedName;
        this.simpleName = simpleName;
        this.interfaceNames = interfaceNames;
        this.superClassName = superClassName;
        this.isInterface = isInterface;
        this.isAnnotation = isAnnotation;
        this.isEnum = isEnum;
        this.packageName = packageName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public List<String> getInterfaceNames() {
        return interfaceNames;
    }

    public String getSuperClassName() {
        return superClassName;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public boolean isAnnotation() {
        return isAnnotation;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public String getPackageName() {
        return packageName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassSignatureDTO that = (ClassSignatureDTO) o;
        return isInterface == that.isInterface &&
                isAnnotation == that.isAnnotation &&
                isEnum == that.isEnum &&
                Objects.equals(qualifiedName, that.qualifiedName) &&
                Objects.equals(simpleName, that.simpleName) &&
                Objects.equals(interfaceNames, that.interfaceNames) &&
                Objects.equals(superClassName, that.superClassName) &&
                Objects.equals(packageName, that.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(qualifiedName, simpleName, interfaceNames, superClassName, 
                           isInterface, isAnnotation, isEnum, packageName);
    }

    @Override
    public String toString() {
        return "ClassSignatureDTO{" +
                "qualifiedName='" + qualifiedName + '\'' +
                ", simpleName='" + simpleName + '\'' +
                ", interfaceNames=" + interfaceNames +
                ", superClassName='" + superClassName + '\'' +
                ", isInterface=" + isInterface +
                ", isAnnotation=" + isAnnotation +
                ", isEnum=" + isEnum +
                ", packageName='" + packageName + '\'' +
                '}';
    }
}
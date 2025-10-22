package org.example.liteworkspace.dto;

import java.util.List;
import java.util.Objects;

/**
 * 方法签名DTO，用于轻量级存储方法信息而不保存PSI对象
 */
public class MethodSignatureDTO {
    private final String classFqn;   // 所属类的全限定名
    private final String methodName; // 方法名
    private final List<String> parameterTypes; // 参数类型的FQN
    private final String returnType; // 返回值类型FQN

    public MethodSignatureDTO(String classFqn, String methodName,
                              List<String> parameterTypes, String returnType) {
        this.classFqn = classFqn;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }

    public String getClassFqn() {
        return classFqn;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public String getReturnType() {
        return returnType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodSignatureDTO that = (MethodSignatureDTO) o;
        return Objects.equals(classFqn, that.classFqn) &&
                Objects.equals(methodName, that.methodName) &&
                Objects.equals(parameterTypes, that.parameterTypes) &&
                Objects.equals(returnType, that.returnType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classFqn, methodName, parameterTypes, returnType);
    }

    @Override
    public String toString() {
        return "MethodSignatureDTO{" +
                "classFqn='" + classFqn + '\'' +
                ", methodName='" + methodName + '\'' +
                ", parameterTypes=" + parameterTypes +
                ", returnType='" + returnType + '\'' +
                '}';
    }
}
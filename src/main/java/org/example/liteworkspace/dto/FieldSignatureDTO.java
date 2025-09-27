package org.example.liteworkspace.dto;

import java.util.Objects;

/**
 * 字段签名DTO，用于轻量级存储字段信息而不保存PSI对象
 */
public class FieldSignatureDTO {
    private final String classFqn;     // 所属类的全限定名
    private final String fieldName;    // 字段名
    private final String fieldType;    // 字段类型的FQN
    private final boolean isStatic;    // 是否是静态字段
    private final boolean isFinal;     // 是否是final字段
    private final boolean isTransient; // 是否是transient字段
    private final boolean isVolatile;  // 是否是volatile字段

    public FieldSignatureDTO(String classFqn, String fieldName, String fieldType,
                             boolean isStatic, boolean isFinal, boolean isTransient, 
                             boolean isVolatile) {
        this.classFqn = classFqn;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
        this.isTransient = isTransient;
        this.isVolatile = isVolatile;
    }

    public String getClassFqn() {
        return classFqn;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public boolean isTransient() {
        return isTransient;
    }

    public boolean isVolatile() {
        return isVolatile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldSignatureDTO that = (FieldSignatureDTO) o;
        return isStatic == that.isStatic &&
                isFinal == that.isFinal &&
                isTransient == that.isTransient &&
                isVolatile == that.isVolatile &&
                Objects.equals(classFqn, that.classFqn) &&
                Objects.equals(fieldName, that.fieldName) &&
                Objects.equals(fieldType, that.fieldType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classFqn, fieldName, fieldType, isStatic, isFinal, 
                           isTransient, isVolatile);
    }

    @Override
    public String toString() {
        return "FieldSignatureDTO{" +
                "classFqn='" + classFqn + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", fieldType='" + fieldType + '\'' +
                ", isStatic=" + isStatic +
                ", isFinal=" + isFinal +
                ", isTransient=" + isTransient +
                ", isVolatile=" + isVolatile +
                '}';
    }
}
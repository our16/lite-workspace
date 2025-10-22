package org.example.liteworkspace.dto;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.example.liteworkspace.util.LogUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * PSI对象到DTO对象的转换工具类
 * 用于将PSI对象快速转换为轻量级DTO，避免长期保存PSI对象
 */
public class PsiToDtoConverter {

    /**
     * 将PsiClass转换为ClassSignatureDTO
     */
    public static ClassSignatureDTO convertToClassSignature(PsiClass psiClass) {
        if (psiClass == null) {
            return null;
        }

        String qualifiedName = psiClass.getQualifiedName();
        String simpleName = psiClass.getName();
        
        // 获取实现的接口
        List<String> interfaceNames = new ArrayList<>();
        for (PsiClassType interfaceType : psiClass.getImplementsListTypes()) {
            PsiClass interfaceClass = interfaceType.resolve();
            if (interfaceClass != null) {
                String interfaceName = interfaceClass.getQualifiedName();
                if (interfaceName != null) {
                    interfaceNames.add(interfaceName);
                }
            }
        }
        
        // 获取父类
        String superClassName = null;
        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null && !"java.lang.Object".equals(superClass.getQualifiedName())) {
            superClassName = superClass.getQualifiedName();
        }
        
        // 获取包名
        String packageName = "";
        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile instanceof PsiJavaFile) {
            packageName = ((PsiJavaFile) containingFile).getPackageName();
            if (packageName == null) {
                packageName = "";
            }
        }

        return new ClassSignatureDTO(
                qualifiedName,
                simpleName,
                interfaceNames,
                superClassName,
                psiClass.isInterface(),
                psiClass.isAnnotationType(),
                psiClass.isEnum(),
                packageName
        );
    }

    /**
     * 将PsiMethod转换为MethodSignatureDTO
     */
    public static MethodSignatureDTO convertToMethodSignature(PsiMethod psiMethod) {
        if (psiMethod == null) {
            return null;
        }

        PsiClass containingClass = psiMethod.getContainingClass();
        String classFqn = containingClass != null ? containingClass.getQualifiedName() : null;
        String methodName = psiMethod.getName();
        
        // 获取参数类型
        List<String> parameterTypes = new ArrayList<>();
        for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
            String parameterType = getTypeName(parameter.getType());
            parameterTypes.add(parameterType);
        }
        
        // 获取返回类型
        String returnType = getTypeName(psiMethod.getReturnType());

        return new MethodSignatureDTO(classFqn, methodName, parameterTypes, returnType);
    }

    /**
     * 将PsiField转换为FieldSignatureDTO
     */
    public static FieldSignatureDTO convertToFieldSignature(PsiField psiField) {
        if (psiField == null) {
            return null;
        }

        PsiClass containingClass = psiField.getContainingClass();
        String classFqn = containingClass != null ? containingClass.getQualifiedName() : null;
        String fieldName = psiField.getName();
        String fieldType = getTypeName(psiField.getType());
        
        boolean isStatic = psiField.hasModifierProperty(PsiModifier.STATIC);
        boolean isFinal = psiField.hasModifierProperty(PsiModifier.FINAL);
        boolean isTransient = psiField.hasModifierProperty(PsiModifier.TRANSIENT);
        boolean isVolatile = psiField.hasModifierProperty(PsiModifier.VOLATILE);

        return new FieldSignatureDTO(classFqn, fieldName, fieldType, isStatic, isFinal, isTransient, isVolatile);
    }

    /**
     * 获取PsiType的类型名称
     */
    private static String getTypeName(PsiType type) {
        if (type == null) {
            return "void";
        }
        
        if (type instanceof PsiPrimitiveType) {
            return type.getPresentableText();
        }
        
        if (type instanceof PsiArrayType) {
            PsiArrayType arrayType = (PsiArrayType) type;
            return getTypeName(arrayType.getComponentType()) + "[]";
        }
        
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiClass psiClass = classType.resolve();
            if (psiClass != null) {
                return psiClass.getQualifiedName();
            }
        }
        
        return type.getPresentableText();
    }

    /**
     * 批量将PsiClass数组转换为ClassSignatureDTO列表
     */
    public static List<ClassSignatureDTO> convertClassesToDto(List<PsiClass> psiClasses) {
        List<ClassSignatureDTO> result = new ArrayList<>();
        if (psiClasses == null) {
            return result;
        }
        
        for (PsiClass psiClass : psiClasses) {
            ClassSignatureDTO dto = convertToClassSignature(psiClass);
            if (dto != null) {
                result.add(dto);
            }
        }
        
        return result;
    }

    /**
     * 批量将PsiMethod数组转换为MethodSignatureDTO列表
     */
    public static List<MethodSignatureDTO> convertMethodsToDto(List<PsiMethod> psiMethods) {
        List<MethodSignatureDTO> result = new ArrayList<>();
        if (psiMethods == null) {
            return result;
        }
        
        for (PsiMethod psiMethod : psiMethods) {
            MethodSignatureDTO dto = convertToMethodSignature(psiMethod);
            if (dto != null) {
                result.add(dto);
            }
        }
        
        return result;
    }

    /**
     * 批量将PsiField数组转换为FieldSignatureDTO列表
     */
    public static List<FieldSignatureDTO> convertFieldsToDto(List<PsiField> psiFields) {
        List<FieldSignatureDTO> result = new ArrayList<>();
        if (psiFields == null) {
            return result;
        }
        
        for (PsiField psiField : psiFields) {
            FieldSignatureDTO dto = convertToFieldSignature(psiField);
            if (dto != null) {
                result.add(dto);
            }
        }
        
        return result;
    }
}
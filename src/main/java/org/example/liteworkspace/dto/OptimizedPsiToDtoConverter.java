package org.example.liteworkspace.dto;

import com.intellij.psi.*;
import org.example.liteworkspace.util.LogUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PSI 对象到 DTO 对象转换工具类
 * 
 * 简化版本：完全移除缓存机制，避免项目间数据污染
 */
public class OptimizedPsiToDtoConverter {
    
    // 项目级别的实例缓存（简化版，仅存储实例本身）
    private static final Map<String, OptimizedPsiToDtoConverter> instances = new HashMap<>();
    
    public static synchronized OptimizedPsiToDtoConverter getInstance(String projectId) {
        return instances.computeIfAbsent(projectId, id -> {
            OptimizedPsiToDtoConverter converter = new OptimizedPsiToDtoConverter();
            converter.projectId = id;
            return converter;
        });
    }
    
    /**
     * 清理指定项目的实例
     */
    public static synchronized void clearInstance(String projectId) {
        instances.remove(projectId);
        LogUtil.info("已清理项目 {} 的转换器实例", projectId);
    }
    
    /**
     * 清理所有实例
     */
    public static synchronized void clearAllInstances() {
        instances.clear();
        LogUtil.info("已清理所有转换器实例");
    }
    
    // 项目标识
    private String projectId;
    
    private OptimizedPsiToDtoConverter() {
        // 无缓存，无后台线程
    }
    
    /**
     * 将 PsiClass 转换为 ClassSignatureDTO（无缓存版本）
     */
    public ClassSignatureDTO convertToClassSignature(PsiClass psiClass) {
        if (psiClass == null) {
            return null;
        }
        
        LogUtil.debug("转换类: {}", psiClass.getQualifiedName());
        
        // 直接执行转换，无缓存
        return performClassConversion(psiClass);
    }
    
    /**
     * 将 PsiMethod 转换为 MethodSignatureDTO（无缓存版本）
     */
    public MethodSignatureDTO convertToMethodSignature(PsiMethod psiMethod) {
        if (psiMethod == null) {
            return null;
        }
        
        LogUtil.debug("转换方法: {}", psiMethod.getName());
        
        // 直接执行转换，无缓存
        return performMethodConversion(psiMethod);
    }
    
    /**
     * 将 PsiField 转换为 FieldSignatureDTO（无缓存版本）
     */
    public FieldSignatureDTO convertToFieldSignature(PsiField psiField) {
        if (psiField == null) {
            return null;
        }
        
        LogUtil.debug("转换字段: {}", psiField.getName());
        
        // 直接执行转换，无缓存
        return performFieldConversion(psiField);
    }
    
    /**
     * 批量转换 PsiClass 列表（无缓存版本）
     */
    public List<ClassSignatureDTO> convertClassesToDto(List<PsiClass> psiClasses) {
        if (psiClasses == null || psiClasses.isEmpty()) {
            return new ArrayList<>();
        }
        
        LogUtil.debug("批量转换 {} 个类", psiClasses.size());
        
        return psiClasses.stream()
                .map(this::convertToClassSignature)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 批量转换 PsiMethod 列表（无缓存版本）
     */
    public List<MethodSignatureDTO> convertMethodsToDto(List<PsiMethod> psiMethods) {
        if (psiMethods == null || psiMethods.isEmpty()) {
            return new ArrayList<>();
        }
        
        LogUtil.debug("批量转换 {} 个方法", psiMethods.size());
        
        return psiMethods.stream()
                .map(this::convertToMethodSignature)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 批量转换 PsiField 列表（无缓存版本）
     */
    public List<FieldSignatureDTO> convertFieldsToDto(List<PsiField> psiFields) {
        if (psiFields == null || psiFields.isEmpty()) {
            return new ArrayList<>();
        }
        
        LogUtil.debug("批量转换 {} 个字段", psiFields.size());
        
        return psiFields.stream()
                .map(this::convertToFieldSignature)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 执行实际的类转换逻辑
     */
    private ClassSignatureDTO performClassConversion(PsiClass psiClass) {
        String qualifiedName = psiClass.getQualifiedName();
        String simpleName = psiClass.getName();
        
        // 获取实现的接口
        List<String> interfaceNames = Arrays.stream(psiClass.getImplementsListTypes())
                .map(PsiClassType::resolve)
                .filter(Objects::nonNull)
                .map(PsiClass::getQualifiedName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
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
     * 执行实际的方法转换逻辑
     */
    private MethodSignatureDTO performMethodConversion(PsiMethod psiMethod) {
        PsiClass containingClass = psiMethod.getContainingClass();
        String classFqn = containingClass != null ? containingClass.getQualifiedName() : null;
        String methodName = psiMethod.getName();
        
        // 获取参数类型
        List<String> parameterTypes = Arrays.stream(psiMethod.getParameterList().getParameters())
                .map(PsiParameter::getType)
                .map(this::getTypeName)
                .collect(Collectors.toList());
        
        // 获取返回类型
        String returnType = getTypeName(psiMethod.getReturnType());

        return new MethodSignatureDTO(classFqn, methodName, parameterTypes, returnType);
    }
    
    /**
     * 执行实际字段转换逻辑
     */
    private FieldSignatureDTO performFieldConversion(PsiField psiField) {
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
     * 获取 PsiType 的类型名称
     */
    private String getTypeName(PsiType type) {
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
     * 获取转换器信息（无缓存版本）
     */
    public String getConverterInfo() {
        return String.format(
            "ConverterInfo{projectId=%s, instances=%d, noCache=true}",
            projectId,
            instances.size()
        );
    }
}

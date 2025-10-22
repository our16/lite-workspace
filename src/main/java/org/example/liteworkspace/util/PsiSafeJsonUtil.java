package org.example.liteworkspace.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.dto.FieldSignatureDTO;
import org.example.liteworkspace.dto.MethodSignatureDTO;
import org.example.liteworkspace.dto.PsiToDtoConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * 安全序列化 PSI 对象工具类
 * 使用轻量级DTO替代PSI对象，避免长期保存PSI对象
 */
public class PsiSafeJsonUtil {

    private static final ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /**
     * 将对象安全转成 JSON 字符串
     */
    public static String toJsonStr(Object obj) {
        if (obj == null) {
            return "null";
        }

        Object safeObj = (obj instanceof PsiElement)
                ? toSafeDto((PsiElement) obj)
                : obj;

        try {
            return mapper.writeValueAsString(safeObj);
        } catch (JsonProcessingException e) {
            return "\"<serialize-error>\"";
        }
    }

    /**
     * 将 PSI 对象转为安全 DTO
     */
    private static Object toSafeDto(PsiElement element) {
        return ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<Object>) () -> {
            if (element instanceof PsiClass psiClass) {
                // 使用ClassSignatureDTO替代Map
                return PsiToDtoConverter.convertToClassSignature(psiClass);
            } else if (element instanceof PsiMethod psiMethod) {
                // 使用MethodSignatureDTO替代Map
                return PsiToDtoConverter.convertToMethodSignature(psiMethod);
            } else if (element instanceof PsiField psiField) {
                // 使用FieldSignatureDTO替代Map
                return PsiToDtoConverter.convertToFieldSignature(psiField);
            } else if (element instanceof PsiVariable psiVar) {
                // 对于其他变量类型，使用Map保持兼容性
                Map<String, Object> dto = new HashMap<>();
                dto.put("psiType", element.getClass().getSimpleName());
                dto.put("varName", psiVar.getName());
                dto.put("varType", psiVar.getType().getPresentableText());
                return dto;
            } else {
                // 兜底：只输出文本长度，避免整个 AST
                Map<String, Object> dto = new HashMap<>();
                dto.put("psiType", element.getClass().getSimpleName());
                String text = element.getText();
                dto.put("textLength", text != null ? text.length() : 0);
                return dto;
            }
        });
    }
}


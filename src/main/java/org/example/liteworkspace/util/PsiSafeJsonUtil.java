package org.example.liteworkspace.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;

import java.util.HashMap;
import java.util.Map;

/**
 * 安全序列化 PSI 对象工具类
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
    private static Map<String, Object> toSafeDto(PsiElement element) {
        return ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<Map<String, Object>>) () -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("psiType", element.getClass().getSimpleName());

            if (element instanceof PsiClass psiClass) {
                dto.put("qualifiedName", psiClass.getQualifiedName());
                dto.put("name", psiClass.getName());
            } else if (element instanceof PsiMethod psiMethod) {
                dto.put("methodName", psiMethod.getName());
                dto.put("returnType", psiMethod.getReturnType() != null ? psiMethod.getReturnType().getPresentableText() : null);
            } else if (element instanceof PsiVariable psiVar) {
                dto.put("varName", psiVar.getName());
                dto.put("varType", psiVar.getType().getPresentableText());
            } else {
                // 兜底：只输出文本长度，避免整个 AST
                String text = element.getText();
                dto.put("textLength", text != null ? text.length() : 0);
            }

            return dto;
        });
    }
}


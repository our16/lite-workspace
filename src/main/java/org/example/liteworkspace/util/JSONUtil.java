package org.example.liteworkspace.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSONUtil {

    private static final Logger log = LoggerFactory.getLogger(JSONUtil.class);

    private static final ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    /**
     * 对象转 JSON 字符串
     *
     * @param obj 待序列化对象
     * @return JSON 字符串，序列化失败时返回 null
     */
    public static String toJsonStr(Object obj) {
        if (obj == null) {
            return "null";
        }

        return PsiSafeJsonUtil.toJsonStr(obj);
    }
}

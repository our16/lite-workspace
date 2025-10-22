package org.example.liteworkspace.model.provider;

public interface LlmProvider {
    /**
     * 调用大模型，返回生成的回复内容
     * @param prompt 用户输入
     * @return 模型生成的文本
     * @throws Exception 调用失败时抛出异常
     */
    String invoke(String prompt) throws Exception;
}
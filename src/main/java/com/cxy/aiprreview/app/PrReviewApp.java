package com.cxy.aiprreview.app;
import com.cxy.aiprreview.advisor.MyLoggerAdvisor;
import com.cxy.aiprreview.dto.ReviewReport;
import com.cxy.aiprreview.excption.BusinessException;
import com.cxy.aiprreview.excption.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.Map;


@Component
public class PrReviewApp {
    private final ChatClient chatClient;
    private static final String SYSTEM_PROMPT = """
            你是一个资深的 Java 架构师和技术专家。请对以下代码改动（Code Diff）进行严格的代码审查（Code Review）。
                            请重点关注以下几点：
                            1. 是否存在内存泄漏、死循环或空指针异常等潜在 Bug？
                            2. 代码是否符合清洁代码规范（命名、冗余度、可读性）？
                            3. 是否有性能优化的空间？

                            请直接给出具体的修改建议，如果可以，请提供优化后的代码片段。

                            以下是需要审查的代码改动：
                            {diff}
            """;
    private static final String JSON_FORMAT_PROMPT = """
            你是一个资深的 Java 架构师和技术专家。请对以下代码改动（Code Diff）进行严格的代码审查（Code Review）。
            
            请重点关注以下几点：
            1. 是否存在内存泄漏、死循环或空指针异常等潜在 Bug？
            2. 代码是否符合清洁代码规范（命名、冗余度、可读性）？
            3. 是否有性能优化的空间？
            
            【重要】请以 JSON 格式输出审查结果，必须严格遵循以下结构：
            {{
                          "comments": [
                            {{
                              "filePath": "文件路径（如果无法确定，使用 'unknown'）",
                              "lineNumber": 行号（整数，如果无法确定，使用 0）,
                              "suggestion": "详细的审查意见和改进建议",
                              "codeSnippet": "优化后的代码片段（可选，如果没有则为空字符串）"
                            }}
                          ]
                        }}
            
            只返回 JSON 数据，不要包含任何其他说明文字。
            
            以下是需要审查的代码改动：
            {diff}
            """;

    public PrReviewApp(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MyLoggerAdvisor()
                )
                .build();
    }

    /***
     * AI审查代码
     * @param codeDiff 代码改动
     * @return AI恢复的建议
     */
    public String reviewCode(String codeDiff) {
        //1.判断改动是否为空
        if (codeDiff == null || codeDiff.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码未改动");
        }
        // 2. 使用 Spring AI 的 PromptTemplate 动态渲染参数
        PromptTemplate promptTemplate = new PromptTemplate(SYSTEM_PROMPT);
        Prompt prompt = promptTemplate.create(Map.of("diff", codeDiff));

        // 3. 调用大模型并获取文本结果
        ChatResponse chatResponse = chatClient.prompt(prompt)
                .system(SYSTEM_PROMPT )
                .call()
                .chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "大模型返回结果为空");
        }
        String content = chatResponse.getResult().getOutput().getText();
        return content;
    }
    /***
     * AI审查代码
     * @param codeDiff 代码改动
     * @return AI回复json格式建议
     */
    public ReviewReport getAiReview(String codeDiff) {
        // 定义转换器
        var converter = new BeanOutputConverter<>(ReviewReport.class);

        PromptTemplate promptTemplate = new PromptTemplate(JSON_FORMAT_PROMPT);
        Prompt prompt = promptTemplate.create(Map.of(
                "diff", codeDiff,
                "format", converter.getFormat() // 自动注入 JSON 约束
        ));

        ChatResponse chatResponse = chatClient.prompt(prompt)
                .system(JSON_FORMAT_PROMPT )
                .call()
                .chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "大模型返回结果为空");
        }
        String content = chatResponse.getResult().getOutput().getText();
        if(content == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "大模型返回结果为空");
        }
        // 将 AI 的 JSON 回复自动转为 Java 对象
        return converter.convert(content);
    }

}

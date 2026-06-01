package com.cxy.aiprreview.app;
import com.cxy.aiprreview.advisor.MyLoggerAdvisor;
import com.cxy.aiprreview.excption.BusinessException;
import com.cxy.aiprreview.excption.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

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

}

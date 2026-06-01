package com.cxy.aiprreview.app;
import org.springframework.ai.chat.client.ChatClient;
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

    public PrReviewApp(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
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
    public String reviewCode(ChatModel dashscopeChatModel,String codeDiff) {
        // 2. 使用 Spring AI 的 PromptTemplate 动态渲染参数
        PromptTemplate promptTemplate = new PromptTemplate(SYSTEM_PROMPT);
        Prompt prompt = promptTemplate.create(Map.of("diff", codeDiff));

        // 3. 调用大模型并获取文本结果
        ChatResponse chatResponse = chatClient.prompt()
                .system(SYSTEM_PROMPT )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        return content;
    }

}

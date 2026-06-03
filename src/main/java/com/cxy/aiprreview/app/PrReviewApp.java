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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class PrReviewApp {
    private final ChatClient chatClient;
    private final String prReviewPrompt;

    public PrReviewApp(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/pr-review-json-prompt.txt") Resource prReviewPromptResource) {
        this.prReviewPrompt = loadPrompt(prReviewPromptResource);
        this.chatClient = chatClientBuilder
                .defaultSystem(prReviewPrompt)
                .defaultAdvisors(
                        new MyLoggerAdvisor()
                )
                .build();
    }

    private String loadPrompt(Resource resource) {
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载 PR 审查提示词失败", e);
        }
    }

    /**
     * AI 审查代码。
     *
     * @param codeDiff 代码改动
     * @return AI 回复的建议
     */
    public String reviewCode(String codeDiff) {
        if (codeDiff == null || codeDiff.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码未改动");
        }

        PromptTemplate promptTemplate = new PromptTemplate(prReviewPrompt);
        Prompt prompt = promptTemplate.create(Map.of("diff", codeDiff));

        ChatResponse chatResponse = chatClient.prompt(prompt)
                .call()
                .chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "大模型返回结果为空");
        }
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * AI 审查代码，并将模型回复转换为结构化结果。
     *
     * @param codeDiff 代码改动
     * @return AI 回复的 JSON 格式建议
     */
    public ReviewReport getAiReview(String codeDiff) {
        var converter = new BeanOutputConverter<>(ReviewReport.class);

        PromptTemplate promptTemplate = new PromptTemplate(prReviewPrompt);
        Prompt prompt = promptTemplate.create(Map.of(
                "diff", codeDiff,
                "format", converter.getFormat()
        ));

        ChatResponse chatResponse = chatClient.prompt(prompt)
                .call()
                .chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "大模型返回结果为空");
        }
        String content = chatResponse.getResult().getOutput().getText();
        if (content == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "大模型返回结果为空");
        }
        return converter.convert(content);
    }
}

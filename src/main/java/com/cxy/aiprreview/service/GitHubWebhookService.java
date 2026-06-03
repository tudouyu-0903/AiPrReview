package com.cxy.aiprreview.service;

import com.cxy.aiprreview.dto.ReviewReport;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;


public interface GitHubWebhookService {
    /***
     * 审核GitHub Webhook
     * @param eventType
     * @param payload
     * @return
     */
    void handleGitHubWebhook(String eventType, Map<String, Object> payload);

    /***
     * 提交代码审查结果在github评论区
     * @param reviewReport
     */
    void submitCodeReview( String repoFullName,int prNumber,String latestCommitSha,ReviewReport reviewReport);
}

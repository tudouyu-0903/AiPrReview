package com.cxy.aiprreview.controller;

import com.cxy.aiprreview.anno.VerifyGitHubSignature;
import com.cxy.aiprreview.app.PrReviewApp;
import com.cxy.aiprreview.common.BaseResponse;
import com.cxy.aiprreview.common.ResultUtils;
import com.cxy.aiprreview.dto.ReviewReport;
import com.cxy.aiprreview.excption.ErrorCode;
import com.cxy.aiprreview.service.GitHubWebhookService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
@Slf4j
@RestController
@RequestMapping("/github")
public class GitHubWebhookController {

    @Resource
    private GitHubWebhookService gitHubWebhookService;

    /***
     * 审核GitHub pull_request提交的修改代码
     * @param eventType 事件类型 pull_request
     * @param payload 事件的全部详细数据（JSON 格式）
     * @return 执行结果
     */
    @PostMapping("/webhook")
    @VerifyGitHubSignature
    public ResponseEntity<BaseResponse<String>> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestBody Map<String, Object> payload) {

        // 1. 验证是否为 PR 事件
        if (!"pull_request".equals(eventType)) {
            return ResponseEntity.ok(ResultUtils.success("Webhook processed"));
        }
        // 2. 验证 PR 的动作 (opened: 新建PR, synchronize: 追随提交了新代码)
        String action = (String) payload.get("action");
        if (!"opened".equals(action) && !"synchronize".equals(action)) {
            return ResponseEntity.ok(ResultUtils.success("Ignore PR action: " + action));
        }
        //将修改后的代码提交给AI进行审核
        ReviewReport reviewReport = gitHubWebhookService.handleGitHubWebhook(eventType, payload);
        if(reviewReport == null){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).
                    body(new BaseResponse<>(ErrorCode.OPERATION_ERROR));
        }
        return ResponseEntity.ok(ResultUtils.success("Webhook processed"));

    }
}
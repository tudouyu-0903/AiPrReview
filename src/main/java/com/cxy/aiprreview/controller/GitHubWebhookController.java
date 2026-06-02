package com.cxy.aiprreview.controller;

import com.cxy.aiprreview.app.PrReviewApp;
import com.cxy.aiprreview.common.BaseResponse;
import com.cxy.aiprreview.common.ResultUtils;
import com.cxy.aiprreview.dto.ReviewReport;
import com.cxy.aiprreview.excption.ErrorCode;
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

    private final RestTemplate restTemplate = new RestTemplate();
    @Resource
    private PrReviewApp prReviewApp;
    @PostMapping("/webhook")
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

        try {
            // 3. 获取项目信息 (哪个项目)
            Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
            String repoFullName = (String) repository.get("full_name"); // 例如: "owner/repo-name"

            // 4. 获取 Pull Request 的核心数据
            Map<String, Object> pullRequest = (Map<String, Object>) payload.get("pull_request");
            int prNumber = (int) pullRequest.get("number"); // PR 编号
            String diffUrl = (String) pullRequest.get("diff_url"); // 包含具体改动了哪行代码的文本链接

            System.out.printf("【收到通知】项目 [%s] 触发了第 %d 号 PR\n", repoFullName, prNumber);
            System.out.println("正在通过 diff_url 获取代码具体改动行...");

            // 5. 请求 diff_url 拿到纯文本格式的 Diff 数据
            // 如果是私有仓库，需要在此处配置 GitHub Token 鉴权，公开仓库可直接请求
            String codeDiff = restTemplate.getForObject(diffUrl, String.class);

            // 6. 打印出具体的改动行数据
            System.out.println("====== 接收到的代码 Diff ======");
            System.out.println(codeDiff);
            System.out.println("=============================");

            // TODO: 此处直接将 codeDiff 丢给你的 Spring AI 服务进行审查
            ReviewReport aiReview = prReviewApp.getAiReview(codeDiff);

            return ResponseEntity.ok(ResultUtils.success("Webhook processed"));
        } catch (Exception e) {
            log.info("处理 GitHub Webhook 失败: " + e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST) // 让 GitHub 识别到报错（HTTP 400）
                    .body((BaseResponse<String>) ResultUtils.error(ErrorCode.OPERATION_ERROR,e.getMessage()));        }

    }
}
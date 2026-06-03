package com.cxy.aiprreview.service.impel;

import com.cxy.aiprreview.app.PrReviewApp;
import com.cxy.aiprreview.dto.ReviewReport;
import com.cxy.aiprreview.excption.BusinessException;
import com.cxy.aiprreview.excption.ErrorCode;
import com.cxy.aiprreview.service.GitHubWebhookService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
@Slf4j
@Service
public class GitHubWebhookServiceImple implements GitHubWebhookService {
    private final RestTemplate restTemplate = new RestTemplate();
    @Resource
    private PrReviewApp prReviewApp;

    @Async("aiReviewExecutor")
    @Override
    public void handleGitHubWebhook(String eventType, Map<String, Object> payload) {
        try {
            // 1. 获取项目信息 (哪个项目)
            Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
            String repoFullName = (String) repository.get("full_name"); // 例如: "owner/repo-name"

            // 2. 获取 Pull Request 的核心数据
            Map<String, Object> pullRequest = (Map<String, Object>) payload.get("pull_request");
            int prNumber = (int) pullRequest.get("number"); // PR 编号
            String diffUrl = (String) pullRequest.get("diff_url"); // 包含具体改动了哪行代码的文本链接
            Map<String, Object> head = (Map<String, Object>) pullRequest.get("head");
            String latestCommitSha = (String) head.get("sha");

            log.info("【异步任务】项目 [{}] 触发了第 {} 号 PR，正在下载 Diff...", repoFullName, prNumber);

            // 3. 请求 diff_url 拿到纯文本格式的 Diff 数据
            // 如果是私有仓库，需要在此处配置 GitHub Token 鉴权，公开仓库可直接请求
            String codeDiff = restTemplate.getForObject(diffUrl, String.class);
            // 4.AI 服务进行审查
            ReviewReport aiReview = prReviewApp.getAiReview(codeDiff);

            // 防御性判空
            if (aiReview == null || aiReview.getComments() == null || aiReview.getComments().isEmpty()) {
                log.info("【异步任务结束】大模型未检测到致命 Bug 或性能风险，无需在 GitHub 留白报告。");
                return;
            }
            // 5. 提交代码审查结果在github评论区
            submitCodeReview(repoFullName,prNumber,latestCommitSha,aiReview);
            log.info("【后台异步线程成功结束】项目 [{}] 的第 {} 号 PR 审查大报告已发布！", repoFullName, prNumber);
        } catch (Exception e) {
            log.info("处理 GitHub Webhook 失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,e.getMessage());
        }
    }

    /***
     * 调用 GitHub API 把评论写回去
     * @param reviewReport
     */
    @Value("${github.token}")
    private String githubToken;
    @Override
    public void submitCodeReview(String repoFullName,int prNumber,String latestCommitSha,ReviewReport reviewReport) {
        GHPullRequest pr = null;
        try {
            //1.创建 GitHub 客户端连接
            GitHub github = new GitHubBuilder()
                    .withOAuthToken(githubToken)
                    .build();
            //2.获取仓库对象
            GHRepository repo = github.getRepository(repoFullName);
            //3.获取指定的 Pull Request
            pr = repo.getPullRequest(prNumber);
        } catch (Exception e) {
            log.info("获取 GitHub 仓库失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,e.getMessage());
        }

        // 获取 PR 的最新 commit SHA 获取当前 PR 关联的最顶层 Commit (用于精确定位行号)

        // 4.. 循环遍历 AI 给出的 comments 数组
        if (reviewReport == null || reviewReport.getComments() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"没有AI给出的代码审查建议");
        }
        try {
            // 6. 组装整篇大报告
            StringBuilder reportMarkdown = new StringBuilder();
            reportMarkdown.append("# 🤖 AI 智能代码审查报告\n\n");
            reportMarkdown.append("期待与您一起提升代码质量！以下是本次审查发现的优化点：\n\n");
            for (int i = 0; i < reviewReport.getComments().size(); i++) {
                var item = reviewReport.getComments().get(i);
                reportMarkdown.append(String.format("### 📌 建议 %d: `%s` (第 %d 行)\n",
                        i + 1, item.getFilePath(), item.getLineNumber()));
                reportMarkdown.append("> **审查意见：** ").append(item.getSuggestion()).append("\n\n");

                if (item.getCodeSnippet() != null && !item.getCodeSnippet().isBlank()) {
                    reportMarkdown.append("💡 **建议修改为：**\n```java\n")
                            .append(item.getCodeSnippet()).append("\n```\n\n");
                }
                reportMarkdown.append("--\n");
            }
        // 7.直接评论到 PR 的主留言板（绝对不会报 422 错误）
            pr.comment(reportMarkdown.toString());
        } catch (Exception e) {
            log.info("提交代码审查结果失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,e.getMessage());
        }
    }
}

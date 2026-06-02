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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
@Slf4j
@Service
public class GitHubWebhookServiceImple implements GitHubWebhookService {
    private final RestTemplate restTemplate = new RestTemplate();
    @Resource
    private PrReviewApp prReviewApp;
    @Override
    public ReviewReport handleGitHubWebhook(String eventType, Map<String, Object> payload) {
        try {
            // 3. 获取项目信息 (哪个项目)
            Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
            String repoFullName = (String) repository.get("full_name"); // 例如: "owner/repo-name"

            // 4. 获取 Pull Request 的核心数据
            Map<String, Object> pullRequest = (Map<String, Object>) payload.get("pull_request");
            int prNumber = (int) pullRequest.get("number"); // PR 编号
            String diffUrl = (String) pullRequest.get("diff_url"); // 包含具体改动了哪行代码的文本链接
            Map<String, Object> head = (Map<String, Object>) pullRequest.get("head");
            String latestCommitSha = (String) head.get("sha");

            System.out.printf("【收到通知】项目 [%s] 触发了第 %d 号 PR\n", repoFullName, prNumber);
            System.out.println("正在通过 diff_url 获取代码具体改动行...");

            // 5. 请求 diff_url 拿到纯文本格式的 Diff 数据
            // 如果是私有仓库，需要在此处配置 GitHub Token 鉴权，公开仓库可直接请求
            String codeDiff = restTemplate.getForObject(diffUrl, String.class);

            // 6. 打印出具体的改动行数据
            System.out.println("====== 接收到的代码 Diff ======");
            System.out.println(codeDiff);
            System.out.println("=============================");

            // 此处直接将 codeDiff 丢给你的 Spring AI 服务进行审查
            ReviewReport aiReview = prReviewApp.getAiReview(codeDiff);
            // 7. 提交代码审查结果在github评论区
            submitCodeReview(repoFullName,prNumber,latestCommitSha,aiReview);
            return aiReview;
        } catch (Exception e) {
            log.info("处理 GitHub Webhook 失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,e.getMessage());
        }
    }

    /***
     * 调用 GitHub API 把评论写回去
     * @param reviewReport
     */
    @Override
    public void submitCodeReview(String repoFullName,int prNumber,String latestCommitSha,ReviewReport reviewReport) {
        // 2. 🔥 闭环核心：调用 GitHub API 把评论写回去
        // 注意：正式生产推荐用 GitHub App，测试阶段可以用你个人的 PAT Token
        String myGitHubToken = "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"; // 换成你自己的 GitHub Token
        GHPullRequest pr = null;
        try {
            //创建 GitHub 客户端连接
            GitHub github = new GitHubBuilder()
                    .withOAuthToken(myGitHubToken)
                    .build();
            // 获取仓库对象
            GHRepository repo = github.getRepository(repoFullName);
            // 获取指定的 Pull Request
            pr = repo.getPullRequest(prNumber);
        } catch (Exception e) {
            log.info("获取 GitHub 仓库失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,e.getMessage());
        }

        // 获取 PR 的最新 commit SHA
        // 🔥 关键：获取当前 PR 关联的最顶层 Commit (用于精确定位行号)

        // 3. 循环遍历 AI 给出的 comments 数组
        if (reviewReport == null && reviewReport.getComments() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"没有AI给出的代码审查建议");
        }
        try {
            for (com.cxy.aiprreview.dto.ReviewCommentItem item : reviewReport.getComments()) {

                // 跳过定位失败或未知的行
                if (item.getLineNumber() <= 0 || "unknown".equals(item.getFilePath())) {
                    continue;
                }

                // 组装要在 GitHub 显示的漂亮 Markdown 格式
                StringBuilder bodyBuilder = new StringBuilder();
                bodyBuilder.append("### 🤖 AI 智能审查意见\n");
                bodyBuilder.append(item.getSuggestion()).append("\n\n");

                if (item.getCodeSnippet() != null && !item.getCodeSnippet().isBlank()) {
                    bodyBuilder.append("💡 **优化建议：**\n")
                            .append("```suggestion\n") // 采用 GitHub 专属魔法卡片语法
                            .append(item.getCodeSnippet()).append("\n")
                            .append("```");
                }

                // 发送给 GitHub，展示在对应文件的对应行
                pr.createReviewComment(
                        bodyBuilder.toString(),
                        latestCommitSha,
                        item.getFilePath(),
                        item.getLineNumber()
                );
            }
        } catch (Exception e) {
            log.info("提交代码审查结果失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,e.getMessage());
        }
    }
}

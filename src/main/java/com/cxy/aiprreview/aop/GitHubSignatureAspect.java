package com.cxy.aiprreview.aop;

import com.cxy.aiprreview.excption.BusinessException;
import com.cxy.aiprreview.excption.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Aspect
@Component
public class GitHubSignatureAspect {

    // 从 application.yml 中注入你设置的暗号密钥
    @Value("${github.webhook-secret}")
    private String webhookSecret;

    /**
     * 定义前置通知：只要方法上打上了 @VerifyGitHubSignature 注解，就会触发该方法
     */
    @Before("@annotation(com.cxy.aiprreview.anno.VerifyGitHubSignature)")
    public void verifySignature() throws Exception {
        // 1. 通过 Spring 的上下文工具抓取到当前的 HttpServletRequest 对象
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();

        // 2. 提取 GitHub 专属的加密签名头
        String gitHubSignature = request.getHeader("X-Hub-Signature-256");
        if (gitHubSignature == null || !gitHubSignature.startsWith("sha256=")) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "【AOP拦截】缺失 X-Hub-Signature-256 签名头");
        }

        // 3. 提取被过滤器缓存的 Body 原始字节码
        if (!(request instanceof ContentCachingRequestWrapper)) {
            return;
        }
        ContentCachingRequestWrapper wrappedRequest = (ContentCachingRequestWrapper) request;
        byte[] bodyBytes = wrappedRequest.getContentAsByteArray();
        String requestBody = new String(bodyBytes, StandardCharsets.UTF_8);

        // 4. 执行 HmacSHA256 算法加密
        String computedSignature = "sha256=" + hmacSha256(requestBody, webhookSecret);

        // 5. 严格比对：如果不一致，直接抛异常，彻底阻断 Controller 的执行
        if (!computedSignature.equals(gitHubSignature)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "【AOP拦截】安全校验失败，拒绝访问！");
        }
    }

    // 标准加密算法
    private String hmacSha256(String data, String secret) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder sb = new StringBuilder();
        for (byte b : rawHmac) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
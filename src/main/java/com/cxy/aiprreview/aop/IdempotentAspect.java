package com.cxy.aiprreview.aop;

import com.cxy.aiprreview.anno.Idempotent;
import com.cxy.aiprreview.excption.BusinessException;
import com.cxy.aiprreview.excption.ErrorCode;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Map;

@Aspect
@Component
@Slf4j
public class IdempotentAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(idempotent)")
    public Object GlendaleIdempotent(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 1. 获取当前请求的 HttpServletRequest 对象
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();

        // 2. 从 Header 中提取 GitHub 唯一的 Delivery ID
        String deliveryId = request.getHeader("X-GitHub-Delivery");
        if (deliveryId == null || deliveryId.isBlank()) {
            log.warn("[幂等检测] 无法获取 X-GitHub-Delivery 请求头，跳过幂等检查。");
            return joinPoint.proceed();
        }

        // 3. 从方法参数中解析出 Payload 体，进而提取业务去重标识
        String repo = "unknown";
        int prNumber = 0;
        String commitSha = "unknown";
        ///遍历方法参数
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            //判断参数是否是 Map 类型（GitHub Webhook 的 JSON 数据会被解析成 Map）,找到Payload 体
            if (arg instanceof Map) {
                Map<String, Object> payload = (Map<String, Object>) arg;
                try {
                    Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
                    if (repository != null) {
                        repo = ((String) repository.get("full_name")).replace("/", ":"); // 替换斜杠防 Redis 树形目录混乱
                    }
                    Map<String, Object> pullRequest = (Map<String, Object>) payload.get("pull_request");
                    if (pullRequest != null) {
                        prNumber = (int) pullRequest.get("number");
                        Map<String, Object> head = (Map<String, Object>) pullRequest.get("head");
                        if (head != null) {
                            commitSha = (String) head.get("sha");
                        }
                    }
                } catch (Exception e) {
                    log.error("解析 Webhook Payload 提取业务 Key 失败: ", e);
                }
                break; // 找到 payload map 后直接退出循环
            }
        }

        // 4. 🔥 核心：构建独一无二的分布式组合锁 Key
        // 格式例如：idempotent:github:deliveryId:repo-name:pr_2:sha_7cbf2f...
        String lockKey = String.format("%s%s:%s:pr_%d:sha_%s", 
                idempotent.prefix(), deliveryId, repo, prNumber, commitSha);
        // 5. 运用 Redis 原子的 SETNX (setIfAbsent) 抢占锁
        // 如果当前 Key 在 Redis 中不存在，写入成功并返回 true；如果已存在，代表重复投递，返回 false。
        Boolean isAbsent = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "processing", 
                idempotent.expireTime(), idempotent.timeUnit());
        if (Boolean.FALSE.equals(isAbsent)) {
            log.warn("检测到重复的 GitHub 投递请求！Key: {}", lockKey);
            // 既然是重复请求，直接对 GitHub 抛出自定义异常或静默返回 200，此处建议直接拦截拦截
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Duplicate webhook request, processing bypassed.");
        }
        log.info("[幂等锁抢占成功] 该请求为首次合法投递，已上锁。Key: {}", lockKey);
        // 6. 锁成功，放行让 Controller 的主流程继续往下走
        return joinPoint.proceed();
    }
}
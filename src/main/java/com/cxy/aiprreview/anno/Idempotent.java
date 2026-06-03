package com.cxy.aiprreview.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /**
     * 幂等 Key 的前缀
     */
    String prefix() default "idempotent:github:";

    /**
     * 锁的过期时间（防刷间隔），默认 5 分钟
     * 5 分钟足够大模型完成一次完整的后台评审与回传了
     */
    long expireTime() default 5;

    /**
     * 时间单位，默认分钟
     */
    TimeUnit timeUnit() default TimeUnit.MINUTES;
}
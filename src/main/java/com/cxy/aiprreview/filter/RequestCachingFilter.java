package com.cxy.aiprreview.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import java.io.IOException;

/***
 * 将HttpServletRequest 包装为 ContentCachingRequestWrapper (内部带有一个字节数组缓存)
 */
@Component
public class RequestCachingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            // 将请求包装为可重复读取的 Wrapper
            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper((HttpServletRequest) request);
            chain.doFilter(wrappedRequest, response);
        } else {
            // 对于非HTTP请求，直接传递原始请求
            chain.doFilter(request, response);
        }
    }
}
package com.cxy.aiprreview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Objects;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCommentItem {
    private String filePath;       // 文件路径，对应 GitHub 的 path
    private int lineNumber;        // 行号，对应 GitHub 的 line
    private String suggestion;     // 审查意见（支持Markdown）
    private String codeSnippet;    // 优化后的代码片段（可选）


    // 重写 toString，方便排查日志
    @Override
    public String toString() {
        return "ReviewCommentItem{" +
                "filePath='" + filePath + '\'' +
                ", lineNumber=" + lineNumber +
                ", suggestion='" + suggestion + '\'' +
                ", codeSnippet='" + codeSnippet + '\'' +
                '}';
    }
}
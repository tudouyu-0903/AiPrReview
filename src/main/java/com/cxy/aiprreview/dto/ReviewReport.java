package com.cxy.aiprreview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReport {
    private List<ReviewCommentItem> comments;

    @Override
    public String toString() {
        return "ReviewReport{" +
                "comments=" + comments +
                '}';
    }
}
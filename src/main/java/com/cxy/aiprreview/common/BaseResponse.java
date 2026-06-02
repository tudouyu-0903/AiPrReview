package com.cxy.aiprreview.common;

import com.cxy.aiprreview.excption.ErrorCode;
import lombok.Data;

import java.io.Serializable;

@Data
/***
 * 全局响应封装类
 */
public class BaseResponse<T> implements Serializable {
    //    返回前端正常/异常码
    private int code;
    //    每个接口返回的数据
    private T data;
    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode code) {
        this(code.getCode(), null, code.getMessage());
    }


}

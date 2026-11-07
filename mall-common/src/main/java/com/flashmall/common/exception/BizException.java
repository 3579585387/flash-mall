package com.flashmall.common.exception;

import com.flashmall.common.result.Result;
import lombok.Getter;

/**
 * 业务异常：由全局异常处理器统一翻译成 Result.fail(...)
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = Result.FAIL;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}

package com.flashmall.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录返回
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 令牌，后续请求放在请求头 Authorization 中 */
    private String token;

    private Long userId;

    private String nickName;

    /** 1 C 端 2 B 端 */
    private Integer type;
}

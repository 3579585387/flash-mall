package com.flashmall.common.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    /** 用户 id（C 端是 user.id，B 端是 employee.id） */
    private Long id;

    /** 用户类型：1 C 端，2 B 端 */
    private Integer type;
}

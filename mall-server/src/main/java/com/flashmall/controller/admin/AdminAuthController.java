package com.flashmall.controller.admin;

import com.flashmall.common.result.Result;
import com.flashmall.pojo.dto.AdminLoginDTO;
import com.flashmall.pojo.vo.LoginVO;
import com.flashmall.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * B 端登录
 * <p>
 * 默认账号：admin / 123456（种子数据里写的是 MD5）
 * 这个接口在拦截器白名单里，其余 /api/admin/** 都必须带商家 token。
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid AdminLoginDTO form) {
        return Result.ok(authService.loginAdmin(form));
    }
}

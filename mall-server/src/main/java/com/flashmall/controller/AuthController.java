package com.flashmall.controller;

import com.flashmall.common.result.Result;
import com.flashmall.pojo.dto.LoginFormDTO;
import com.flashmall.pojo.vo.LoginVO;
import com.flashmall.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端登录
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 发送验证码
     * <p>
     * 演示项目直接把验证码返回，省去接短信服务商；真实项目只返回 true。
     */
    @PostMapping("/send-code")
    public Result<String> sendCode(@RequestParam String phone) {
        return Result.ok(authService.sendCode(phone));
    }

    /**
     * 登录（用户不存在会自动注册）
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginFormDTO form) {
        return Result.ok(authService.login(form));
    }
}

package com.flashmall.controller.admin;

import com.flashmall.common.result.Result;
import com.flashmall.pojo.vo.DashboardVO;
import com.flashmall.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * B 端运营看板
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public Result<DashboardVO> dashboard() {
        return Result.ok(dashboardService.dashboard());
    }
}

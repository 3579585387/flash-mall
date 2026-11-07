package com.flashmall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashmall.common.constant.MessageConstant;
import com.flashmall.common.constant.RedisConstants;
import com.flashmall.common.exception.BizException;
import com.flashmall.mapper.EmployeeMapper;
import com.flashmall.mapper.UserMapper;
import com.flashmall.pojo.dto.AdminLoginDTO;
import com.flashmall.pojo.dto.LoginFormDTO;
import com.flashmall.pojo.entity.Employee;
import com.flashmall.pojo.entity.User;
import com.flashmall.pojo.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 登录鉴权
 * <p>
 * C 端：手机号 + 验证码（验证码存 Redis，一次性使用）
 * B 端：账号 + 密码（MD5，生产应换 BCrypt）
 * 两端共用一套 token 机制，用 type 字段区分，配合两个拦截器做权限隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final StringRedisTemplate redis;
    private final UserMapper userMapper;
    private final EmployeeMapper employeeMapper;

    /**
     * 发送验证码
     * <p>
     * 演示项目直接把验证码返回给前端（真实环境要接短信服务商，不能返回）。
     */
    public String sendCode(String phone) {
        if (!StringUtils.hasText(phone) || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BizException(MessageConstant.PHONE_ILLEGAL);
        }
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        redis.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                Duration.ofMinutes(RedisConstants.LOGIN_CODE_TTL_MINUTES));
        log.info("[演示] 手机号 {} 的验证码是 {}", phone, code);
        return code;
    }

    /**
     * C 端登录：手机号 + 验证码，用户不存在则自动注册
     */
    public LoginVO login(LoginFormDTO form) {
        String key = RedisConstants.LOGIN_CODE_KEY + form.getPhone();
        String cachedCode = redis.opsForValue().get(key);
        if (cachedCode == null || !cachedCode.equals(form.getCode())) {
            throw new BizException(MessageConstant.CODE_ERROR);
        }
        // 验证码一次性使用，校验通过立刻删除，防止重放
        redis.delete(key);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, form.getPhone()));
        if (user == null) {
            user = createUser(form.getPhone());
        }
        return createToken(user.getId(), user.getNickName(), RedisConstants.USER_TYPE_USER);
    }

    /**
     * B 端登录
     */
    public LoginVO loginAdmin(AdminLoginDTO form) {
        Employee employee = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getUsername, form.getUsername()));
        if (employee == null || !md5(form.getPassword()).equals(employee.getPassword())) {
            throw new BizException(MessageConstant.ACCOUNT_ERROR);
        }
        if (employee.getStatus() == null || employee.getStatus() != 1) {
            throw new BizException(MessageConstant.ACCOUNT_LOCKED);
        }
        return createToken(employee.getId(), employee.getName(), RedisConstants.USER_TYPE_ADMIN);
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("用户" + phone.substring(phone.length() - 4));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    /**
     * 生成 token 并写入 Redis Hash
     * <p>
     * 用 Hash 而不是 String：后续可以在同一个 key 上扩展字段（比如权限列表、登录设备）。
     */
    private LoginVO createToken(Long id, String nickName, int type) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = RedisConstants.LOGIN_TOKEN_KEY + token;

        Map<String, String> map = new HashMap<>(4);
        map.put("id", String.valueOf(id));
        map.put("nickName", nickName == null ? "" : nickName);
        map.put("type", String.valueOf(type));

        redis.opsForHash().putAll(key, map);
        redis.expire(key, Duration.ofMinutes(RedisConstants.LOGIN_TOKEN_TTL_MINUTES));

        return new LoginVO(token, id, nickName, type);
    }

    private String md5(String raw) {
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }
}

package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.runtime.model.User;
import com.thirdexploration.promengine.runtime.repository.UserRepository;
import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import com.thirdexploration.promengine.web.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return ApiResponse.error("用户名和密码不能为空");
        }
        User user = userRepository.findByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return ApiResponse.error("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return ApiResponse.ok(Map.of(
            "token", token,
            "userId", user.getId(),
            "username", user.getUsername(),
            "nickname", user.getNickname()
        ));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, String>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String nickname = body.getOrDefault("nickname", username);
        if (username == null || password == null) {
            return ApiResponse.error("用户名和密码不能为空");
        }
        if (userRepository.findByUsername(username) != null) {
            return ApiResponse.error("用户名已存在");
        }
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .password(passwordEncoder.encode(password))
                .nickname(nickname)
                .enabled(true)
                .createdAt(System.currentTimeMillis())
                .build();
        userRepository.save(user);
        return ApiResponse.ok(Map.of("message", "注册成功"));
    }
}
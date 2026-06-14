package com.docmgr.service;

import com.docmgr.entity.User;
import com.docmgr.repository.UserRepository;
import com.docmgr.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    public Map<String, Object> register(String username, String password, String email) {
        if (userRepo.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User(username, encoder.encode(password), email);
        user = userRepo.save(user);
        String token = jwtUtil.generateToken(user.getId(), username, user.getRole());
        return Map.of("message", "注册成功", "token", token,
                "user", Map.of("id", user.getId(), "username", username, "email", email != null ? email : "", "role", user.getRole()));
    }

    public Map<String, Object> login(String username, String password) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));
        if (!encoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), username, user.getRole());
        return Map.of("message", "登录成功", "token", token,
                "user", Map.of("id", user.getId(), "username", username,
                        "email", user.getEmail() != null ? user.getEmail() : "",
                        "role", user.getRole(), "avatar", user.getAvatar() != null ? user.getAvatar() : ""));
    }

    public User getUserInfo(Long userId) {
        return userRepo.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
    }
}

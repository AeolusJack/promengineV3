package com.thirdexploration.promengine.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

@Component
public class JwtHeaderFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtHeaderFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        // 公开接口直接放行
        if (path.startsWith("/api/auth/") || path.equals("/api/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = null;
        // 1. 尝试从 Authorization 头获取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        // 2. 对于 WebSocket 连接，从请求参数获取 token
        if (token == null && path.startsWith("/ws/")) {
            token = request.getParameter("token");
        }

        String userId = null;
        if (token != null && jwtUtil.validateToken(token)) {
            userId = jwtUtil.getUserIdFromToken(token);
        }

        // 如果解析成功，包装请求并注入 X-User-Id 头
        if (userId != null) {
            String finalUserId = userId;
            HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(request) {
                @Override
                public String getHeader(String name) {
                    if ("X-User-Id".equalsIgnoreCase(name)) {
                        return finalUserId;
                    }
                    return super.getHeader(name);
                }

                @Override
                public Enumeration<String> getHeaders(String name) {
                    if ("X-User-Id".equalsIgnoreCase(name)) {
                        return Collections.enumeration(Collections.singletonList(finalUserId));
                    }
                    return super.getHeaders(name);
                }
            };
            filterChain.doFilter(wrappedRequest, response);
        } else {
            // 未认证的 HTTP 请求返回 401，WebSocket 连接直接拒绝（或放行由后续处理）
            if (!path.startsWith("/ws/")) {
                response.setStatus(401);
                response.getWriter().write("{\"error\":\"未登录或Token已过期\"}");
                return;
            }
            filterChain.doFilter(request, response); // 未携带 token 的 WebSocket 连接，放行但无 userId
        }
    }
}
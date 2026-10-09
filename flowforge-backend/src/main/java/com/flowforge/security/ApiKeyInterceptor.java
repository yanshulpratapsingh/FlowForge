package com.flowforge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import java.util.Optional;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    @Value("${flowforge.security.api-key:}")
    private String expectedApiKey;

    private final ObjectMapper objectMapper;

    public ApiKeyInterceptor() {
        this(Optional.empty());
    }

    public ApiKeyInterceptor(Optional<ObjectMapper> objectMapper) {
        this.objectMapper = (objectMapper != null && objectMapper.isPresent())
                ? objectMapper.get()
                : new ObjectMapper()
                        .findAndRegisterModules()
                        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (expectedApiKey == null || expectedApiKey.trim().isEmpty()) {
            return true;
        }

        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey != null && MessageDigest.isEqual(
                expectedApiKey.getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse errorResponse = new ErrorResponse(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                "Missing or invalid API key"
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        return false;
    }
}

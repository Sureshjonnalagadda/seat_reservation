package com.paytm.reservation.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class RequestIdResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    public RequestIdResponseBodyAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body == null || !(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }
        String path = servletRequest.getServletRequest().getRequestURI();
        if (path.startsWith("/actuator")) {
            return body;
        }
        String requestId = RequestIdSupport.resolve(servletRequest.getServletRequest());
        if (requestId == null || requestId.isBlank()) {
            return body;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.convertValue(body, Map.class);
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("request_id", requestId);
        payload.forEach((key, value) -> {
            if (!"request_id".equals(key)) {
                ordered.put(key, value);
            }
        });
        return ordered;
    }
}

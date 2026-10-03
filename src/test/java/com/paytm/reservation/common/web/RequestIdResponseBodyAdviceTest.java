package com.paytm.reservation.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.reservation.auth.dto.RegisterResponse;
import com.paytm.reservation.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdResponseBodyAdviceTest {

    private final RequestIdResponseBodyAdvice advice = new RequestIdResponseBodyAdvice(new ObjectMapper());

    @Test
    void addsRequestIdToSuccessBody() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, "req-success-1");
        ServletServerHttpRequest serverRequest = new ServletServerHttpRequest(servletRequest);

        RegisterResponse body = new RegisterResponse(1L, "alice", Role.USER);
        MethodParameter returnType = new MethodParameter(
                RequestIdResponseBodyAdviceTest.class.getDeclaredMethod("sampleEndpoint"),
                -1
        );

        Object result = advice.beforeBodyWrite(
                body,
                returnType,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                serverRequest,
                null
        );

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("request_id")).isEqualTo("req-success-1");
        assertThat(map.get("username")).isEqualTo("alice");
        assertThat(map.keySet().iterator().next()).isEqualTo("request_id");
    }

    @SuppressWarnings("unused")
    private RegisterResponse sampleEndpoint() {
        return null;
    }
}

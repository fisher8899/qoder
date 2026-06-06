package com.ccerphr.assessment.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldIncludeProvidedRequestIdInUnhandledErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        request.addHeader("X-Request-Id", "req-123");

        Result<Void> result = handler.handleException(new RuntimeException("boom"), request);

        assertEquals(500, result.getCode());
        assertTrue(result.getMessage().contains("req-123"));
    }

    @Test
    void shouldGenerateRequestIdWhenHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");

        Result<Void> result = handler.handleException(new RuntimeException("boom"), request);
        Object generatedRequestId = request.getAttribute("requestId");

        assertEquals(500, result.getCode());
        assertNotNull(generatedRequestId);
        assertTrue(generatedRequestId instanceof String);
        assertTrue(UUID_PATTERN.matcher((String) generatedRequestId).matches());
        assertTrue(result.getMessage().contains((String) generatedRequestId));
    }
}

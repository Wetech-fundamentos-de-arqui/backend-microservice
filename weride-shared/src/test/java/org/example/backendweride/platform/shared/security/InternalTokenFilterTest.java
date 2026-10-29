package org.example.backendweride.platform.shared.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalTokenFilterTest {
    private final InternalTokenFilter filter = new InternalTokenFilter(Map.of("booking", "booking-secret"));
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(String path, String caller, String token) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        if (caller != null) request.addHeader("X-Service-Name", caller);
        if (token != null) request.addHeader("X-Internal-Token", token);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void withoutHeadersIsUnauthorized() throws Exception {
        assertEquals(401, call("/internal/v1/rides/1", null, null).getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void unknownCallerIsUnauthorized() throws Exception {
        assertEquals(401, call("/internal/v1/rides/1", "ghost", "booking-secret").getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void wrongSecretIsUnauthorized() throws Exception {
        assertEquals(401, call("/internal/v1/rides/1", "booking", "nope").getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void validCallerAndSecretPasses() throws Exception {
        call("/internal/v1/rides/1", "booking", "booking-secret");
        verify(chain).doFilter(any(), any());
        assertEquals("service:booking", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void otherPathsAreNotChecked() throws Exception {
        call("/api/v1/plans", null, null);
        verify(chain).doFilter(any(), any());
    }
}

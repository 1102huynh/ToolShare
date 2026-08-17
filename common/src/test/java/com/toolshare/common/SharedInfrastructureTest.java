package com.toolshare.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toolshare.common.api.ApiErrorResponse;
import com.toolshare.common.exception.ConflictException;
import com.toolshare.common.exception.ForbiddenException;
import com.toolshare.common.exception.MalformedRequestException;
import com.toolshare.common.exception.ResourceNotFoundException;
import com.toolshare.common.exception.ToolShareExceptionHandler;
import com.toolshare.common.exception.UnauthorizedException;
import com.toolshare.common.trace.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SharedInfrastructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ToolShareExceptionHandler())
                .addFilter(new TraceIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void validationErrorResponseHasExpectedShape() throws Exception {
        MockMvc mockMvc = mockMvc();

        // T-012 note: asserts membership, not fieldErrors[0], because Hibernate
        // Validator returns constraint violations as a Set — its iteration order is
        // not guaranteed and was observed to shift with unrelated classpath changes
        // elsewhere in this module (e.g. adding another @ExceptionHandler method),
        // which made a fixed-index assertion here flaky independent of correctness.
        mockMvc.perform(post("/api/v1/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad-email\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[*].field", org.hamcrest.Matchers.hasItem("email")));
    }

    @Test
    void unexpectedExceptionIsMappedToInternalServerError() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(get("/api/v1/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void resourceNotFoundAndConflictAreMapped() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(get("/api/v1/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void unauthorizedAndForbiddenAreMapped() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(get("/api/v1/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void malformedRequestIsMapped() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(post("/api/v1/test/malformed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void incomingTraceIdIsPropagatedAndGeneratedWhenMissing() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/trace");
        request.addHeader("X-Trace-Id", "incoming-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            HttpServletRequest httpReq = (HttpServletRequest) req;
            HttpServletResponse httpRes = (HttpServletResponse) res;
            assertEquals("incoming-trace", httpReq.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE));
            assertEquals("incoming-trace", httpRes.getHeader("X-Trace-Id"));
        });

        MockHttpServletRequest generatedRequest = new MockHttpServletRequest("GET", "/api/v1/test/trace");
        MockHttpServletResponse generatedResponse = new MockHttpServletResponse();

        filter.doFilter(generatedRequest, generatedResponse, (req, res) -> {
            HttpServletRequest httpReq = (HttpServletRequest) req;
            HttpServletResponse httpRes = (HttpServletResponse) res;
            String traceId = (String) httpReq.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
            assertNotNull(traceId);
            assertFalse(traceId.isBlank());
            assertEquals(traceId, httpRes.getHeader("X-Trace-Id"));
        });
    }

    @Test
    void apiErrorResponseIsExternalizedAsJson() throws Exception {
        ApiErrorResponse response = new ApiErrorResponse(
                "VALIDATION_ERROR",
                "Request validation failed",
                "abc-123",
                java.time.OffsetDateTime.now(),
                java.util.List.of(new com.toolshare.common.api.FieldErrorDetail("email", "Invalid email"))
        );

        String json = objectMapper.writeValueAsString(response);
        assertTrue(json.contains("\"code\":\"VALIDATION_ERROR\""));
        assertTrue(json.contains("\"traceId\":\"abc-123\""));
        assertTrue(json.contains("\"field\":\"email\""));
    }

    @RestController
    @RequestMapping("/api/v1")
    @Validated
    static class TestController {

        @PostMapping("/test/validate")
        public void validate(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/unexpected")
        public void unexpected() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/test/not-found")
        public void notFound() {
            throw new ResourceNotFoundException("Test resource not found");
        }

        @GetMapping("/test/conflict")
        public void conflict() {
            throw new ConflictException("Test conflict");
        }

        @GetMapping("/test/unauthorized")
        public void unauthorized() {
            throw new UnauthorizedException("No token");
        }

        @GetMapping("/test/forbidden")
        public void forbidden() {
            throw new ForbiddenException("Forbidden");
        }

        @PostMapping("/test/malformed")
        public void malformed(@RequestBody String payload) {
            throw new MalformedRequestException("Malformed JSON");
        }
    }

    static class TestRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @Email(message = "Invalid email")
        private String email;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}

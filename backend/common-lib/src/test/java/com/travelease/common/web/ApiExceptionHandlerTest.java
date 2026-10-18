package com.travelease.common.web;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.TooManyRequestsException;

/** #15: representative error cases through a real Spring MVC stack and the shared handler. */
class ApiExceptionHandlerTest {

    record Payload(String name) {
    }

    @RestController
    static class ProbeController {

        @PostMapping("/json")
        String json(@RequestBody Payload payload) {
            return payload.name();
        }

        @GetMapping("/typed/{id}")
        String typed(@PathVariable Long id) {
            return String.valueOf(id);
        }

        @GetMapping("/opaque/{id}")
        String opaque(@PathVariable String id) {
            return String.valueOf(Long.parseLong(id));
        }

        @GetMapping("/param")
        String param(@RequestParam String status) {
            return status;
        }

        @GetMapping("/missing")
        String missing() {
            throw new ResourceNotFoundException("TravelRequest", 9L);
        }

        @GetMapping("/policy")
        String policy() {
            throw new PolicyViolationException("documentIds", "A receipt is required");
        }

        @GetMapping("/stale")
        String stale() {
            throw new ObjectOptimisticLockingFailureException(Payload.class, 1L);
        }

        @GetMapping("/duplicate-insert")
        String duplicateInsert() {
            throw new DataIntegrityViolationException("Duplicate entry 'x' for key 'uk_secret_column'");
        }

        @GetMapping("/rate-limited")
        String rateLimited() {
            throw new TooManyRequestsException("Too many attempts");
        }

        @GetMapping("/status")
        String responseStatus() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("db password=hunter2 at com.example.Secret");
        }
    }

    @RestControllerAdvice
    static class Handler extends ApiExceptionHandler {
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController()).setControllerAdvice(new Handler()).build();
    }

    @Test
    @DisplayName("malformed JSON is a 400 VALIDATION_ERROR, not a 500")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/json").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("malformed identifiers (typed and opaque string ids) are 400s")
    void malformedIdentifiers() throws Exception {
        mockMvc.perform(get("/typed/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
        mockMvc.perform(get("/opaque/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a missing required parameter is a 400 naming the parameter")
    void missingParameter() throws Exception {
        mockMvc.perform(get("/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
    }

    @Test
    @DisplayName("unsupported method is 405; missing resources are 404")
    void methodAndNotFound() throws Exception {
        mockMvc.perform(post("/missing")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Notification not found"));
    }

    @Test
    @DisplayName("stale versions and constraint conflicts are 409 CONFLICT without leaking SQL")
    void conflicts() throws Exception {
        mockMvc.perform(get("/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
        mockMvc.perform(get("/duplicate-insert"))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("uk_secret_column"))));
    }

    @Test
    @DisplayName("business rules stay 422, rate limits 429")
    void policyAndRateLimit() throws Exception {
        mockMvc.perform(get("/policy"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("POLICY_VIOLATION"));
        mockMvc.perform(get("/rate-limited")).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("unexpected failures are a 500 whose body exposes no exception details")
    void unexpectedFailure() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("com.example"))));
    }
}

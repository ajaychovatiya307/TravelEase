package com.travelease.expense.client;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.travelease.common.exception.PolicyViolationException;
import com.travelease.expense.client.dto.TravelRequestResponse;

/** #10: new expense claims (and submissions) are refused for cancelled trips. */
class TravelRequestClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final TravelRequestClient client = new TravelRequestClient(restTemplate, "http://travel/api");

    private void travelRequestWithStatus(String status) {
        TravelRequestResponse response = new TravelRequestResponse("7", "1", "Pune", LocalDate.now(),
                LocalDate.now().plusDays(1), "x", status, "2", null, null, List.of(), Instant.now(), Instant.now());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(TravelRequestResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
    }

    @Test
    void cancelledTripIsRefusedWithAClearMessage() {
        travelRequestWithStatus("CANCELLED");

        assertThatThrownBy(() -> client.validateTravelRequestApprovedAndOwned("7", "1"))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("cancelled travel request");
    }

    @Test
    void approvedOwnedTripIsAccepted() {
        travelRequestWithStatus("APPROVED");

        assertThatCode(() -> client.validateTravelRequestApprovedAndOwned("7", "1")).doesNotThrowAnyException();
    }
}

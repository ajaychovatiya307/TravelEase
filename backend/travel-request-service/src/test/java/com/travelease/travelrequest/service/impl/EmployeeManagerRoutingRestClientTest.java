package com.travelease.travelrequest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.travelease.common.exception.PolicyViolationException;

/** #11: admin-service answers are translated into clear business errors (real HTTP client, mocked server). */
class EmployeeManagerRoutingRestClientTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    private final EmployeeManagerRoutingRestClient client =
            new EmployeeManagerRoutingRestClient(restTemplate, "http://admin");

    @Test
    void inactiveApproverBecomesAPolicyViolationWithAdminsExplanation() {
        server.expect(requestTo("http://admin/internal/employees/1/approver"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"POLICY_VIOLATION\",\"message\":\"x\",\"fieldErrors\":[{\"field\":\"approver\","
                                + "\"message\":\"Your assigned approver is no longer active.\"}]}"));

        assertThatThrownBy(() -> client.resolveApprover(1L))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("no longer active");
    }

    @Test
    void eligibilityIsReportedWithItsReason() {
        server.expect(requestTo("http://admin/internal/employees/3/approver-eligibility"))
                .andRespond(withSuccess("{\"userId\":3,\"eligible\":true,\"reason\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://admin/internal/employees/8/approver-eligibility"))
                .andRespond(withSuccess("{\"userId\":8,\"eligible\":false,\"reason\":\"User is inactive\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.approverIneligibilityReason(3L)).isNull();
        assertThat(client.approverIneligibilityReason(8L)).isEqualTo("User is inactive");
    }
}

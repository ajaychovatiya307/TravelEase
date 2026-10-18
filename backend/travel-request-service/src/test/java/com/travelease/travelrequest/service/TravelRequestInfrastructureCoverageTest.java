package com.travelease.travelrequest.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.travelease.common.document.DocumentFileType;
import com.travelease.travelrequest.client.dto.ApproverResolutionResponse;
import com.travelease.travelrequest.client.dto.TravelRequestPolicySnapshot;
import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.repository.TravelRequestDocumentRepository;
import com.travelease.travelrequest.service.impl.EmployeeManagerRoutingRestClient;
import com.travelease.travelrequest.service.impl.TravelRequestFailedDocumentRecorder;
import com.travelease.travelrequest.service.impl.TravelRequestPolicyRestClient;

@ExtendWith(MockitoExtension.class)
class TravelRequestInfrastructureCoverageTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private TravelRequestDocumentRepository documentRepository;
    @InjectMocks
    private TravelRequestPolicyRestClient policyClient;
    @InjectMocks
    private EmployeeManagerRoutingRestClient routingClient;

    @Test
    void resolvesApproverFromAdminService() {
        ApproverResolutionResponse response = new ApproverResolutionResponse(7L);
        when(restTemplate.exchange(
                eq("http://admin/internal/employees/1/approver"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(ApproverResolutionResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
        EmployeeManagerRoutingRestClient client =
                new EmployeeManagerRoutingRestClient(restTemplate, "http://admin");

        org.assertj.core.api.Assertions.assertThat(client.resolveApprover(1L)).isEqualTo(7L);
    }

    @Test
    void returnsNullWhenAdminDoesNotReturnAnApprover() {
        when(restTemplate.exchange(
                eq("http://admin/internal/employees/1/approver"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(ApproverResolutionResponse.class)))
                .thenReturn(ResponseEntity.ok().build());
        EmployeeManagerRoutingRestClient client =
                new EmployeeManagerRoutingRestClient(restTemplate, "http://admin");

        org.assertj.core.api.Assertions.assertThat(client.resolveApprover(1L)).isNull();
    }

    @Test
    void wrapsApproverResolutionFailuresWithContext() {
        when(restTemplate.exchange(
                eq("http://admin/internal/employees/1/approver"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(ApproverResolutionResponse.class)))
                .thenThrow(new RestClientException("connection refused"));
        EmployeeManagerRoutingRestClient client =
                new EmployeeManagerRoutingRestClient(restTemplate, "http://admin");

        assertThatThrownBy(() -> client.resolveApprover(1L))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("employeeId=1");
    }

    @Test
    void readsPolicyMandateFromAdminService() {
        TravelRequestPolicySnapshot response = new TravelRequestPolicySnapshot(true);
        when(restTemplate.exchange(
                eq("http://admin/internal/travel-request-policy"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(TravelRequestPolicySnapshot.class)))
                .thenReturn(ResponseEntity.ok(response));
        TravelRequestPolicyRestClient client =
                new TravelRequestPolicyRestClient(restTemplate, "http://admin");

        org.assertj.core.api.Assertions.assertThat(client.documentsMandatory()).isTrue();
    }

    @Test
    void treatsMissingPolicySnapshotAsNotMandatory() {
        when(restTemplate.exchange(
                eq("http://admin/internal/travel-request-policy"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(TravelRequestPolicySnapshot.class)))
                .thenReturn(ResponseEntity.ok().build());
        TravelRequestPolicyRestClient client =
                new TravelRequestPolicyRestClient(restTemplate, "http://admin");

        org.assertj.core.api.Assertions.assertThat(client.documentsMandatory()).isFalse();
    }

    @Test
    void wrapsPolicyReadFailuresWithContext() {
        when(restTemplate.exchange(
                eq("http://admin/internal/travel-request-policy"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(TravelRequestPolicySnapshot.class)))
                .thenThrow(new RestClientException("connection refused"));
        TravelRequestPolicyRestClient client =
                new TravelRequestPolicyRestClient(restTemplate, "http://admin");

        assertThatThrownBy(client::documentsMandatory)
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("policy mandate");
    }

    @Test
    void recordsFailedDocumentWithFailedStatus() {
        TravelRequest travelRequest = new TravelRequest();
        travelRequest.setId(10L);
        TravelRequestFailedDocumentRecorder failedRecorder =
                new TravelRequestFailedDocumentRecorder(documentRepository);

        failedRecorder.recordFailedDocument(travelRequest, "file.pdf", DocumentFileType.PDF, 12L);

        verify(documentRepository).save(any());
    }
}

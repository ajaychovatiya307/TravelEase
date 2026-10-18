package com.travelease.travelrequest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestStatus;
import com.travelease.travelrequest.repository.TravelRequestRepository;
import com.travelease.travelrequest.service.TravelRequestService;
import com.travelease.travelrequest.service.impl.EmployeeManagerRoutingServiceClient;
import com.travelease.travelrequest.service.impl.TravelRequestPolicyClient;

/**
 * #10 concurrency: deliberately NOT @Transactional, so each step commits like separate requests
 * and the entity @Version is exercised for real.
 */
@SpringBootTest(classes = TravelRequestIntegrationTestConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:travelrequest-concurrency-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Travel request stale-update detection IT (#10)")
class TravelRequestConcurrencyIT {

    @Autowired
    private TravelRequestRepository repository;

    @Autowired
    private TravelRequestService service;

    @MockBean
    private EmployeeManagerRoutingServiceClient routingClient;

    @MockBean
    private TravelRequestPolicyClient policyClient;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("a decision based on a stale copy fails after the owner cancelled (optimistic lock)")
    void staleUpdateAfterCancellationIsRejected() {
        TravelRequest request = new TravelRequest();
        request.setEmployeeId(1L);
        request.setApproverId(2L);
        request.setDestination("Pune");
        request.setStartDate(LocalDate.now().plusDays(10));
        request.setEndDate(LocalDate.now().plusDays(12));
        request.setJustification("Customer workshop");
        request.setStatus(TravelRequestStatus.PENDING);
        request.setDeleted(false);
        request.setCreatedAt(Instant.now());
        request.setCreatedBy("it");
        request.setUpdatedAt(Instant.now());
        request.setUpdatedBy("it");
        long id = repository.saveAndFlush(request).getId();

        // the approver's screen loaded the request before the owner withdrew it
        TravelRequest staleCopy = repository.findById(id).orElseThrow();

        service.cancelTravelRequest(id, 1L);

        staleCopy.setStatus(TravelRequestStatus.APPROVED);
        assertThatThrownBy(() -> repository.saveAndFlush(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(TravelRequestStatus.CANCELLED);
    }
}

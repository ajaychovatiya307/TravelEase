package com.travelease.expense.service;

import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.common.error.ErrorResponse;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.expense.client.PolicyConfigClient;
import com.travelease.expense.client.TravelRequestClient;
import com.travelease.expense.dto.ExpenseClaimCreateRequest;
import com.travelease.expense.dto.ExpenseClaimResponse;
import com.travelease.expense.dto.ExpenseClaimUpdateRequest;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseClaimStatus;
import com.travelease.expense.entity.ExpenseDocument;
import com.travelease.expense.mapper.ExpenseClaimMapper;
import com.travelease.expense.repository.ExpenseClaimRepository;
import com.travelease.expense.repository.ExpenseDocumentRepository;
import com.travelease.expense.service.impl.ExpenseClaimServiceImpl;
import com.travelease.expense.validation.DuplicateExpenseDetector;
import com.travelease.expense.validation.ExpenseClaimPolicyValidator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExpenseClaimServiceImpl} (tasks.md L-05; spec.md FR-EXP-01–06;
 * VAL-05–10, VAL-13; AC-06, AC-07, AC-08, AC-13), covering expense claim submission
 * and edit with positive and negative cases per Constitution §10:
 *
 * <ul>
 *   <li>valid submission against an APPROVED, owned trip: the claim is persisted with
 *       {@code PENDING} status, the parsed {@code employeeId}/{@code travelRequestId},
 *       request fields, {@code duplicateFlag = false}, and the mapped response is
 *       returned (FR-EXP-01, FR-EXP-02, AC-06)</li>
 *   <li>potential duplicate: the claim is still saved (flagging never blocks) but with
 *       {@code duplicateFlag = true} for manual Finance review (FR-EXP-07, AC-09)</li>
 *   <li>trip not APPROVED / not owned: the {@link PolicyViolationException} thrown by
 *       the cross-service {@code TravelRequestClient#validateTravelRequestApprovedAndOwned}
 *       check propagates and nothing is saved (VAL-09)</li>
 *   <li>expense date outside the trip range: the {@link PolicyViolationException}
 *       (field {@code "expenseDate"}) from {@code ExpenseClaimPolicyValidator}
 *       propagates and nothing is saved (VAL-08)</li>
 *   <li>foreign currency on submit or edit: the {@link PolicyViolationException}
 *       (field {@code "currency"}) from
 *       {@code ExpenseClaimPolicyValidator#validateBaseCurrency} propagates and
 *       nothing is saved or mutated (NFR-05, VAL-06)</li>
 *   <li>amount above the category receipt threshold: creation succeeds without
 *       any documents (create-then-upload flow; the create path no longer
 *       consults the threshold), an edit of the PENDING claim without attached
 *       documents throws the {@link PolicyViolationException} (field
 *       {@code "documentIds"}), and an edit with an attached document succeeds
 *       (VAL-10, edit path)</li>
 *   <li>edit of a non-{@code PENDING} claim: the {@link PolicyViolationException} from
 *       {@code ExpenseClaimPolicyValidator#validateEditable} propagates; the claim is
 *       never saved and the duplicate detector is never invoked (VAL-13, AC-13)</li>
 *   <li>edit of a non-existent claim: {@link ResourceNotFoundException} before any
 *       other collaborator is touched (AC-08)</li>
 *   <li>edit of a {@code PENDING} claim: the updated fields are persisted, the
 *       duplicate flag is re-evaluated with the claim itself excluded, and the mapped
 *       response is returned (FR-EXP-06, AC-07, AC-13)</li>
 * </ul>
 *
 * <p>The service is plain (non-Spring) business logic with constructor-injected
 * collaborators, so the repository, both cross-service clients, the mapper, the
 * policy validator, the duplicate detector and the {@code ApplicationEventPublisher}
 * are mocked with Mockito and the service is injected via {@code @InjectMocks} -
 * no Spring context is needed. Event emission ({@code CREATED}/{@code UPDATED})
 * publishes a {@code DomainEventEnvelope} through the publisher (tasks.md H-07);
 * the positive tests assert the flow reaches the response mapping after
 * {@code save} - i.e. the publish step runs without error.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExpenseClaimServiceImpl submitExpenseClaim / editExpenseClaim (tasks.md L-05; spec.md FR-EXP-01–06; VAL-05–10, VAL-13; AC-06, AC-07, AC-08, AC-13)")
class ExpenseClaimServiceTest {

    private static final Long TRAVEL_REQUEST_ID = 10L;
    private static final Long EMPLOYEE_ID = 1L;
    private static final Long EXPENSE_CLAIM_ID = 20L;
    private static final String TRAVEL_REQUEST_ID_STR = "10";
    private static final String EMPLOYEE_ID_STR = "1";
    private static final String EXPENSE_CLAIM_ID_STR = "20";
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");
    private static final BigDecimal THRESHOLD = new BigDecimal("100.00");
    private static final String CURRENCY = "EUR";
    private static final String FOREIGN_CURRENCY = "USD";
    private static final String CATEGORY = "MEAL";
    private static final LocalDate EXPENSE_DATE = LocalDate.of(2025, 6, 15);
    private static final LocalDate OUT_OF_RANGE_DATE = LocalDate.of(2025, 7, 1);
    private static final LocalDate TRIP_START = LocalDate.of(2025, 6, 10);
    private static final LocalDate TRIP_END = LocalDate.of(2025, 6, 20);
    private static final List<String> DOC_IDS = List.of("doc-1");

    @Mock
    private ExpenseClaimRepository expenseClaimRepository;

    @Mock
    private ExpenseDocumentRepository expenseDocumentRepository;

    @Mock
    private TravelRequestClient travelRequestClient;

    @Mock
    private PolicyConfigClient policyConfigClient;

    @Mock
    private ExpenseClaimMapper expenseClaimMapper;

    @Mock
    private ExpenseClaimPolicyValidator policyValidator;

    @Mock
    private DuplicateExpenseDetector duplicateExpenseDetector;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ExpenseClaimServiceImpl expenseClaimService;

    private static ExpenseClaimCreateRequest createRequest() {
        return createRequest(EXPENSE_DATE);
    }

    private static ExpenseClaimCreateRequest createRequest(LocalDate expenseDate) {
        ExpenseClaimCreateRequest request = new ExpenseClaimCreateRequest();
        request.setTravelRequestId(TRAVEL_REQUEST_ID_STR);
        request.setAmount(AMOUNT);
        request.setCurrency(CURRENCY);
        request.setCategory(CATEGORY);
        request.setExpenseDate(expenseDate);
        request.setDocumentIds(DOC_IDS);
        return request;
    }

    private static ExpenseClaimCreateRequest createRequest(LocalDate expenseDate, String currency) {
        ExpenseClaimCreateRequest request = createRequest(expenseDate);
        request.setCurrency(currency);
        return request;
    }

    private static ExpenseClaimCreateRequest createRequestWithoutDocuments() {
        ExpenseClaimCreateRequest request = createRequest(EXPENSE_DATE);
        request.setDocumentIds(List.of());
        return request;
    }

    private static ExpenseClaimUpdateRequest updateRequest() {
        ExpenseClaimUpdateRequest request = new ExpenseClaimUpdateRequest();
        request.setTravelRequestId(TRAVEL_REQUEST_ID_STR);
        request.setAmount(AMOUNT);
        request.setCurrency(CURRENCY);
        request.setCategory(CATEGORY);
        request.setExpenseDate(EXPENSE_DATE);
        request.setDocumentIds(DOC_IDS);
        return request;
    }

    private static ExpenseClaimUpdateRequest updateRequest(String currency) {
        ExpenseClaimUpdateRequest request = updateRequest();
        request.setCurrency(currency);
        return request;
    }

    private static ExpenseClaim pendingClaim() {
        ExpenseClaim claim = new ExpenseClaim();
        claim.setId(EXPENSE_CLAIM_ID);
        claim.setEmployeeId(EMPLOYEE_ID);
        claim.setTravelRequestId(TRAVEL_REQUEST_ID);
        claim.setAmount(new BigDecimal("120.00"));
        claim.setCurrency("USD");
        claim.setCategory("HOTEL");
        claim.setExpenseDate(EXPENSE_DATE.minusDays(1));
        claim.setStatus(ExpenseClaimStatus.PENDING);
        claim.setDeleted(false);
        return claim;
    }

    private static ExpenseClaim approvedClaim() {
        ExpenseClaim claim = pendingClaim();
        claim.setStatus(ExpenseClaimStatus.APPROVED);
        return claim;
    }

    private static TravelRequestClient.TravelRequestDateRange dateRange() {
        return new TravelRequestClient.TravelRequestDateRange(TRIP_START, TRIP_END);
    }

    private static ExpenseClaimResponse response() {
        return new ExpenseClaimResponse(EXPENSE_CLAIM_ID_STR, EMPLOYEE_ID_STR, TRAVEL_REQUEST_ID_STR,
                AMOUNT, CURRENCY, CATEGORY, EXPENSE_DATE, ExpenseClaimStatus.PENDING, false,
                null, null, List.of(), null, null);
    }

    private static ExpenseDocument document() {
        ExpenseDocument document = new ExpenseDocument();
        document.setId(1L);
        return document;
    }

    @Nested
    @DisplayName("submitExpenseClaim — positive cases (FR-EXP-01–02, FR-EXP-07, AC-06, AC-09)")
    class SubmitPositiveCases {

        @Test
        @DisplayName("submits a valid claim for an approved, owned trip: PENDING claim with parsed ids is saved, duplicate detector runs, mapped response returned")
        void submitsValidClaimForApprovedTrip() {
            ExpenseClaimResponse expected = response();

            when(travelRequestClient.getTravelRequestDateRange(TRAVEL_REQUEST_ID_STR)).thenReturn(dateRange());
            when(expenseClaimRepository.save(any(ExpenseClaim.class))).thenAnswer(invocation -> {
                ExpenseClaim savedClaim = invocation.getArgument(0);
                savedClaim.setId(EXPENSE_CLAIM_ID); // JPA assigns the id on persist
                return savedClaim;
            });
            when(expenseClaimMapper.toResponse(any(ExpenseClaim.class), anyList())).thenReturn(expected);

            ExpenseClaimResponse actual =
                    expenseClaimService.submitExpenseClaim(TRAVEL_REQUEST_ID_STR, EMPLOYEE_ID_STR, createRequest());

            assertThat(actual).isSameAs(expected);

            ArgumentCaptor<ExpenseClaim> captor = ArgumentCaptor.forClass(ExpenseClaim.class);
            verify(expenseClaimRepository).save(captor.capture());
            ExpenseClaim saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(ExpenseClaimStatus.DRAFT);
            assertThat(saved.getEmployeeId()).isEqualTo(EMPLOYEE_ID);
            assertThat(saved.getTravelRequestId()).isEqualTo(TRAVEL_REQUEST_ID);
            assertThat(saved.getAmount()).isEqualByComparingTo(AMOUNT);
            assertThat(saved.getCurrency()).isEqualTo(CURRENCY);
            assertThat(saved.getCategory()).isEqualTo(CATEGORY);
            assertThat(saved.getExpenseDate()).isEqualTo(EXPENSE_DATE);
            assertThat(saved.isDuplicateFlag()).isFalse();
            assertThat(saved.isDeleted()).isFalse();

            // VAL-09 cross-service check and FR-EXP-07 duplicate detection actually ran
            verify(travelRequestClient).validateTravelRequestApprovedAndOwned(TRAVEL_REQUEST_ID_STR, EMPLOYEE_ID_STR);
            verify(duplicateExpenseDetector).isPotentialDuplicate(TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY);
            // A CREATED domain event is published for the persisted claim (H-07)
            ArgumentCaptor<DomainEventEnvelope> eventCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            DomainEventEnvelope event = eventCaptor.getValue();
            assertThat(event.entityType()).isEqualTo("EXPENSE_CLAIM");
            assertThat(event.entityId()).isEqualTo(EXPENSE_CLAIM_ID_STR);
            assertThat(event.action()).isEqualTo("CREATED");
            assertThat(event.timestamp()).isNotNull();
            verify(expenseClaimMapper).toResponse(saved, List.of());
        }

        @Test
        @DisplayName("flags a potential duplicate but still saves the claim (non-blocking; FR-EXP-07, AC-09)")
        void flagsButDoesNotBlockDuplicateClaim() {
            ExpenseClaimResponse expected = response();

            when(travelRequestClient.getTravelRequestDateRange(TRAVEL_REQUEST_ID_STR)).thenReturn(dateRange());
            when(duplicateExpenseDetector.isPotentialDuplicate(TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY))
                    .thenReturn(true);
            when(expenseClaimRepository.save(any(ExpenseClaim.class))).thenAnswer(invocation -> {
                ExpenseClaim savedClaim = invocation.getArgument(0);
                savedClaim.setId(EXPENSE_CLAIM_ID); // JPA assigns the id on persist
                return savedClaim;
            });
            when(expenseClaimMapper.toResponse(any(ExpenseClaim.class), anyList())).thenReturn(expected);

            ExpenseClaimResponse actual =
                    expenseClaimService.submitExpenseClaim(TRAVEL_REQUEST_ID_STR, EMPLOYEE_ID_STR, createRequest());

            assertThat(actual).isSameAs(expected);

            ArgumentCaptor<ExpenseClaim> captor = ArgumentCaptor.forClass(ExpenseClaim.class);
            verify(expenseClaimRepository).save(captor.capture());
            assertThat(captor.getValue().isDuplicateFlag()).isTrue();
            assertThat(captor.getValue().getStatus()).isEqualTo(ExpenseClaimStatus.DRAFT);
        }

        @Test
        @DisplayName("submits an above-threshold claim without documents (create-then-upload; VAL-10 is edit-path-only) - the threshold is never consulted on create")
        void allowsSubmissionAboveThresholdWithoutDocuments() {
            ExpenseClaimResponse expected = response();

            when(travelRequestClient.getTravelRequestDateRange(TRAVEL_REQUEST_ID_STR)).thenReturn(dateRange());
            when(expenseClaimRepository.save(any(ExpenseClaim.class))).thenAnswer(invocation -> {
                ExpenseClaim savedClaim = invocation.getArgument(0);
                savedClaim.setId(EXPENSE_CLAIM_ID); // JPA assigns the id on persist
                return savedClaim;
            });
            when(expenseClaimMapper.toResponse(any(ExpenseClaim.class), anyList())).thenReturn(expected);

            ExpenseClaimResponse actual =
                    expenseClaimService.submitExpenseClaim(TRAVEL_REQUEST_ID_STR, EMPLOYEE_ID_STR,
                            createRequestWithoutDocuments());

            assertThat(actual).isSameAs(expected);

            ArgumentCaptor<ExpenseClaim> captor = ArgumentCaptor.forClass(ExpenseClaim.class);
            verify(expenseClaimRepository).save(captor.capture());
            ExpenseClaim saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(ExpenseClaimStatus.DRAFT);
            assertThat(saved.getAmount()).isEqualByComparingTo(AMOUNT);
            assertThat(saved.isDuplicateFlag()).isFalse();

            // VAL-10 now lives on the edit path only: create never consults the
            // category threshold nor the receipt requirement validator
            verify(policyConfigClient, never()).getActiveReceiptThreshold(any());
            verify(policyValidator, never()).validateReceiptRequirement(any(), any(), any());
            verify(eventPublisher).publishEvent(any(DomainEventEnvelope.class));
            verify(expenseClaimMapper).toResponse(saved, List.of());
        }
    }

    @Nested
    @DisplayName("submitExpenseClaim — negative cases (VAL-08, VAL-09; Constitution §10)")
    class SubmitNegativeCases {

        @Test
        @DisplayName("propagates the PolicyViolationException when the trip is not APPROVED (VAL-09); nothing is saved")
        void rejectsSubmissionWhenTripIsNotApproved() {
            doThrow(new PolicyViolationException(
                    "Travel request must be in APPROVED status to submit an expense claim"))
                    .when(travelRequestClient).validateTravelRequestApprovedAndOwned(TRAVEL_REQUEST_ID_STR, EMPLOYEE_ID_STR);

            ExpenseClaimCreateRequest request = createRequest();

            assertThatThrownBy(() ->
                    expenseClaimService.submitExpenseClaim(TRAVEL_REQUEST_ID_STR, EMPLOYEE_ID_STR, request))
                    .isInstanceOf(PolicyViolationException.class)
                    .hasMessageContaining("APPROVED");

            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verifyNoInteractions(duplicateExpenseDetector, policyConfigClient, expenseClaimMapper);
        }

        @Test
        @DisplayName("propagates the PolicyViolationException (field \"expenseDate\") when the expense date falls outside the trip range (VAL-08); nothing is saved")
        void rejectsExpenseDateOutsideTripRange() {
            when(travelRequestClient.getTravelRequestDateRange(TRAVEL_REQUEST_ID_STR)).thenReturn(dateRange());
            doThrow(new PolicyViolationException("expenseDate",
                    "Expense date must fall within travel request date range: " + TRIP_START + " to " + TRIP_END))
                    .when(policyValidator).validateExpenseDateInRange(OUT_OF_RANGE_DATE, TRIP_START, TRIP_END);

            assertThatThrownBy(() ->
                    expenseClaimService.submitExpenseClaim(TRAVEL_REQUEST_ID_STR, EMPLOYEE_ID_STR,
                            createRequest(OUT_OF_RANGE_DATE)))
                    .isInstanceOfSatisfying(PolicyViolationException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo("POLICY_VIOLATION");
                        assertThat(ex.getFieldErrors())
                                .extracting(ErrorResponse.FieldError::field)
                                .containsExactly("expenseDate");
                    });

            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verifyNoInteractions(duplicateExpenseDetector, policyConfigClient, expenseClaimMapper);
        }

        }

    @Nested
    @DisplayName("editExpenseClaim — negative cases (VAL-10, VAL-13; AC-08, AC-13; Constitution §10)")
    class EditNegativeCases {

        @Test
        @DisplayName("rejects editing a non-PENDING claim with a PolicyViolationException (VAL-13); nothing is saved, duplicate detector never invoked")
        void rejectsEditOfNonPendingClaim() {
            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(approvedClaim()));
            doThrow(new PolicyViolationException("Expense claim can only be edited when status is PENDING"))
                    .when(policyValidator).validateEditable(ExpenseClaimStatus.APPROVED);

            ExpenseClaimUpdateRequest request = updateRequest();

            assertThatThrownBy(() -> expenseClaimService.editExpenseClaim(EXPENSE_CLAIM_ID_STR, EMPLOYEE_ID, request))
                    .isInstanceOf(PolicyViolationException.class)
                    .hasMessageContaining("PENDING");

            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verifyNoInteractions(duplicateExpenseDetector, travelRequestClient, policyConfigClient, expenseClaimMapper);
        }

        @Test
        @DisplayName("rejects editing a non-existent claim with a ResourceNotFoundException; no other collaborator is touched")
        void rejectsEditOfNonExistentClaim() {
            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> expenseClaimService.editExpenseClaim(EXPENSE_CLAIM_ID_STR, EMPLOYEE_ID, updateRequest()))
                    .isInstanceOfSatisfying(ResourceNotFoundException.class,
                            ex -> assertThat(ex.getMessage()).contains(EXPENSE_CLAIM_ID_STR));

            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verifyNoInteractions(travelRequestClient, policyConfigClient, policyValidator,
                    duplicateExpenseDetector, expenseClaimMapper);
        }

        @Test
        @DisplayName("rejects editing an above-threshold PENDING claim without attached documents (VAL-10, edit path); nothing is saved")
        void rejectsEditAboveThresholdWithoutAttachedDocuments() {
            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(pendingClaim()));
            when(travelRequestClient.getTravelRequestDateRange(TRAVEL_REQUEST_ID_STR)).thenReturn(dateRange());
            when(policyConfigClient.getActiveReceiptThreshold(CATEGORY)).thenReturn(THRESHOLD);
            when(expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(List.of());
            doThrow(new PolicyViolationException("documentIds",
                    "A supporting document is required when the amount exceeds the category threshold (VAL-10)"))
                    .when(policyValidator).validateReceiptRequirement(AMOUNT, THRESHOLD, List.of());

            assertThatThrownBy(() -> expenseClaimService.editExpenseClaim(EXPENSE_CLAIM_ID_STR, EMPLOYEE_ID, updateRequest()))
                    .isInstanceOfSatisfying(PolicyViolationException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo("POLICY_VIOLATION");
                        assertThat(ex.getMessage())
                                .isEqualTo("A supporting document is required when the amount exceeds the category threshold (VAL-10)");
                        assertThat(ex.getFieldErrors())
                                .extracting(ErrorResponse.FieldError::field)
                                .containsExactly("documentIds");
                    });

            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verify(eventPublisher, never()).publishEvent(any(DomainEventEnvelope.class));
        }
    }

    @Nested
    @DisplayName("editExpenseClaim — positive cases (FR-EXP-06, AC-07, AC-13)")
    class EditPositiveCases {

        @Test
        @DisplayName("edits a PENDING claim: updated fields are saved, duplicate flag re-evaluated excluding the claim itself, VAL-10 passes via the attached document, mapped response returned")
        void editsPendingClaim() {
            ExpenseClaim claim = pendingClaim();
            ExpenseClaimResponse expected = response();

            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(claim));
            when(travelRequestClient.getTravelRequestDateRange(TRAVEL_REQUEST_ID_STR)).thenReturn(dateRange());
            when(policyConfigClient.getActiveReceiptThreshold(CATEGORY)).thenReturn(THRESHOLD);
            when(expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID))
                    .thenReturn(List.of(document()));
            when(duplicateExpenseDetector.isPotentialDuplicateExcluding(
                    TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY, EXPENSE_CLAIM_ID)).thenReturn(false);
            when(expenseClaimRepository.save(claim)).thenReturn(claim);
            when(expenseClaimMapper.toResponse(any(ExpenseClaim.class), anyList())).thenReturn(expected);

            ExpenseClaimResponse actual = expenseClaimService.editExpenseClaim(EXPENSE_CLAIM_ID_STR, EMPLOYEE_ID, updateRequest());

            assertThat(actual).isSameAs(expected);

            assertThat(claim.getAmount()).isEqualByComparingTo(AMOUNT);
            assertThat(claim.getCurrency()).isEqualTo(CURRENCY);
            assertThat(claim.getCategory()).isEqualTo(CATEGORY);
            assertThat(claim.getExpenseDate()).isEqualTo(EXPENSE_DATE);
            assertThat(claim.isDuplicateFlag()).isFalse();
            assertThat(claim.getStatus()).isEqualTo(ExpenseClaimStatus.PENDING);

            verify(duplicateExpenseDetector).isPotentialDuplicateExcluding(
                    TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY, EXPENSE_CLAIM_ID);
            verify(expenseClaimRepository).save(claim);
            // UPDATED event emission (H-07) ran without error; reaching the mapper
            // after save proves the publish step completed
            verify(eventPublisher).publishEvent(any(DomainEventEnvelope.class));
            verify(expenseClaimMapper).toResponse(claim, List.of());
        }
    }

    @Nested
    @DisplayName("submitExpenseClaim / editExpenseClaim — base currency enforcement (NFR-05, VAL-06; Q18)")
    class BaseCurrencyEnforcement {

        @Test
        @DisplayName("rejects a claim with a foreign currency on submit (NFR-05); the \"currency\" violation propagates, nothing is saved, no event is published")
        void rejectsSubmitWithForeignCurrency() {
            doThrow(new PolicyViolationException("currency",
                    "Only the base currency INR is accepted; foreign-currency amounts must be pre-converted before entry (NFR-05, Q18)"))
                    .when(policyValidator).validateBaseCurrency(FOREIGN_CURRENCY);

            assertThatThrownBy(() ->
                    expenseClaimService.submitExpenseClaim(TRAVEL_REQUEST_ID_STR, EMPLOYEE_ID_STR,
                            createRequest(EXPENSE_DATE, FOREIGN_CURRENCY)))
                    .isInstanceOfSatisfying(PolicyViolationException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo("POLICY_VIOLATION");
                        assertThat(ex.getFieldErrors())
                                .extracting(ErrorResponse.FieldError::field)
                                .containsExactly("currency");
                    });

            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verify(eventPublisher, never()).publishEvent(any(DomainEventEnvelope.class));
            verifyNoInteractions(travelRequestClient, duplicateExpenseDetector, policyConfigClient, expenseClaimMapper);
        }

        @Test
        @DisplayName("rejects editing a PENDING claim with a foreign currency (NFR-05); the loaded claim is not mutated, nothing is saved, no event is published")
        void rejectsEditWithForeignCurrency() {
            ExpenseClaim claim = pendingClaim();
            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(claim));
            doThrow(new PolicyViolationException("currency",
                    "Only the base currency INR is accepted; foreign-currency amounts must be pre-converted before entry (NFR-05, Q18)"))
                    .when(policyValidator).validateBaseCurrency(FOREIGN_CURRENCY);

            assertThatThrownBy(() ->
                    expenseClaimService.editExpenseClaim(EXPENSE_CLAIM_ID_STR, EMPLOYEE_ID, updateRequest(FOREIGN_CURRENCY)))
                    .isInstanceOfSatisfying(PolicyViolationException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo("POLICY_VIOLATION");
                        assertThat(ex.getFieldErrors())
                                .extracting(ErrorResponse.FieldError::field)
                                .containsExactly("currency");
                    });

            // the currency rule (step 4) fires before any field is written (step 7),
            // so the loaded claim must keep its pre-edit values
            assertThat(claim.getAmount()).isEqualByComparingTo(new BigDecimal("120.00"));
            assertThat(claim.getCategory()).isEqualTo("HOTEL");
            assertThat(claim.getExpenseDate()).isEqualTo(EXPENSE_DATE.minusDays(1));
            assertThat(claim.getStatus()).isEqualTo(ExpenseClaimStatus.PENDING);

            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verify(eventPublisher, never()).publishEvent(any(DomainEventEnvelope.class));
            verifyNoInteractions(travelRequestClient, policyConfigClient, duplicateExpenseDetector, expenseClaimMapper);
        }
    }

    @Nested
    @DisplayName("getExpenseClaim — read returns persisted documents (N-04)")
    class Fetching {

        @Test
        @DisplayName("loads the claim's non-deleted documents and passes them to the mapper instead of an empty list")
        void existingClaimReturnsResponseWithLoadedDocuments() {
            ExpenseClaim claim = approvedClaim();
            List<ExpenseDocument> documents = List.of(document());
            ExpenseClaimResponse expected = response();

            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(claim));
            when(expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(documents);
            when(expenseClaimMapper.toResponse(claim, documents)).thenReturn(expected);

            ExpenseClaimResponse actual = expenseClaimService.getExpenseClaim(EXPENSE_CLAIM_ID_STR, EMPLOYEE_ID, Set.of("EMPLOYEE"));

            assertThat(actual).isSameAs(expected);
            verify(expenseDocumentRepository).findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID);
            verify(expenseClaimMapper).toResponse(claim, documents);
        }

        @Test
        @DisplayName("unknown or soft-deleted claim fails with ResourceNotFoundException and no document lookup runs")
        void unknownIdReturns404() {
            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> expenseClaimService.getExpenseClaim(EXPENSE_CLAIM_ID_STR, EMPLOYEE_ID, Set.of("EMPLOYEE")))
                    .isInstanceOfSatisfying(ResourceNotFoundException.class,
                            ex -> assertThat(ex.getMessage()).contains(EXPENSE_CLAIM_ID_STR));

            verify(expenseDocumentRepository, never()).findByExpenseClaimIdAndDeletedFalse(anyLong());
            verifyNoInteractions(expenseClaimMapper);
        }
    }

    @Nested
    @DisplayName("listExpenseClaims — each row carries its documents (N-04; mirrors TravelRequestServiceImpl.listTravelRequests)")
    class Listing {

        @Test
        @DisplayName("maps each claim in the page with its non-deleted documents")
        void listReturnsPageWithDocumentsPerRow() {
            ExpenseClaim claim = pendingClaim();
            List<ExpenseDocument> documents = List.of(document());
            ExpenseClaimResponse mapped = response();
            Pageable pageable = PageRequest.of(0, 20);

            when(expenseClaimRepository.findByStatusNotAndDeletedFalse(ExpenseClaimStatus.DRAFT, pageable))
                    .thenReturn(new PageImpl<>(List.of(claim), pageable, 1));
            when(expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(documents);
            when(expenseClaimMapper.toResponse(claim, documents)).thenReturn(mapped);

            Page<ExpenseClaimResponse> page = expenseClaimService.listExpenseClaims(pageable);

            assertThat(page.getContent()).containsExactly(mapped);
            verify(expenseDocumentRepository).findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID);
            verify(expenseClaimMapper).toResponse(claim, documents);
        }

        @Test
        @DisplayName("employee listing queries only claims owned by that employee")
        void employeeListReturnsOnlyOwnedClaims() {
            ExpenseClaim claim = pendingClaim();
            List<ExpenseDocument> documents = List.of(document());
            ExpenseClaimResponse mapped = response();
            Pageable pageable = PageRequest.of(0, 20);

            when(expenseClaimRepository.findByEmployeeIdAndDeletedFalse(EMPLOYEE_ID, pageable))
                    .thenReturn(new PageImpl<>(List.of(claim), pageable, 1));
            when(expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(documents);
            when(expenseClaimMapper.toResponse(claim, documents)).thenReturn(mapped);

            Page<ExpenseClaimResponse> page = expenseClaimService.listExpenseClaims(EMPLOYEE_ID_STR, pageable);

            assertThat(page.getContent()).containsExactly(mapped);
            verify(expenseClaimRepository).findByEmployeeIdAndDeletedFalse(EMPLOYEE_ID, pageable);
            verify(expenseClaimRepository, never()).findByDeletedFalse(pageable);
            verify(expenseDocumentRepository).findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID);
        }

        @Test
        @DisplayName("applies status and duplicate filters together for Finance")
        void filtersFinanceListByStatusAndDuplicateFlag() {
            ExpenseClaim claim = pendingClaim();
            ExpenseClaimResponse mapped = response();
            Pageable pageable = PageRequest.of(0, 20);

            when(expenseClaimRepository.findByStatusAndDuplicateFlagAndDeletedFalse(
                    ExpenseClaimStatus.PENDING, true, pageable))
                    .thenReturn(new PageImpl<>(List.of(claim), pageable, 1));
            when(expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID))
                    .thenReturn(List.of());
            when(expenseClaimMapper.toResponse(claim, List.of())).thenReturn(mapped);

            Page<ExpenseClaimResponse> result = expenseClaimService.listExpenseClaims(
                    ExpenseClaimStatus.PENDING, true, pageable);

            assertThat(result.getContent()).containsExactly(mapped);
            verify(expenseClaimRepository).findByStatusAndDuplicateFlagAndDeletedFalse(
                    ExpenseClaimStatus.PENDING, true, pageable);
            verify(expenseClaimRepository, never()).findByDeletedFalse(pageable);
        }

        @ParameterizedTest
        @EnumSource(value = ExpenseClaimStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
        @DisplayName("uses exact status matching for every Finance queue lifecycle status")
        void filtersFinanceListByExactStatus(ExpenseClaimStatus status) {
            Pageable pageable = PageRequest.of(0, 20);
            when(expenseClaimRepository.findByStatusAndDeletedFalse(status, pageable))
                    .thenReturn(Page.empty(pageable));

            Page<ExpenseClaimResponse> result = expenseClaimService.listExpenseClaims(status, null, pageable);

            assertThat(result).isEmpty();
            verify(expenseClaimRepository).findByStatusAndDeletedFalse(status, pageable);
            verify(expenseClaimRepository, never()).findByDeletedFalse(pageable);
        }
    }
}
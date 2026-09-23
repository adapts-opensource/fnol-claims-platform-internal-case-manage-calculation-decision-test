package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Spy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Verifies system behavior when an address lookup returns multiple active policies for a single insured entity.
 * Ensures correct transformation logic, reserve creation, and structured logging for multi-policy matches.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Address Matches Multiple Policies Under Same Insured Tests")
class AddressMatchesMultiplePoliciesUnderSameInsuredTest {

    // --- Mocks for External Dependencies (Infra I/O) ---
    @Mock
    private PolicyLookupService policyLookupService;

    @Mock
    private ReserveCreationService reserveCreationService;

    @Mock
    private StructuredLogger logger;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    // --- Spy for internal state verification if needed ---
    @Spy
    private DecisionTransformer decisionTransformer;

    // --- Test Constants ---
    private static final String INSURED_ID = "INS-001";
    private static final String ADDRESS_LINE_1 = "123 Insurance Way";
    private static final String CITY = "Springfield";
    private static final String STATE = "IL";
    private static final String ZIP = "62704";
    private static final String POLICY_ID_1 = "POL-001";
    private static final String POLICY_ID_2 = "POL-002";
    private static final String RESERVE_ID = "RES-999";

    private AddressRequest addressRequest;
    private List<Policy> matchedPolicies;

    @BeforeEach
    void setUp() {
        // Input Validation: Ensure request is well-formed
        addressRequest = new AddressRequest(INSURED_ID, ADDRESS_LINE_1, CITY, STATE, ZIP);
        
        // Scenario Setup: Multiple policies match the address
        matchedPolicies = List.of(
            new Policy(POLICY_ID_1, INSURED_ID, ADDRESS_LINE_1, CITY, STATE, ZIP, PolicyType.AUTOMOBILE, PolicyStatus.ACTIVE),
            new Policy(POLICY_ID_2, INSURED_ID, ADDRESS_LINE_1, CITY, STATE, ZIP, PolicyType.HOMEOWNERS, PolicyStatus.ACTIVE)
        );
    }

    @Test
    @DisplayName("Address matches multiple policies under same insured")
    void address_matches_multiple_policies_under_same_insured() {
        // Given: Mock policy lookup to return multiple matches
        when(policyLookupService.findByAddressAndInsured(INSURED_ID, ADDRESS_LINE_1, CITY, STATE, ZIP))
            .thenReturn(matchedPolicies);

        // Given: Mock reserve creation for the aggregated exposure
        when(reserveCreationService.createReserveLine(any(ReserveLineRequest.class)))
            .thenReturn(new ReserveLineResponse(RESERVE_ID, ReserveStatus.PENDING));

        // When: Execute the transformation decision
        DecisionResult result = insuredEngagementService.transformDecision(addressRequest);

        // Then: Assert result contains multiple policies
        assertNotNull(result, "Result should not be null");
        assertEquals(DecisionOutcome.MULTI_POLICY_MATCH, result.getOutcome());
        assertEquals(2, result.getPolicyCount());
        assertTrue(result.getPolicies().contains(POLICY_ID_1));
        assertTrue(result.getPolicies().contains(POLICY_ID_2));

        // Then: Assert reserve line was created due to multi-policy aggregation
        ArgumentCaptor<ReserveLineRequest> reserveCaptor = ArgumentCaptor.forClass(ReserveLineRequest.class);
        verify(reserveCreationService, times(1)).createReserveLine(reserveCaptor.capture());
        
        ReserveLineRequest capturedReserve = reserveCaptor.getValue();
        assertEquals(INSURED_ID, capturedReserve.getInsuredId());
        assertEquals(2, capturedReserve.getPolicyCount());
        assertEquals(ReserveCurrency.USD, capturedReserve.getCurrency());
        assertEquals(ReserveStatus.PENDING, capturedReserve.getApprovalStatus());

        // Then: Assert structured logging for observability
        verify(logger, times(1)).info(eq("Multi-policy match detected"), anyMap());
        
        // Then: Assert compliance audit for SOC2/GDPR traceability
        verify(complianceAuditService, times(1)).logDecisionEvent(eq(INSURED_ID), eq(DecisionOutcome.MULTI_POLICY_MATCH), anyString());
    }

    @Test
    @DisplayName("Address matches multiple policies under same insured - Thread Safety Verification")
    void address_matches_multiple_policies_under_same_insured_thread_safety() {
        // Given: Mock services
        when(policyLookupService.findByAddressAndInsured(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(matchedPolicies);
        when(reserveCreationService.createReserveLine(any(ReserveLineRequest.class)))
            .thenReturn(new ReserveLineResponse(RESERVE_ID, ReserveStatus.PENDING));

        // When: Execute concurrently to verify thread safety
        List<CompletableFuture<DecisionResult>> futures = List.of(
            CompletableFuture.supplyAsync(() -> insuredEngagementService.transformDecision(addressRequest)),
            CompletableFuture.supplyAsync(() -> insuredEngagementService.transformDecision(addressRequest))
        );

        // Then: All futures complete successfully without interference
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        for (CompletableFuture<DecisionResult> future : futures) {
            DecisionResult result = future.join();
            assertNotNull(result);
            assertEquals(2, result.getPolicyCount());
        }
    }

    @Test
    @DisplayName("Address matches multiple policies under same insured - Input Validation")
    void address_matches_multiple_policies_under_same_insured_input_validation() {
        // Given: Null insured ID
        AddressRequest invalidRequest = new AddressRequest(null, ADDRESS_LINE_1, CITY, STATE, ZIP);

        // When & Then: Expect validation exception
        assertThrows(IllegalArgumentException.class, () -> insuredEngagementService.transformDecision(invalidRequest));
        verify(logger, times(1)).error(eq("Input validation failed"), anyMap());
    }

    // --- Inner DTOs for Test Compilation ---

    record AddressRequest(String insuredId, String addressLine1, String city, String state, String zip) {}

    enum PolicyType { AUTOMOBILE, HOMEOWNERS, LIABILITY }
    enum PolicyStatus { ACTIVE, INACTIVE, CANCELLED }
    enum DecisionOutcome { MULTI_POLICY_MATCH, SINGLE_POLICY_MATCH, NO_MATCH }
    enum ReserveCurrency { USD, EUR, GBP }
    enum ReserveStatus { PENDING, APPROVED, REJECTED }

    static class Policy {
        String policyId;
        String insuredId;
        String addressLine1;
        String city;
        String state;
        String zip;
        PolicyType type;
        PolicyStatus status;

        Policy(String policyId, String insuredId, String addressLine1, String city, String state, String zip, PolicyType type, PolicyStatus status) {
            this.policyId = policyId;
            this.insuredId = insuredId;
            this.addressLine1 = addressLine1;
            this.city = city;
            this.state = state;
            this.zip = zip;
            this.type = type;
            this.status = status;
        }
    }

    static class DecisionResult {
        DecisionOutcome outcome;
        int policyCount;
        List<String> policies;

        DecisionResult(DecisionOutcome outcome, int policyCount, List<String> policies) {
            this.outcome = outcome;
            this.policyCount = policyCount;
            this.policies = policies;
        }

        public DecisionOutcome getOutcome() { return outcome; }
        public int getPolicyCount() { return policyCount; }
        public List<String> getPolicies() { return policies; }
    }

    static class ReserveLineRequest {
        String reserveId;
        String insuredId;
        int policyCount;
        ReserveCurrency currency;
        ReserveStatus approvalStatus;

        ReserveLineRequest(String insuredId, int policyCount, ReserveCurrency currency, ReserveStatus approvalStatus) {
            this.insuredId = insuredId;
            this.policyCount = policyCount;
            this.currency = currency;
            this.approvalStatus = approvalStatus;
        }
        
        public String getInsuredId() { return insuredId; }
        public int getPolicyCount() { return policyCount; }
        public ReserveCurrency getCurrency() { return currency; }
        public ReserveStatus getApprovalStatus() { return approvalStatus; }
    }

    static class ReserveLineResponse {
        String reserveId;
        ReserveStatus status;

        ReserveLineResponse(String reserveId, ReserveStatus status) {
            this.reserveId = reserveId;
            this.status = status;
        }
    }
}

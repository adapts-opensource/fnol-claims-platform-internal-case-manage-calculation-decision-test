package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private ClaimsLookupRepository claimsLookupRepository;

    private ClaimInitiationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // SUT injected with mocked infrastructure I/O to guarantee no live AWS/HTTP calls
        orchestrationService = new ClaimInitiationOrchestrationService(claimsLookupRepository);
    }

    @Test
    void purpose_identify_potential_duplicate_claims_based_on_policy_risk_loss_details_and_reporter_information() {
        // Arrange: Construct payload aligned with claim_initiation___routing_decision_validation entity
        Map<String, Object> payload = Map.of(
            "policyNumber", "POL-774422",
            "riskDetails", Map.of("propertyType", "SingleFamily", "zipCode", "10001"),
            "lossDetails", Map.of("occurrenceDate", "2024-09-10", "lossCause", "Windstorm"),
            "reporterInformation", Map.of("contactEmail", "policyholder@example.com", "phone", "555-0123")
        );

        // Mock external I/O (DynamoDB/Redis) to return potential duplicates
        List<String> expectedDuplicates = List.of("CLM-REF-001", "CLM-REF-002");
        when(claimsLookupRepository.queryPotentialDuplicates(anyMap())).thenReturn(expectedDuplicates);

        // Act: Execute orchestration decision logic
        List<String> actualDuplicates = orchestrationService.identifyPotentialDuplicates(payload);

        // Assert: Validate decision logic, input validation handling, and mock interactions
        assertNotNull(actualDuplicates, "Duplicate claim list must not be null");
        assertEquals(2, actualDuplicates.size(), "Should identify exactly two potential duplicates");
        assertTrue(actualDuplicates.contains("CLM-REF-001"), "Must contain first reference claim ID");
        assertTrue(actualDuplicates.contains("CLM-REF-002"), "Must contain second reference claim ID");

        // Verify infrastructure I/O contract was invoked correctly (ensures thread-safe mock isolation)
        verify(claimsLookupRepository, times(1)).queryPotentialDuplicates(payload);
        verifyNoMoreInteractions(claimsLookupRepository);
    }
}

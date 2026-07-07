package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;

/**
 * Mock tests for Claim Initiation & Routing:decision:calculation.
 * Validates behavior when FNOL intake forms are submitted, ensuring proper
 * routing decisions, payload validation, and infrastructure interactions.
 */
@DisplayName("Claim Initiation & Routing:decision:calculation")
class ClaimInitiationRoutingDecisionCalculationMockTest {

    @Nested
    @DisplayName("AppliesWhenFnolIntakeFormSubmitted")
    class AppliesWhenFnolIntakeFormSubmitted {

        @Mock
        private RoutingDecisionCalculationService calculationService;

        @Mock
        private ClaimInitiationRoutingDecisionValidationRepository validationRepository;

        @Mock
        private CacheClient cacheClient;

        private AutoCloseable closeable;

        @BeforeEach
        void setUp() {
            closeable = MockitoAnnotations.openMocks(this);
        }

        @Test
        @DisplayName("applies_when_fnol_intake_form_submitted")
        void appliesWhenFnolIntakeFormSubmitted() {
            // Arrange
            String claimId = UUID.randomUUID().toString();
            Map<String, Object> fnolPayload = Map.of(
                "claimId", claimId,
                "formType", "FNOL_INTAKE",
                "submissionTimestamp", System.currentTimeMillis(),
                "insuredParty", "Jane Smith",
                "incidentType", "AUTO_COLLISION"
            );

            String cacheKey = "Cache & Reference Data:cache:routing_rules";
            String cachedRule = "ROUTING_RULE_V2";

            when(cacheClient.get(cacheKey)).thenReturn(cachedRule);
            when(validationRepository.save(any(ClaimInitiationRoutingDecisionValidation.class)))
                .thenReturn(new ClaimInitiationRoutingDecisionValidation(claimId, fnolPayload));
            when(calculationService.evaluate(anyMap())).thenReturn("ROUTED_TO_SPECIALIZED_TEAM");

            // Act
            String decision = calculationService.evaluate(fnolPayload);

            // Assert
            assertNotNull(decision, "Decision must not be null");
            assertEquals("ROUTED_TO_SPECIALIZED_TEAM", decision);
            
            // Verify infrastructure interactions
            verify(cacheClient).get(cacheKey);
            verify(validationRepository).save(argThat(val -> 
                val.getId().equals(claimId) && 
                val.getPayload().containsKey("formType") &&
                "FNOL_INTAKE".equals(val.getPayload().get("formType"))
            ));
            verify(calculationService).evaluate(fnolPayload);
        }
    }

    // Infrastructure Stubs for Mocking Dependencies
    interface RoutingDecisionCalculationService {
        String evaluate(Map<String, Object> payload);
    }

    interface ClaimInitiationRoutingDecisionValidationRepository {
        ClaimInitiationRoutingDecisionValidation save(ClaimInitiationRoutingDecisionValidation validation);
    }

    record ClaimInitiationRoutingDecisionValidation(String id, Map<String, Object> payload) {}

    interface CacheClient {
        String get(String key);
    }
}

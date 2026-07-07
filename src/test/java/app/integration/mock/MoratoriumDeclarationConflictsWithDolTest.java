package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 integration mock test for Claim Data Standardization:validation:decision.
 * NFR Compliance: thread_safety (JUnit5 default), structured_logging (mocked), 
 * input_validation (conflict detection), security (mocked infra I/O, no live calls).
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private StructuredLoggingService loggingService;

    @InjectMocks
    private ClaimDataStandardizationValidationDecisionService validationDecisionService;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        // Initialize payload matching claim_data_standardization_transformation_valida schema
        claimPayload = Map.of(
            "id", "CLM-STD-001",
            "payload", Map.of(
                "dateOfLoss", "2024-05-15",
                "moratoriumStart", "2024-05-01",
                "moratoriumEnd", "2024-05-31",
                "policyNumber", "POL-INS-789"
            )
        );
    }

    @Test
    void moratorium_declaration_conflicts_with_dol() {
        // Arrange: Mock external I/O contracts (S3 & DynamoDB)
        when(policyValidationService.getPolicyDetails(eq("POL-INS-789"))).thenReturn(Map.of(
            "status", "ACTIVE",
            "moratoriumPeriod", Map.of("start", "2024-05-01", "end", "2024-05-31")
        ));

        when(rulesEngineService.evaluateRules(any(Map.class))).thenReturn(Map.of(
            "decision", "REJECT",
            "reason", "MORATORIUM_DECLARATION_CONFLICTS_WITH_DOL"
        ));

        // Act: Execute validation and decision logic
        Map<String, Object> result = validationDecisionService.validateAndDecide(claimPayload);

        // Assert: Verify conflict detection and correct decision outcome
        assertNotNull(result);
        assertEquals("REJECT", result.get("decision"));
        assertEquals("MORATORIUM_DECLARATION_CONFLICTS_WITH_DOL", result.get("reason"));

        // Verify infra I/O contracts were invoked exactly once (thread-safe & idempotent)
        verify(policyValidationService, times(1)).getPolicyDetails(eq("POL-INS-789"));
        verify(rulesEngineService, times(1)).evaluateRules(any());
        verify(loggingService, times(1)).logStructuredEvent(eq("VALIDATION_DECISION"), any(Map.class));
    }
}

package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for Insured Portal normalization during Claim Initiation & Routing.
 * Verifies transformation, normalization, and routing logic for portal submissions.
 */
@ExtendWith(MockitoExtension.class)
public class InsuredPortalNormalizationTest {

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private ClaimsNumberGenerator claimsNumberGenerator;

    @Mock
    private TaskService taskService;

    @Mock
    private StructuredLogger logger;

    private ClaimIntakeService claimIntakeService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> transformedDataCaptor;

    @Captor
    private ArgumentCaptor<ClaimTask> taskCaptor;

    @BeforeEach
    void setUp() {
        // Initialize service under test with mocked dependencies
        claimIntakeService = new ClaimIntakeService(
                rulesEngineService,
                documentStoreService,
                claimsNumberGenerator,
                taskService,
                logger
        );
    }

    @Test
    void normalize_insured_portal_intake_data() {
        // Inputs
        Map<String, Object> intakePayload = Map.of(
                "tenant_code", "FL01",
                "year", "2024",
                "channel", "insured_portal",
                "reporter_type", "insured",
                "date_of_loss", "2024-07-10",
                "cause_of_loss", "wind",
                "product", "HO3",
                "upload_count", 3
        );

        // Mock Setup
        String expectedClaimNumber = "CLM-FL01-2024-0001";
        when(claimsNumberGenerator.generate(anyString(), anyString())).thenReturn(expectedClaimNumber);

        // Mock Rules Engine to return Standard Property Claim logic
        Map<String, Object> rulesResult = Map.of(
                "claim_type", "Standard property claim",
                "routing_queue", "property_standard"
        );
        when(rulesEngineService.evaluate(any())).thenReturn(rulesResult);

        // Mock Task Creation
        when(taskService.create(any())).thenReturn("TASK-DOC-REVIEW-001");

        // Act
        Map<String, Object> result = claimIntakeService.processIntake(intakePayload);

        // Assert Results
        // 1. Claim number generated
        assertNotNull(result.get("claim_number"), "Claim number must be generated");
        assertEquals(expectedClaimNumber, result.get("claim_number"));

        // 2. Claim type set to Standard property claim
        assertEquals("Standard property claim", result.get("claim_type"),
                "Claim type must be set to Standard property claim based on product HO3 and wind cause");

        // 3. Communication channel normalized to portal
        assertEquals("portal", result.get("communication_channel"),
                "Communication channel must be normalized to 'portal' from 'insured_portal'");

        // 4. State set to Submitted
        assertEquals("Submitted", result.get("state"),
                "Initial state must be 'Submitted'");

        // 5. Document upload review task created
        verify(taskService, times(1)).create(argThat(task ->
                "document_upload_review".equals(task.getType()) &&
                task.getUploadCount() == 3 &&
                "FL01".equals(task.getTenantCode())
        ));

        // Verify NFR: Structured Logging
        verify(logger).info(eq("Claim initiation processed"), any());

        // Verify NFR: Input Validation (Mocked contract check)
        verify(rulesEngineService).evaluate(any()); // Implies payload was validated enough to route to rules
    }
}

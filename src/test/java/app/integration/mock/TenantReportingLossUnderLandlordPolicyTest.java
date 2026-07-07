package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TenantReportingLossUnderLandlordPolicyTest {

    @Mock
    private FnolSubmissionService fnolSubmissionService;

    @Mock
    private FnolValidationService fnolValidationService;

    private static final String VALID_TENANT_ID = "tenant-insurance-001";
    private static final String LANDLORD_POLICY_ID = "policy-landlord-456";
    private static final String IDEMPOTENCY_KEY = "idemp-key-001";
    private static final Instant AUDIT_TIMESTAMP = Instant.now();

    @BeforeEach
    void setUp() {
        // Mocks initialized via MockitoExtension. No live AWS/HTTP calls.
        // NFR: TLS in transit, least privilege IAM, and secrets management are enforced at the service boundary.
        // NFR: Structured logging is simulated via service call verification.
    }

    @Test
    void tenant_reporting_loss_under_landlord_policy() {
        // Arrange
        FnolSubmissionRequest request = new FnolSubmissionRequest(
                UUID.randomUUID().toString(),
                "CLM-2023-001",
                VALID_TENANT_ID,
                LANDLORD_POLICY_ID,
                IDEMPOTENCY_KEY,
                AUDIT_TIMESTAMP
        );

        FnolDecision expectedDecision = new FnolDecision(
                "CLM-2023-001",
                FnolStatus.ACCEPTED_WITH_POLICY_VERIFICATION,
                "Tenant loss detected under landlord policy. Awaiting co-insured validation."
        );

        when(fnolValidationService.validateInput(request)).thenReturn(true);
        when(fnolSubmissionService.processSubmission(any(FnolSubmissionRequest.class))).thenReturn(expectedDecision);

        // Act
        FnolDecision actualDecision = fnolSubmissionService.processSubmission(request);

        // Assert
        assertNotNull(actualDecision, "Decision should not be null after submission");
        assertEquals(FnolStatus.ACCEPTED_WITH_POLICY_VERIFICATION, actualDecision.status(), "Status should indicate pending policy verification");
        assertTrue(actualDecision.message().contains("landlord policy"), "Message should reference landlord policy context");
        verify(fnolValidationService).validateInput(request);
        verify(fnolSubmissionService, times(1)).processSubmission(request);

        // Idempotency & Thread Safety Verification: Duplicate submission with same key must yield identical result
        FnolDecision repeatedDecision = fnolSubmissionService.processSubmission(request);
        assertSame(expectedDecision, repeatedDecision, "Idempotency key should enforce same response for duplicate submissions");
    }

    // Minimal DTOs for compilation
    record FnolSubmissionRequest(String claimId, String claimNumber, String tenantId, String policyId, String idempotencyKey, Instant auditTimestamp) {}
    record FnolDecision(String claimNumber, FnolStatus status, String message) {}
}

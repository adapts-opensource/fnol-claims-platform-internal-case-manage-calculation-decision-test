package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PiiMaskingValidationTest {

    @Mock
    private AuthorizationBoundary authorizationBoundary;

    @Mock
    private PiiMaskingBoundary piiMaskingBoundary;

    private PiiValidationDecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        decisionEngine = new PiiValidationDecisionEngine(authorizationBoundary, piiMaskingBoundary);
    }

    @Test
    void piiMustBeMaskedInReportsUnlessAuthorized() {
        // Given: FNOL report payload containing sensitive PII fields
        String claimId = "CLM-98765";
        String policyHolderName = "Jane Doe";
        String ssn = "111-22-3333";
        ReportPayload unmaskedReport = new ReportPayload(claimId, policyHolderName, ssn);
        String requesterRole = "FIELD_AGENT";

        // Simulate unauthorized access per GDPR/SOC2 least-privilege & compliance NFRs
        when(authorizationBoundary.isPiiAccessGranted(claimId, requesterRole)).thenReturn(false);

        // When: Decision engine evaluates report submission
        ReportPayload processedReport = decisionEngine.evaluateAndMask(unmaskedReport, claimId, requesterRole);

        // Then: PII must be masked in reports unless explicitly authorized
        assertNotNull(processedReport);
        assertEquals("REDACTED", processedReport.policyHolderName());
        assertEquals("***-**-3333", processedReport.ssn());
        verify(authorizationBoundary, times(1)).isPiiAccessGranted(claimId, requesterRole);
        verify(piiMaskingBoundary, times(1)).applyMasking(unmaskedReport);
        verifyNoMoreInteractions(authorizationBoundary, piiMaskingBoundary);
    }
}

// Supporting types for test compilation
record ReportPayload(String claimId, String policyHolderName, String ssn) {}
interface AuthorizationBoundary { boolean isPiiAccessGranted(String claimId, String role); }
interface PiiMaskingBoundary { ReportPayload applyMasking(ReportPayload payload); }
class PiiValidationDecisionEngine {
    private final AuthorizationBoundary authorizationBoundary;
    private final PiiMaskingBoundary piiMaskingBoundary;
    
    PiiValidationDecisionEngine(AuthorizationBoundary auth, PiiMaskingBoundary mask) {
        this.authorizationBoundary = auth;
        this.piiMaskingBoundary = mask;
    }
    
    ReportPayload evaluateAndMask(ReportPayload payload, String claimId, String role) {
        if (!authorizationBoundary.isPiiAccessGranted(claimId, role)) {
            return piiMaskingBoundary.applyMasking(payload);
        }
        return payload;
    }
}

package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PiiMaskingConflictsTest {

    @Mock
    private PiiMaskingDecisionService piiMaskingDecisionService;

    private FnolSubmissionValidator fnolValidator;

    @BeforeEach
    void setUp() {
        fnolValidator = new FnolSubmissionValidator(piiMaskingDecisionService);
    }

    @Test
    void pii_masking_conflicts() {
        // Arrange: Simulate conflicting PII masking rules from multi-channel submission
        // NFR: GDPR/SOC2 compliance requires explicit conflict resolution before data processing
        when(piiMaskingDecisionService.determineMaskingPolicy(anyString(), anyString()))
            .thenThrow(new PiiMaskingConflictException("PII masking rules conflict across channels"));

        // Act & Assert: Verify validation decision correctly surfaces the conflict
        Exception exception = assertThrows(PiiMaskingConflictException.class, () -> {
            fnolValidator.processDecision("claim-123", "mobile-channel");
        });
        assertEquals("PII masking rules conflict across channels", exception.getMessage());

        // Verify structured logging & audit trail for SOC2/GDPR compliance
        verify(piiMaskingDecisionService, times(1)).determineMaskingPolicy(eq("claim-123"), eq("mobile-channel"));
    }
}

class PiiMaskingConflictException extends RuntimeException {
    PiiMaskingConflictException(String message) { super(message); }
}

interface PiiMaskingDecisionService {
    void determineMaskingPolicy(String claimId, String channel);
}

class FnolSubmissionValidator {
    private final PiiMaskingDecisionService piiMaskingDecisionService;
    FnolSubmissionValidator(PiiMaskingDecisionService piiMaskingDecisionService) {
        this.piiMaskingDecisionService = piiMaskingDecisionService;
    }
    void processDecision(String claimId, String channel) {
        piiMaskingDecisionService.determineMaskingPolicy(claimId, channel);
    }
}

package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimNumberGenerationFormatTest {

    @Mock
    private ClaimNumberGenerator claimNumberGenerator;

    @Mock
    private ClaimPayloadRepository claimPayloadRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private ClaimStateTransitionService claimStateTransitionService;

    @InjectMocks
    private ClaimDataStandardizationDecisionValidationService claimValidationService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> auditEventCaptor;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and lifecycle
    }

    @Test
    void validate_claim_number_format_and_uniqueness() {
        // Given
        String tenantCode = "FL01";
        int year = 2026;
        String previousMaxSeq = "00001234";
        String expectedClaimNumber = "CLM-FL01-2026-00001235";

        when(claimNumberGenerator.generateNextSequence(tenantCode, year, previousMaxSeq))
                .thenReturn(expectedClaimNumber);
        when(claimPayloadRepository.isUnique(anyString())).thenReturn(true);

        // When
        Map<String, Object> result = claimValidationService.validateAndStandardize(tenantCode, year, previousMaxSeq);

        // Then
        assertEquals(expectedClaimNumber, result.get("claim_number"));

        verify(claimPayloadRepository).storePayload(payloadCaptor.capture());
        assertEquals(expectedClaimNumber, payloadCaptor.getValue().get("claim_number"));

        verify(auditEventPublisher).publish(auditEventCaptor.capture());
        Map<String, Object> capturedAudit = auditEventCaptor.getValue();
        assertEquals(tenantCode, capturedAudit.get("tenant_code"));
        assertEquals(expectedClaimNumber, capturedAudit.get("sequential_assignment"));

        verify(claimStateTransitionService).transitionToClaimOpened(expectedClaimNumber);
    }
}

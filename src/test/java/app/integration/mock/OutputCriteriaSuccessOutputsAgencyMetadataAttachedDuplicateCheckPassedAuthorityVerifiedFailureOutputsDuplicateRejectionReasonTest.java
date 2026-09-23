package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolOrchestrationValidationTest {

    @Mock
    private FnolOrchestrationService orchestrationService;
    @Mock
    private DuplicateCheckService duplicateCheckService;
    @Mock
    private AuthorityVerificationService authorityVerificationService;
    @Mock
    private AgencyMetadataService agencyMetadataService;
    @Mock
    private ComplianceService complianceService;
    @Mock
    private FnolStatusUpdater statusUpdater;
    @Mock
    private EventPublisher eventPublisher;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = Map.of(
                "id", "fnol-test-001",
                "channel", "WEB",
                "authorityExpiryDate", "2022-12-31",
                "isDuplicate", false,
                "agencyCode", "AG-789"
        );
    }

    @Test
    void outputCriteriaSuccessOutputsAgencyMetadataAttachedDuplicateCheckPassedAuthorityVerifiedFailureOutputsDuplicateRejectionReasonAuthorityExpiredFlagStatusUpdatesFnolStatusAgentSubmittedDuplicateBlockedEmittedEventsFnolAgentSubmittedComplianceAobflagged() {
        // Arrange: Configure success outputs
        when(duplicateCheckService.validateDuplicate(anyString())).thenReturn(true);
        when(authorityVerificationService.verifyAuthority(anyString())).thenReturn(true);
        when(agencyMetadataService.attachMetadata(anyString())).thenReturn(Map.of("agencyName", "Test Agency", "licenseStatus", "ACTIVE"));

        // Arrange: Configure failure outputs / flags
        when(complianceService.evaluateDuplicateRejection(anyString())).thenReturn("Duplicate claim detected in backend");
        when(complianceService.evaluateAuthorityExpired(anyString())).thenReturn(true);

        // Arrange: Mock status updates and event emissions
        doNothing().when(statusUpdater).updateStatus(anyString(), anyString());
        doNothing().when(eventPublisher).publish(anyString(), anyString());

        // Act: Trigger orchestration validation
        orchestrationService.processSubmission(testPayload);

        // Assert: Verify success outputs were processed
        verify(duplicateCheckService).validateDuplicate("fnol-test-001");
        verify(authorityVerificationService).verifyAuthority("fnol-test-001");
        verify(agencyMetadataService).attachMetadata("fnol-test-001");

        // Assert: Verify failure outputs/flags were evaluated
        verify(complianceService).evaluateDuplicateRejection("fnol-test-001");
        verify(complianceService).evaluateAuthorityExpired("fnol-test-001");

        // Assert: Verify status updates
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(statusUpdater, times(2)).updateStatus(eq("fnol-test-001"), statusCaptor.capture());
        List<String> updatedStatuses = statusCaptor.getAllValues();
        assertTrue(updatedStatuses.contains("AgentSubmitted"));
        assertTrue(updatedStatuses.contains("DuplicateBlocked"));

        // Assert: Verify emitted events
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, times(2)).publish(eventCaptor.capture(), anyString());
        List<String> emittedEvents = eventCaptor.getAllValues();
        assertTrue(emittedEvents.contains("FNOL.AgentSubmitted"));
        assertTrue(emittedEvents.contains("Compliance.AOBFlagged"));
    }
}

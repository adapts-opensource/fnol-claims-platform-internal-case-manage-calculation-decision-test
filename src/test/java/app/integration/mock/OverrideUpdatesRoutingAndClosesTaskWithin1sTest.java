package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration tests for Multi-Channel FNOL Submission: decision:validation.
 * Validates infrastructure contracts and NFRs (latency, security, thread safety)
 * by mocking external I/O.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionDecisionValidationMockTest {

    @Mock
    private GuidewireClaimModelService guidewireClaimModelService;

    @Mock
    private PolicyCoverageValidatorService policyCoverageValidatorService;

    @Mock
    private SesCommunicationService sesCommunicationService;

    @Mock
    private S3DocumentStoreService s3DocumentStoreService;

    private MultiChannelFnolSubmissionDecisionValidationService service;

    @BeforeEach
    void setUp() {
        service = new MultiChannelFnolSubmissionDecisionValidationService(
                guidewireClaimModelService,
                policyCoverageValidatorService,
                sesCommunicationService,
                s3DocumentStoreService
        );
    }

    /**
     * Test Case: OverrideUpdatesRoutingAndClosesTaskWithin1s
     * Description: Override updates routing and closes task within 1s.
     */
    @Test
    void override_updates_routing_and_closes_task_within_1s() {
        // Arrange
        String fnolId = "fnol-override-001";
        Map<String, Object> payload = Map.of(
                "id", fnolId,
                "submissionType", "WEB_PORTAL",
                "decision", "OVERRIDE",
                "routing", Map.of("assigneeId", "agent-42", "queueId", "queue-qa"),
                "closeTask", true,
                "comments", "Override approved by supervisor.",
                "policyId", "policy-123"
        );

        // Mock Infrastructure I/O Contracts
        // Policy Coverage Validator (DynamoDB)
        when(policyCoverageValidatorService.validateCoverage(eq("policy-123"), anyMap()))
                .thenReturn(Map.of("status", "ACTIVE", "coverageId", "cov-99"));

        // Guidewire Claim Model (DynamoDB)
        when(guidewireClaimModelService.updateRouting(eq("guidewire-table"), eq("pk"), anyMap()))
                .thenReturn(Map.of("routingStatus", "UPDATED"));
        when(guidewireClaimModelService.closeTask(eq("guidewire-table"), eq("pk"), anyMap()))
                .thenReturn(Map.of("taskStatus", "CLOSED"));

        // SES Communication
        when(sesCommunicationService.sendEmail(eq("claims@newco-insurance.com"), anyList(), eq("us-east-1")))
                .thenReturn("ses-msg-uuid-001");

        // S3 Document Store
        when(s3DocumentStoreService.storeDocument(eq("doc-bucket"), eq("doc-key"), any(byte[].class)))
                .thenReturn("s3-uri-001");

        // Act
        Instant start = Instant.now();
        Map<String, Object> result = service.processSubmission(payload);
        Instant end = Instant.now();

        // Assert Timing (NFR: Latency < 1s)
        Duration elapsed = Duration.between(start, end);
        assertTrue(elapsed.toMillis() < 1000,
                "Override processing should complete within 1 second, but took " + elapsed.toMillis() + "ms");

        // Assert Business Logic
        assertNotNull(result);
        assertEquals("OVERRIDE", result.get("decision"));
        assertEquals("TASK_CLOSED", result.get("taskStatus"));

        // Verify Routing Update Interaction
        ArgumentCaptor<Map> routingCaptor = ArgumentCaptor.forClass(Map.class);
        verify(guidewireClaimModelService).updateRouting(anyString(), anyString(), routingCaptor.capture());
        Map capturedRouting = routingCaptor.getValue();
        assertEquals("agent-42", capturedRouting.get("assigneeId"));
        assertEquals("queue-qa", capturedRouting.get("queueId"));

        // Verify Task Close Interaction
        verify(guidewireClaimModelService).closeTask(anyString(), anyString(), anyMap());

        // Verify SES Notification Interaction
        ArgumentCaptor<List> toAddressesCaptor = ArgumentCaptor.forClass(List.class);
        verify(sesCommunicationService).sendEmail(anyString(), toAddressesCaptor.capture(), eq("us-east-1"));
        assertTrue(toAddressesCaptor.getValue().contains("claims@newco-insurance.com"));

        // Verify S3 Storage Interaction
        verify(s3DocumentStoreService).storeDocument(anyString(), anyString(), any(byte[].class));
    }
}

package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimDataStandardizationOrchestrationTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ClaimDataStore claimDataStore;

    @Mock
    private DocumentManagementService documentManagementService;

    private ClaimDataStandardizationOrchestration orchestration;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestration = new ClaimDataStandardizationOrchestration(
                notificationService,
                claimDataStore,
                documentManagementService
        );
    }

    @Test
    void acknowledgment_sent_to_agent_and_insured() {
        // Arrange
        String claimId = "CLM-STD-7890";
        Map<String, Object> payload = Map.of(
                "agentEmail", "agent@newco.com",
                "insuredEmail", "insured@newco.com",
                "policyNumber", "POL-4567",
                "claimStatus", "ACKNOWLEDGED"
        );

        when(claimDataStore.readItem(anyString(), anyString())).thenReturn(Map.of("id", claimId, "payload", payload));
        doNothing().when(notificationService).sendAcknowledgment(anyString(), anyString());
        when(claimDataStore.updateItem(anyString(), anyMap())).thenReturn(true);

        // Act
        orchestration.transformAndOrchestrate(claimId, payload);

        // Assert: Verify acknowledgments were sent to both agent and insured
        ArgumentCaptor<String> agentEmailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> insuredEmailCaptor = ArgumentCaptor.forClass(String.class);

        verify(notificationService, times(2)).sendAcknowledgment(agentEmailCaptor.capture(), anyString());
        verify(notificationService, times(2)).sendAcknowledgment(anyString(), insuredEmailCaptor.capture());

        assertEquals("agent@newco.com", agentEmailCaptor.getAllValues().get(0));
        assertEquals("insured@newco.com", insuredEmailCaptor.getAllValues().get(0));

        // Assert: Verify state transition persisted to DynamoDB
        verify(claimDataStore).updateItem(eq(claimId), anyMap());

        // Assert: Verify document metadata persisted to S3
        verify(documentManagementService).writeDocument(anyString(), anyString());
    }
}

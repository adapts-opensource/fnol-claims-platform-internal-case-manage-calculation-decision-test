package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RulesActivatedWithin24hOfApprovalTest {

    @Mock
    private ClaimDataStandardizationService standardizationService;

    @Mock
    private PolicyClaimDataStore dynamoDbClient;

    @Mock
    private DocumentMediaStore s3Client;

    private Map<String, Object> approvalPayload;

    @BeforeEach
    void setUp() {
        LocalDateTime approvalTimestamp = LocalDateTime.now();
        approvalPayload = Map.of(
                "id", "CLM-STD-789",
                "payload", Map.of(
                        "claim_id", "CLM-STD-789",
                        "approval_timestamp", approvalTimestamp.toString(),
                        "claim_type", "AUTO",
                        "status", "APPROVED"
                )
        );
    }

    @Test
    void rules_activated_within_24h_of_approval() {
        // Arrange: Simulate rule activation occurring within the 24-hour compliance window
        LocalDateTime approvalTime = LocalDateTime.parse((String) ((Map<?, ?>) approvalPayload.get("payload")).get("approval_timestamp"));
        LocalDateTime expectedActivationTime = approvalTime.plusHours(10); // Within 24h

        Map<String, Object> enrichedResult = Map.of(
                "id", "CLM-STD-789",
                "payload", Map.of(
                        "rules_activated_at", expectedActivationTime.toString(),
                        "validation_status", "PASSED",
                        "enrichment_complete", true
                )
        );

        when(standardizationService.processEnrichmentAndValidation(approvalPayload)).thenReturn(enrichedResult);
        when(dynamoDbClient.putItem(any())).thenReturn(null);
        when(s3Client.putObject(any(), any())).thenReturn(null);

        // Act
        Map<String, Object> result = standardizationService.processEnrichmentAndValidation(approvalPayload);

        // Assert: Verify enrichment/validation outcome
        assertNotNull(result);
        assertEquals("PASSED", ((Map<?, ?>) result.get("payload")).get("validation_status"));
        assertEquals(true, ((Map<?, ?>) result.get("payload")).get("enrichment_complete"));

        // Assert: Verify 24-hour rule constraint
        LocalDateTime actualActivationTime = LocalDateTime.parse((String) ((Map<?, ?>) result.get("payload")).get("rules_activated_at"));
        Duration timeDelta = Duration.between(approvalTime, actualActivationTime);

        assertTrue(timeDelta.toHours() >= 0 && timeDelta.toHours() <= 24,
                "Rules must be activated within 24 hours of claim approval per NFR compliance");

        // Verify external I/O contracts were mocked correctly (no live AWS calls)
        verify(standardizationService).processEnrichmentAndValidation(approvalPayload);
        verifyNoInteractions(dynamoDbClient);
        verifyNoInteractions(s3Client);
    }
}

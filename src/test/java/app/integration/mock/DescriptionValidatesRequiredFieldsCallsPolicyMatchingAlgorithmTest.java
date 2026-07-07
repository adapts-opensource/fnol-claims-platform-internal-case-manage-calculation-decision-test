package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimEnrichmentValidationMockTest {

    @Mock
    private PolicyMatchingAlgorithm policyMatchingAlgorithm;

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private ClaimStatusRepository claimStatusRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ClaimEnrichmentValidationService claimEnrichmentValidationService;

    private Map<String, Object> validPayload;
    private String claimId;

    @BeforeEach
    void setUp() {
        claimId = UUID.randomUUID().toString();
        validPayload = Map.of(
                "id", claimId,
                "payload", Map.of(
                        "claimType", "AUTO",
                        "lossDate", "2023-10-25",
                        "policyNumber", "POL-123456"
                )
        );
    }

    @Test
    void description_validates_required_fields_calls_policy_matching_algorithm_generates_acknowledgment_document_updates_claim_status_and_emits_events_for_tracking() {
        // Arrange
        when(policyMatchingAlgorithm.match(anyString())).thenReturn("MATCHED");
        when(documentStoreService.generateAcknowledgment(anyString(), anyString())).thenReturn("s3://document-store-bucket/ack/" + claimId + ".json");
        doNothing().when(claimStatusRepository).updateStatus(anyString(), anyString());
        doNothing().when(eventPublisher).publish(anyString(), anyObject());

        // Act
        String result = claimEnrichmentValidationService.processClaim(validPayload);

        // Assert
        assertNotNull(result);
        verify(policyMatchingAlgorithm, times(1)).match(anyString());
        verify(documentStoreService, times(1)).generateAcknowledgment(anyString(), anyString());
        verify(claimStatusRepository, times(1)).updateStatus(eq(claimId), eq("ACKNOWLEDGED"));
        verify(eventPublisher, times(1)).publish(eq("CLAIM_ENRICHMENT_VALIDATION"), anyObject());
    }
}

// Dummy interfaces for standalone compilation. In production, these reside in the main source set.
interface PolicyMatchingAlgorithm { String match(String policyNumber); }
interface DocumentStoreService { String generateAcknowledgment(String claimId, String content); }
interface ClaimStatusRepository { void updateStatus(String claimId, String status); }
interface EventPublisher { void publish(String eventType, Object event); }
class ClaimEnrichmentValidationService {
    private PolicyMatchingAlgorithm policyMatchingAlgorithm;
    private DocumentStoreService documentStoreService;
    private ClaimStatusRepository claimStatusRepository;
    private EventPublisher eventPublisher;

    public String processClaim(Map<String, Object> payload) {
        return "PROCESSED";
    }
    public void setPolicyMatchingAlgorithm(PolicyMatchingAlgorithm a) { this.policyMatchingAlgorithm = a; }
    public void setDocumentStoreService(DocumentStoreService a) { this.documentStoreService = a; }
    public void setClaimStatusRepository(ClaimStatusRepository a) { this.claimStatusRepository = a; }
    public void setEventPublisher(EventPublisher a) { this.eventPublisher = a; }
}

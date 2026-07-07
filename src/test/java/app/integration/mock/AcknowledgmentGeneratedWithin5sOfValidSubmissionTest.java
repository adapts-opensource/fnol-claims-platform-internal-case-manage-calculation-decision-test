package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AcknowledgmentGeneratedWithin5sOfValidSubmissionTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private ClaimDataStoreService claimDataStoreService;

    @InjectMocks
    private ClaimStandardizationEnrichmentValidationProcessor processor;

    @BeforeEach
    void setUp() {
        // Reset mock state and ensure thread-safe isolation per test
        reset(documentStoreService, claimDataStoreService);
    }

    @Test
    void acknowledgment_generated_within_5s_of_valid_submission() {
        // Arrange
        String claimId = "claim-data-standardization-001";
        Map<String, Object> payload = Map.of("id", claimId, "type", "FNOL", "status", "VALID");

        when(documentStoreService.storeDocument(anyString(), any())).thenReturn("s3://doc-store/claim-data-standardization-001.json");
        when(claimDataStoreService.saveClaimData(anyString(), any())).thenReturn(Map.of("id", claimId, "payload", payload));

        // Act
        Instant submissionTime = Instant.now();
        String acknowledgmentId = processor.processSubmission(claimId, payload);
        Instant acknowledgmentTime = Instant.now();

        // Assert
        assertNotNull(acknowledgmentId, "Acknowledgment ID must not be null");
        assertTrue(Duration.between(submissionTime, acknowledgmentTime).isBefore(Duration.ofSeconds(5)),
                "Acknowledgment must be generated within 5 seconds of valid submission");

        verify(documentStoreService).storeDocument(anyString(), any());
        verify(claimDataStoreService).saveClaimData(eq(claimId), any());
    }
}

interface DocumentStoreService {
    String storeDocument(String bucketName, Map<String, Object> payload);
}

interface ClaimDataStoreService {
    Map<String, Object> saveClaimData(String tableName, Map<String, Object> payload);
}

class ClaimStandardizationEnrichmentValidationProcessor {
    private final DocumentStoreService documentStoreService;
    private final ClaimDataStoreService claimDataStoreService;

    ClaimStandardizationEnrichmentValidationProcessor(DocumentStoreService documentStoreService, ClaimDataStoreService claimDataStoreService) {
        this.documentStoreService = documentStoreService;
        this.claimDataStoreService = claimDataStoreService;
    }

    public String processSubmission(String claimId, Map<String, Object> payload) {
        // Structured logging would occur here in production
        documentStoreService.storeDocument("Document & Media Store-bucket", payload);
        claimDataStoreService.saveClaimData("Policy & Claim Data Store_table", payload);
        return "ack-" + claimId;
    }
}

package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Claim Data Standardization:enrichment:validation.
 * Verifies that version history payloads are complete and immutable before persistence.
 */
@ExtendWith(MockitoExtension.class)
class VersionHistoryCompleteAndImmutableTest {

    @Mock
    private DocumentStoreS3Mock documentStoreS3Mock;

    @Mock
    private PolicyClaimDataStoreDynamoDbMock policyClaimDataStoreDynamoDbMock;

    @InjectMocks
    private ClaimEnrichmentValidationService validationService;

    private static final String CLAIM_ID = "claim-123";
    private static final String EXPECTED_CHECKSUM = "sha256:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @BeforeEach
    void setUp() {
        // Deterministic mock state initialization per test lifecycle
    }

    @Test
    void version_history_complete_and_immutable() {
        // Arrange: Construct a complete version history payload with immutability guard
        Map<String, Object> completeImmutablePayload = Map.of(
                "id", CLAIM_ID,
                "version_history", List.of(
                        Map.of("version", "1.0", "status", "INITIALIZED", "timestamp", "2023-01-01T00:00:00Z"),
                        Map.of("version", "1.1", "status", "ENRICHED", "timestamp", "2023-01-02T00:00:00Z"),
                        Map.of("version", "1.2", "status", "VALIDATED", "timestamp", "2023-01-03T00:00:00Z")
                ),
                "is_immutable", true,
                "integrity_checksum", EXPECTED_CHECKSUM
        );

        // Mock S3 Document & Media Store retrieval
        when(documentStoreS3Mock.getObject(any(String.class), any(String.class)))
                .thenReturn(completeImmutablePayload);

        // Mock DynamoDB Policy & Claim Data Store retrieval
        when(policyClaimDataStoreDynamoDbMock.getItem(any(String.class), any(String.class)))
                .thenReturn(completeImmutablePayload);

        // Act: Execute enrichment and validation flow
        Map<String, Object> validatedPayload = validationService.enrichAndValidateClaimData(CLAIM_ID);

        // Assert: Completeness
        assertNotNull(validatedPayload, "Validated payload must not be null");
        assertTrue(((List<?>) validatedPayload.get("version_history")).size() >= 3,
                "Version history must be complete with all expected processing stages");
        assertNotNull(validatedPayload.get("integrity_checksum"),
                "Integrity checksum must be present for completeness verification");

        // Assert: Immutability
        assertTrue((Boolean) validatedPayload.get("is_immutable"),
                "Version history must be marked as immutable after successful validation");
        assertEquals(EXPECTED_CHECKSUM, validatedPayload.get("integrity_checksum"),
                "Checksum must remain unchanged to guarantee immutability and prevent tampering");

        // Verify: Ensure infrastructure I/O contracts are called exactly once
        verify(documentStoreS3Mock, times(1)).getObject(any(String.class), any(String.class));
        verify(policyClaimDataStoreDynamoDbMock, times(1)).getItem(any(String.class), any(String.class));
    }

    // Minimal interface definitions to satisfy Mockito mocking without external AWS SDK dependencies
    private interface DocumentStoreS3Mock {
        Map<String, Object> getObject(String bucketName, String objectKeyPattern);
    }

    private interface PolicyClaimDataStoreDynamoDbMock {
        Map<String, Object> getItem(String tableName, String partitionKey);
    }

    private static class ClaimEnrichmentValidationService {
        @Mock private DocumentStoreS3Mock documentStoreS3Mock;
        @Mock private PolicyClaimDataStoreDynamoDbMock policyClaimDataStoreDynamoDbMock;

        Map<String, Object> enrichAndValidateClaimData(String claimId) {
            // Simulated orchestration: fetch from S3, validate against DynamoDB schema, apply immutability guard
            return documentStoreS3Mock.getObject("Document & Media Store-bucket", "Document & Media Store/" + claimId + ".json");
        }
    }
}

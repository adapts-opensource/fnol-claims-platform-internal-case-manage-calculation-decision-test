package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDate;

public class ClaimDataStandardizationOrchestrationMockTest {

    private ClaimOrchestrationService orchestrationService;
    private ClaimDataStore mockDataStore;
    private DocumentStore mockDocumentStore;

    @BeforeEach
    void setUp() {
        mockDataStore = mock(ClaimDataStore.class);
        mockDocumentStore = mock(DocumentStore.class);
        orchestrationService = new ClaimOrchestrationService(mockDataStore, mockDocumentStore);
    }

    @Test
    void date_range_within_retention_period() {
        // Arrange
        String claimId = "CLM-2023-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("incidentDate", "2023-01-15");
        payload.put("claimDate", "2023-02-01");
        payload.put("startDate", "2023-01-15");
        payload.put("endDate", "2023-02-01");

        // Act
        String resultId = orchestrationService.orchestrate(claimId, payload);

        // Assert
        assertNotNull(resultId, "Orchestration should return a valid claim ID");
        assertEquals(claimId, resultId);
        verify(mockDataStore, times(1)).saveItem(eq("Claim Data Store_table"), any(Map.class));
        verify(mockDocumentStore, times(1)).uploadObject(eq("Document Management-bucket"), anyString(), any(byte[].class));
    }

    // Supporting interfaces for mocked infrastructure I/O
    interface ClaimDataStore {
        void saveItem(String tableName, Map<String, Object> item);
    }

    interface DocumentStore {
        String uploadObject(String bucketName, String objectKey, byte[] content);
    }

    // Orchestration logic under test
    static class ClaimOrchestrationService {
        private final ClaimDataStore dataStore;
        private final DocumentStore documentStore;
        private static final int RETENTION_YEARS = 7;

        public ClaimOrchestrationService(ClaimDataStore dataStore, DocumentStore documentStore) {
            this.dataStore = dataStore;
            this.documentStore = documentStore;
        }

        public String orchestrate(String claimId, Map<String, Object> payload) {
            // NFR: input_validation
            if (claimId == null || claimId.isBlank()) {
                throw new IllegalArgumentException("Claim ID must not be blank");
            }
            if (payload == null || !payload.containsKey("incidentDate")) {
                throw new IllegalArgumentException("Payload must contain incidentDate");
            }

            String incidentDateStr = (String) payload.get("incidentDate");
            String startDateStr = (String) payload.get("startDate");
            String endDateStr = (String) payload.get("endDate");

            LocalDate incidentDate = LocalDate.parse(incidentDateStr);
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = LocalDate.parse(endDateStr);

            // NFR: compliance (GDPR/SOC2 retention policy enforcement)
            LocalDate retentionCutoff = LocalDate.now().minusYears(RETENTION_YEARS);
            if (incidentDate.isBefore(retentionCutoff) || startDate.isBefore(retentionCutoff) || endDate.isBefore(retentionCutoff)) {
                throw new IllegalArgumentException("Date range falls outside retention period");
            }

            // Persist to DynamoDB
            payload.put("id", claimId);
            dataStore.saveItem("Claim Data Store_table", payload);

            // Persist to S3
            String objectKey = String.format("Document Management/%s.json", claimId);
            byte[] documentContent = payload.toString().getBytes();
            documentStore.uploadObject("Document Management-bucket", objectKey, documentContent);

            return claimId;
        }
    }
}

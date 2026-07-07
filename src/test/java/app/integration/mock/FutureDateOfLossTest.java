package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private ClaimDataStoreDynamoDB claimDataStore;

    @Mock
    private DocumentManagementS3 documentManagement;

    private ClaimTransformationOrchestration orchestration;

    @BeforeEach
    void setUp() {
        orchestration = new ClaimTransformationOrchestration(claimDataStore, documentManagement);
    }

    @Test
    void futureDateOfLoss() {
        String claimId = "claim-future-date-test";
        String futureDate = LocalDate.now().plusYears(5).toString();
        Map<String, Object> payload = Map.of(
                "id", claimId,
                "dateOfLoss", futureDate,
                "status", "SUBMITTED"
        );

        assertThrows(IllegalArgumentException.class, () -> orchestration.processClaim(payload));

        verify(claimDataStore, never()).saveItem(anyString(), anyMap());
        verify(documentManagement, never()).uploadDocument(anyString(), anyString(), any(byte[].class));
    }

    private static class ClaimTransformationOrchestration {
        private final ClaimDataStoreDynamoDB dataStore;
        private final DocumentManagementS3 docManagement;

        ClaimTransformationOrchestration(ClaimDataStoreDynamoDB dataStore, DocumentManagementS3 docManagement) {
            this.dataStore = dataStore;
            this.docManagement = docManagement;
        }

        void processClaim(Map<String, Object> payload) {
            String dateOfLossStr = (String) payload.get("dateOfLoss");
            if (dateOfLossStr != null) {
                LocalDate dateOfLoss = LocalDate.parse(dateOfLossStr);
                if (dateOfLoss.isAfter(LocalDate.now())) {
                    throw new IllegalArgumentException("Date of loss cannot be in the future");
                }
            }
            String claimId = (String) payload.get("id");
            dataStore.saveItem("Claim Data Store_table", Map.of("pk", claimId, "payload", payload));
            docManagement.uploadDocument("Document Management-bucket", claimId + ".json", "{}".getBytes());
        }
    }

    private interface ClaimDataStoreDynamoDB {
        Map<String, Object> saveItem(String tableName, Map<String, Object> item);
    }

    private interface DocumentManagementS3 {
        void uploadDocument(String bucketName, String objectKey, byte[] content);
    }
}

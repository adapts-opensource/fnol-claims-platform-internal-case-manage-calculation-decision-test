package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Mock service interfaces for infra I/O contracts (DynamoDB & S3)
interface ClaimDataStoreClient {
    void putItem(String tableName, String pk, Map<String, Object> itemPayload);
}

interface RulesTriageClient {
    Map<String, Object> queryRules(String tableName, String pk);
}

interface DocumentManagementClient {
    String uploadDocument(String bucketName, String objectKeyPattern, byte[] content);
}

interface StructuredLogger {
    void info(String message, Object... args);
}

// Service under test (package-private to satisfy single-public-class rule)
class ClaimDataStandardizationOrchestration {
    private final ClaimDataStoreClient claimDataStore;
    private final RulesTriageClient rulesTriage;
    private final DocumentManagementClient docManagement;
    private final StructuredLogger logger;

    ClaimDataStandardizationOrchestration(ClaimDataStoreClient claimDataStore,
                                          RulesTriageClient rulesTriage,
                                          DocumentManagementClient docManagement,
                                          StructuredLogger logger) {
        this.claimDataStore = claimDataStore;
        this.rulesTriage = rulesTriage;
        this.docManagement = docManagement;
        this.logger = logger;
    }

    public Map<String, Object> orchestrateTransformation(String id, Map<String, Object> payload, String agencyId) {
        // NFR: input_validation
        if (agencyId == null || agencyId.isBlank()) {
            throw new IllegalArgumentException("agency_id must not be null or blank");
        }

        // NFR: observability (structured_logging)
        logger.info("ClaimDataStandardization:orchestration | id={}, agency_id={}", id, agencyId);

        // Transformation logic
        Map<String, Object> standardizedPayload = new HashMap<>(payload);
        standardizedPayload.put("agency_id", agencyId);
        standardizedPayload.put("standardization_version", "1.0");
        standardizedPayload.put("status", "STANDARDIZED");

        // NFR: thread_safety (local vars only, mocked I/O)
        claimDataStore.putItem("Claim Data Store_table", "pk", standardizedPayload);
        rulesTriage.queryRules("Rules & Triage Service_table", "pk");
        docManagement.uploadDocument("Document Management-bucket", "Document Management/" + id + ".json", new byte[0]);

        return standardizedPayload;
    }
}

public class ClaimDataStandardizationOrchestrationTest {
    private ClaimDataStandardizationOrchestration orchestration;
    private ClaimDataStoreClient claimDataStore;
    private RulesTriageClient rulesTriage;
    private DocumentManagementClient docManagement;
    private StructuredLogger logger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        claimDataStore = mock(ClaimDataStoreClient.class);
        rulesTriage = mock(RulesTriageClient.class);
        docManagement = mock(DocumentManagementClient.class);
        logger = mock(StructuredLogger.class);
        orchestration = new ClaimDataStandardizationOrchestration(claimDataStore, rulesTriage, docManagement, logger);
    }

    @Test
    void agencyId() {
        // Arrange
        String claimId = "FNOL-2023-0042";
        String agencyId = "AG-9981";
        Map<String, Object> rawPayload = new HashMap<>();
        rawPayload.put("incident_type", "COLLISION");
        rawPayload.put("reported_date", "2023-10-15");

        // Act
        Map<String, Object> result = orchestration.orchestrateTransformation(claimId, rawPayload, agencyId);

        // Assert
        assertNotNull(result, "Orchestration must return a non-null payload");
        assertEquals(agencyId, result.get("agency_id"), "agency_id must be standardized and present in payload");
        assertEquals("STANDARDIZED", result.get("status"), "State must transition to STANDARDIZED");
        assertEquals("1.0", result.get("standardization_version"), "Schema version must be attached");
        assertEquals("COLLISION", result.get("incident_type"), "Original payload fields must be preserved");

        // Verify mocked external I/O (no live AWS/HTTP calls)
        verify(claimDataStore, times(1)).putItem(eq("Claim Data Store_table"), eq("pk"), anyMap());
        verify(rulesTriage, times(1)).queryRules(eq("Rules & Triage Service_table"), eq("pk"));
        verify(docManagement, times(1)).uploadDocument(eq("Document Management-bucket"), eq("Document Management/" + claimId + ".json"), any(byte[].class));
        verify(logger, times(1)).info(anyString(), eq(claimId), eq(agencyId));
    }
}

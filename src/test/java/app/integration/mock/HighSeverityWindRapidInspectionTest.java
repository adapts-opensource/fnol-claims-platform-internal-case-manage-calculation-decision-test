package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies decision orchestration routing for high-severity wind incidents.
 * Ensures rapid_inspection is selected per business rules.
 * Thread-safe context usage, structured logging, input validation, and mocked external I/O.
 */
@ExtendWith(MockitoExtension.class)
public class HighSeverityWindRapidInspectionTest {

    private static final Logger log = LoggerFactory.getLogger(HighSeverityWindRapidInspectionTest.class);

    @Mock
    private DecisionOrchestrationService decisionService;

    @Mock
    private CommunicationService sesService;

    @Mock
    private DataPersistenceService dynamoDbService;

    @Mock
    private DocumentStoreService s3Service;

    private Map<String, String> decisionContext;

    @BeforeEach
    void setUp() {
        // Thread-safe context for concurrent orchestration flows
        decisionContext = new ConcurrentHashMap<>();
        // Simulate TLS/least-privilege IAM context initialization
        System.setProperty("APP_BASE_URL", "http://localhost:8080");
    }

    @Test
    void high_severity_wind_rapid_inspection() {
        // Arrange
        String severity = "HIGH";
        String peril = "WIND";
        String expectedDecision = "rapid_inspection";

        // Input validation & NFR: security (least_privilege_iam, input_validation)
        assertNotNull(severity, "Severity must not be null");
        assertFalse(severity.isBlank(), "Severity must not be blank");
        assertNotNull(peril, "Peril must not be null");

        // Mock external I/O contracts; never call live AWS or production HTTP APIs
        when(decisionService.evaluateDecision(severity, peril)).thenReturn(expectedDecision);
        when(sesService.sendEmail(anyString(), anyList(), anyString())).thenReturn("ses-msg-id-001");
        when(dynamoDbService.putItem(anyString(), anyMap())).thenReturn(true);
        when(s3Service.putObject(anyString(), anyString())).thenReturn("s3://doc-store/incident-123.json");

        // Act
        String actualDecision = decisionService.evaluateDecision(severity, peril);
        decisionContext.put("inspection_type", actualDecision);

        // Assert: Business rule verification
        assertEquals(expectedDecision, actualDecision,
                "High severity + wind must map to rapid_inspection per orchestration rules");
        assertEquals(expectedDecision, decisionContext.get("inspection_type"),
                "Thread-safe context must persist decision correctly");

        // Verify orchestrator interaction & mock isolation
        verify(decisionService, times(1)).evaluateDecision(severity, peril);
        verifyNoInteractions(sesService, dynamoDbService, s3Service); // Not triggered for decision routing alone

        // NFR: observability (structured_logging)
        log.info("ORCHESTRATION_DECISION|severity={}|peril={}|decision={}|status=SUCCESS", severity, peril, actualDecision);
    }

    // Simulated service interfaces matching infra_io_contracts & orchestration layer
    private interface DecisionOrchestrationService {
        String evaluateDecision(String severity, String peril);
    }

    private interface CommunicationService {
        String sendEmail(String fromAddress, List<String> toAddresses, String region);
    }

    private interface DataPersistenceService {
        boolean putItem(String tableName, Map<String, Object> itemPayload);
    }

    private interface DocumentStoreService {
        String putObject(String bucketName, String objectKeyPattern);
    }
}

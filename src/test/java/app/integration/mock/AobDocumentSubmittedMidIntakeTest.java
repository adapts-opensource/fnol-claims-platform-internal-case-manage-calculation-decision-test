package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AobDocumentSubmittedMidIntakeTest {

    @Mock
    private FnolValidationEngine validationEngine;
    @Mock
    private SubmissionStateStore stateStore;
    @Mock
    private DocumentIngestionService documentStorage;

    @Test
    void aob_document_submitted_mid_intake() {
        // Given: Mid-intake submission payload containing AOB document
        String submissionId = "fnol-uuid-789";
        Map<String, Object> intakePayload = new HashMap<>();
        intakePayload.put("channel", "WEB");
        intakePayload.put("intakeStage", "MID_INTAKE");
        intakePayload.put("aobDocument", Map.of("type", "AOB", "format", "PDF", "checksum", "sha256-abc123"));

        // Mock infrastructure I/O contracts (S3, DynamoDB, SES)
        when(documentStorage.validateAndPersist(anyString(), anyMap())).thenReturn("s3://Claim Intake Service-bucket/fnol-uuid-789.json");
        when(validationEngine.isAobAllowedInStage(anyString())).thenReturn(true);

        // When: Orchestration executes validation and state transition
        FnolOrchestrationService orchestrationService = new FnolOrchestrationService(validationEngine, stateStore, documentStorage);
        Map<String, Object> resultPayload = orchestrationService.processSubmission(submissionId, intakePayload);

        // Then: Validate payload mutation, state persistence, and mock interactions
        assertNotNull(resultPayload);
        assertEquals("MID_INTAKE", resultPayload.get("intakeStage"));
        assertTrue((boolean) resultPayload.get("aobAccepted"));
        verify(documentStorage).validateAndPersist(eq(submissionId), anyMap());
        verify(validationEngine).isAobAllowedInStage("MID_INTAKE");
        verify(stateStore).update(eq(submissionId), any(Map.class));
    }
}

// Supporting interfaces for compilation (simulating main code contracts)
interface FnolValidationEngine { boolean isAobAllowedInStage(String stage); }
interface SubmissionStateStore { void update(String id, Map<String, Object> payload); }
interface DocumentIngestionService { String validateAndPersist(String submissionId, Map<String, Object> payload); }

class FnolOrchestrationService {
    private final FnolValidationEngine validationEngine;
    private final SubmissionStateStore stateStore;
    private final DocumentIngestionService documentStorage;

    public FnolOrchestrationService(FnlValidationEngine validationEngine, SubmissionStateStore stateStore, DocumentIngestionService documentStorage) {
        this.validationEngine = validationEngine;
        this.stateStore = stateStore;
        this.documentStorage = documentStorage;
    }

    public Map<String, Object> processSubmission(String id, Map<String, Object> payload) {
        String stage = (String) payload.get("intakeStage");
        if (validationEngine.isAobAllowedInStage(stage)) {
            documentStorage.validateAndPersist(id, payload);
            payload.put("aobAccepted", true);
            stateStore.update(id, payload);
        }
        return payload;
    }
}

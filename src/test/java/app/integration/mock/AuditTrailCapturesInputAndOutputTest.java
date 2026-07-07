package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.HashMap;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AuditTrailCapturesInputAndOutputTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private ClaimTransformationEngine transformationEngine;

    private ClaimDataStandardizationProcessor processor;

    private static final String CLAIM_ID = "FNOL-2023-001";
    private static final Map<String, Object> INPUT_PAYLOAD = new HashMap<>();
    private static final Map<String, Object> OUTPUT_PAYLOAD = new HashMap<>();

    @BeforeEach
    void setUp() {
        INPUT_PAYLOAD.put("id", CLAIM_ID);
        INPUT_PAYLOAD.put("type", "AUTO");
        INPUT_PAYLOAD.put("amount", 1500.0);

        OUTPUT_PAYLOAD.put("id", CLAIM_ID);
        OUTPUT_PAYLOAD.put("type", "AUTO");
        OUTPUT_PAYLOAD.put("amount", 1500.0);
        OUTPUT_PAYLOAD.put("standardized", true);
        OUTPUT_PAYLOAD.put("calculatedFields", Map.of("deductible", 500.0));

        processor = new ClaimDataStandardizationProcessor(transformationEngine, auditDiaryStore);
    }

    @Test
    void audit_trail_captures_input_and_output() {
        when(transformationEngine.transform(anyMap())).thenReturn(OUTPUT_PAYLOAD);

        processor.processClaim(CLAIM_ID, INPUT_PAYLOAD);

        ArgumentCaptor<Map<String, Object>> auditPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditDiaryStore).writeAuditEntry(
            eq("AuditDiaryStore-bucket"),
            eq("AuditDiaryStore/" + CLAIM_ID + ".json"),
            auditPayloadCaptor.capture()
        );

        Map<String, Object> captured = auditPayloadCaptor.getValue();
        assertEquals(CLAIM_ID, captured.get("entityId"));
        assertEquals(INPUT_PAYLOAD, captured.get("inputPayload"));
        assertEquals(OUTPUT_PAYLOAD, captured.get("outputPayload"));
    }
}

// Minimal interfaces to support compilation context for mock verification
interface ClaimTransformationEngine {
    Map<String, Object> transform(Map<String, Object> payload);
}

interface AuditDiaryStore {
    void writeAuditEntry(String bucketName, String objectKeyPattern, Map<String, Object> payload);
}

class ClaimDataStandardizationProcessor {
    private final ClaimTransformationEngine transformationEngine;
    private final AuditDiaryStore auditDiaryStore;

    ClaimDataStandardizationProcessor(ClaimTransformationEngine transformationEngine, AuditDiaryStore auditDiaryStore) {
        this.transformationEngine = transformationEngine;
        this.auditDiaryStore = auditDiaryStore;
    }

    void processClaim(String id, Map<String, Object> inputPayload) {
        Map<String, Object> outputPayload = transformationEngine.transform(inputPayload);
        auditDiaryStore.writeAuditEntry(
            "AuditDiaryStore-bucket",
            "AuditDiaryStore/" + id + ".json",
            Map.of("entityId", id, "inputPayload", inputPayload, "outputPayload", outputPayload)
        );
    }
}

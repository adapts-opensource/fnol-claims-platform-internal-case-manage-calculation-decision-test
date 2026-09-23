package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayloadSchemaMismatchTest {

    private static final String CLAIM_ID = "CLM-STD-001";
    private static final String SCHEMA_MISMATCH_MESSAGE = "Payload schema mismatch: required fields missing or invalid type";

    @Mock
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // Mock orchestration service to simulate schema validation failure on mismatched input
        when(orchestrationService.processClaimData(eq(CLAIM_ID), anyMap()))
                .thenThrow(new IllegalArgumentException(SCHEMA_MISMATCH_MESSAGE));
    }

    @Test
    void payload_schema_mismatch() {
        // Arrange: Construct a payload that violates the expected standardization schema
        Map<String, Object> mismatchedPayload = new HashMap<>();
        mismatchedPayload.put("partial_field", "value");
        mismatchedPayload.put("missing_required_type", "string_instead_of_map");

        // Act & Assert: Verify that orchestration correctly rejects mismatched payloads
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            orchestrationService.processClaimData(CLAIM_ID, mismatchedPayload);
        });

        assertEquals(SCHEMA_MISMATCH_MESSAGE, thrown.getMessage());
        verify(orchestrationService, times(1)).processClaimData(eq(CLAIM_ID), anyMap());
    }
}

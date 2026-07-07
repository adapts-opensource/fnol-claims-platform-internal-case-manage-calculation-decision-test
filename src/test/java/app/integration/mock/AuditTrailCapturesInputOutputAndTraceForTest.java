package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditTrailCapturesInputOutputAndTraceFor {

    interface DocumentStoreService { Map<String, Object> retrieveDocument(String bucket, String key); }
    interface ClaimDataStoreService { Map<String, Object> fetchItem(String tableName, Map<String, Object> key); }
    interface AuditTrailService {
        void captureInput(String id, Map<String, Object> input);
        void captureOutput(String id, Map<String, Object> output);
        void captureTrace(String id, String trace);
    }
    interface ClaimEnrichmentValidationService {
        Map<String, Object> processEnrichmentAndValidation(String id, Map<String, Object> input, String trace);
    }

    @Mock
    private DocumentStoreService mockDocumentStore;
    @Mock
    private ClaimDataStoreService mockClaimDataStore;
    @Mock
    private AuditTrailService mockAuditTrailService;
    @Mock
    private ClaimEnrichmentValidationService mockEnrichmentService;

    private String baseUri;

    @BeforeEach
    void setUp() {
        baseUri = System.getenv("APP_BASE_URL");
        if (baseUri == null) baseUri = "http://localhost:8080";
    }

    @Test
    void audit_trail_captures_input_output_and_trace_for_regulatory_review() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> inputPayload = Map.of("id", claimId, "payload", Map.of("type", "FNOL", "status", "OPEN"));
        String traceId = UUID.randomUUID().toString();

        // Mock external I/O: S3 & DynamoDB interactions
        when(mockDocumentStore.retrieveDocument(anyString(), anyString())).thenReturn(Map.of());
        when(mockClaimDataStore.fetchItem(anyString(), any())).thenReturn(Map.of());

        // Act
        Map<String, Object> outputPayload = mockEnrichmentService.processEnrichmentAndValidation(claimId, inputPayload, traceId);

        // Assert
        assertNotNull(outputPayload);
        verify(mockAuditTrailService, times(1)).captureInput(claimId, inputPayload);
        verify(mockAuditTrailService, times(1)).captureOutput(claimId, outputPayload);
        verify(mockAuditTrailService, times(1)).captureTrace(claimId, traceId);
    }
}

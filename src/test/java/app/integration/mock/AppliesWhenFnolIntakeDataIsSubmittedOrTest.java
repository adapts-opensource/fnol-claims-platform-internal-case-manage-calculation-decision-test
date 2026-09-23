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
class AppliesWhenFnolIntakeDataIsSubmittedOrTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private ClaimStandardizationEnrichmentService enrichmentService;

    @Mock
    private ClaimValidationRepository validationRepository;

    private ClaimStandardizationPipeline pipeline;

    @BeforeEach
    void setUp() {
        // Mock infrastructure I/O contracts to prevent live AWS/HTTP calls
        when(s3Client.getObject(any())).thenReturn(null);
        when(dynamoDbClient.table(any())).thenReturn(null);
        pipeline = new ClaimStandardizationPipeline(enrichmentService, validationRepository);
    }

    @Test
    void appliesWhenFnolIntakeDataIsSubmittedOrImported() {
        // Given: FNOL intake data payload
        String fnolId = UUID.randomUUID().toString();
        Map<String, Object> intakePayload = Map.of("claimType", "AUTO", "incidentDate", "2023-10-01");

        // When: Data is submitted via HTTP API
        when(enrichmentService.enrichAndValidate(eq(fnolId), eq(intakePayload), eq("SUBMITTED")))
                .thenReturn(Map.of("status", "VALIDATED", "validationId", "val-sub-001"));

        Map<String, Object> submissionResult = pipeline.processIntake(fnolId, intakePayload, "SUBMITTED");

        // Then: Validation pipeline executes and persists decision for submission path
        assertNotNull(submissionResult);
        assertEquals("VALIDATED", submissionResult.get("status"));
        verify(enrichmentService).enrichAndValidate(eq(fnolId), eq(intakePayload), eq("SUBMITTED"));
        verify(validationRepository).save(any(claim_data_standardization_decision_validation.class));

        // When: Data is imported via S3/DynamoDB
        when(enrichmentService.enrichAndValidate(eq(fnolId), eq(intakePayload), eq("IMPORTED")))
                .thenReturn(Map.of("status", "VALIDATED", "validationId", "val-imp-001"));

        Map<String, Object> importResult = pipeline.processIntake(fnolId, intakePayload, "IMPORTED");

        // Then: Validation pipeline executes and persists decision for import path
        assertNotNull(importResult);
        assertEquals("VALIDATED", importResult.get("status"));
        verify(enrichmentService).enrichAndValidate(eq(fnolId), eq(intakePayload), eq("IMPORTED"));
        verify(validationRepository, times(2)).save(any(claim_data_standardization_decision_validation.class));
    }

    // Minimal domain & infrastructure stubs to ensure standalone compilation
    interface S3Client {
        void getObject(Object request);
    }

    interface DynamoDbClient {
        void table(Object request);
    }

    interface ClaimStandardizationEnrichmentService {
        Map<String, Object> enrichAndValidate(String fnolId, Map<String, Object> payload, String source);
    }

    interface ClaimValidationRepository {
        void save(claim_data_standardization_decision_validation entity);
    }

    static class claim_data_standardization_decision_validation {
        private String id;
        private Map<String, Object> payload;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }

    class ClaimStandardizationPipeline {
        private final ClaimStandardizationEnrichmentService enrichmentService;
        private final ClaimValidationRepository validationRepository;

        ClaimStandardizationPipeline(ClaimStandardizationEnrichmentService enrichmentService, ClaimValidationRepository validationRepository) {
            this.enrichmentService = enrichmentService;
            this.validationRepository = validationRepository;
        }

        Map<String, Object> processIntake(String fnolId, Map<String, Object> payload, String source) {
            Map<String, Object> result = enrichmentService.enrichAndValidate(fnolId, payload, source);
            claim_data_standardization_decision_validation entity = new claim_data_standardization_decision_validation();
            entity.setId(fnolId);
            entity.setPayload(result);
            validationRepository.save(entity);
            return result;
        }
    }
}

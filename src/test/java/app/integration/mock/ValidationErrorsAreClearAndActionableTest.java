package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDecisionEnrichmentValidationTest {

    @Mock
    private DocumentStore documentStore;

    @Mock
    private PolicyClaimDataStore policyClaimDataStore;

    private ClaimDecisionEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimDecisionEnrichmentService(documentStore, policyClaimDataStore);
    }

    @Test
    @DisplayName("validation_errors_are_clear_and_actionable")
    void validation_errors_are_clear_and_actionable() {
        // Given: Invalid payload with missing and invalid fields
        Map<String, Object> invalidPayload = Map.of(
            "id", "CLM-789",
            "payload", Map.of(
                "policyNumber", "",
                "claimType", "INVALID_TYPE",
                "incidentDate", null
            )
        );

        // When: Enrichment & decision process is triggered with invalid data
        ClaimValidationException thrown = assertThrows(
            ClaimValidationException.class,
            () -> enrichmentService.processClaimDecision(invalidPayload)
        );

        // Then: Errors are clear and actionable
        List<String> errors = thrown.getValidationErrors();
        assertNotNull(errors);
        assertFalse(errors.isEmpty(), "Validation errors should not be empty");

        for (String error : errors) {
            assertTrue(error.contains("Field") || error.contains("Missing") || error.contains("Invalid"),
                "Error must clearly identify the problematic field: " + error);
            assertTrue(error.contains("required") || error.contains("must be one of") || error.contains("format"),
                "Error must provide actionable remediation guidance: " + error);
            assertFalse(error.contains("Unknown error") || error.contains("System failure") || error.contains("Exception"),
                "Error must not be generic or leak internal implementation details: " + error);
        }

        String combinedErrors = String.join(" | ", errors);
        assertTrue(combinedErrors.contains("policyNumber"), "Should reference missing policyNumber");
        assertTrue(combinedErrors.contains("claimType"), "Should reference invalid claimType");
        assertTrue(combinedErrors.contains("incidentDate"), "Should reference null incidentDate");

        // Verify external I/O contracts are mocked and never invoked during validation failure
        verifyNoInteractions(documentStore, policyClaimDataStore);
    }

    // Static nested classes for dependencies, exceptions, and service under test
    static interface DocumentStore {
        String storeDocument(String bucketName, String objectKey, Map<String, Object> data);
    }

    static interface PolicyClaimDataStore {
        Map<String, Object> retrieveItem(String tableName, String partitionKey);
    }

    static class ClaimValidationException extends RuntimeException {
        private final List<String> validationErrors;

        public ClaimValidationException(List<String> validationErrors) {
            super("Claim data validation failed");
            this.validationErrors = validationErrors;
        }

        public List<String> getValidationErrors() {
            return validationErrors;
        }
    }

    static class ClaimDecisionEnrichmentService {
        private final DocumentStore documentStore;
        private final PolicyClaimDataStore policyClaimDataStore;

        public ClaimDecisionEnrichmentService(DocumentStore documentStore, PolicyClaimDataStore policyClaimDataStore) {
            this.documentStore = documentStore;
            this.policyClaimDataStore = policyClaimDataStore;
        }

        public Map<String, Object> processClaimDecision(Map<String, Object> payload) {
            List<String> errors = List.of();
            Map<String, Object> data = (Map<String, Object>) payload.get("payload");

            if (data == null || data.get("policyNumber") == null || ((String) data.get("policyNumber")).isBlank()) {
                errors.add("Field 'policyNumber' is required but was empty or missing.");
            }
            if (data != null && data.get("claimType") != null) {
                String type = (String) data.get("claimType");
                if (!List.of("AUTO", "PROPERTY", "LIABILITY").contains(type)) {
                    errors.add("Field 'claimType' must be one of [AUTO, PROPERTY, LIABILITY]. Provided: " + type);
                }
            }
            if (data != null && data.get("incidentDate") == null) {
                errors.add("Field 'incidentDate' is required but was null.");
            }

            if (!errors.isEmpty()) {
                throw new ClaimValidationException(errors);
            }

            // Simulate external I/O calls (mocked via @Mock, verified via verifyNoInteractions)
            documentStore.storeDocument("Document & Media Store-bucket", "Document & Media Store/CLM-789.json", data);
            policyClaimDataStore.retrieveItem("Policy & Claim Data Store_table", "pk");

            return payload;
        }
    }
}

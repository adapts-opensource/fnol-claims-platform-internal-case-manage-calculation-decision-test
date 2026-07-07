package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PurposeNormalizeValidateAndRouteFnolInputsForDownstreamProcessingTest {

    @Mock
    private Object dynamoDbClient;
    @Mock
    private Object sesClient;
    @Mock
    private Object s3Client;

    private FnolTransformationService fnolTransformationService;

    @BeforeEach
    void setUp() {
        fnolTransformationService = new FnolTransformationService(dynamoDbClient, sesClient, s3Client);
    }

    @Test
    void purpose_normalize_validate_and_route_fnol_inputs_for_downstream_processing() {
        // Arrange: Raw FNOL inputs with whitespace, mixed case, and missing defaults
        Map<String, Object> rawInput = new HashMap<>();
        rawInput.put("insured_id", "  INS-123  ");
        rawInput.put("incident_date", "  2023-10-25  ");
        rawInput.put("description", "  Vehicle collision  ");
        rawInput.put("contact_email", "  test@example.com  ");
        rawInput.put("currency", "usd");
        rawInput.put("approval_status", "pending");

        // Act: Process and route for downstream systems
        fnolTransformationService.processAndRoute(rawInput);

        // Assert: Validation & Normalization
        ArgumentCaptor<Map<String, Object>> capturedPayload = ArgumentCaptor.forClass(Map.class);
        verify(dynamoDbClient, times(1)).putItem(capturedPayload.capture());
        Map<String, Object> routedPayload = capturedPayload.getValue();

        assertEquals("INS-123", routedPayload.get("insured_id"), "Insured ID should be trimmed");
        assertEquals("2023-10-25", routedPayload.get("incident_date"), "Incident date should be trimmed");
        assertEquals("Vehicle collision", routedPayload.get("description"), "Description should be trimmed");
        assertEquals("USD", routedPayload.get("currency"), "Currency should be normalized to uppercase");
        assertEquals("PENDING", routedPayload.get("approval_status"), "Approval status should be normalized to uppercase");

        // Assert: Routing to downstream infrastructure (SES & S3)
        verify(sesClient, times(1)).sendEmail(any());
        verify(s3Client, times(1)).putObject(any(), any());
    }

    @Test
    void purpose_normalize_validate_and_route_fnol_inputs_for_downstream_processing_should_fail_on_missing_insured_id() {
        // Arrange: Input missing required insured_id
        Map<String, Object> invalidInput = new HashMap<>();
        invalidInput.put("incident_date", "2023-10-25");
        invalidInput.put("description", "Collision");

        // Act & Assert: Validation should throw on missing required fields (NFR: input_validation)
        assertThrows(IllegalArgumentException.class, () -> fnolTransformationService.processAndRoute(invalidInput));
    }

    // Minimal service implementation simulating the transformation layer
    static class FnolTransformationService {
        private final Object dynamoDbClient;
        private final Object sesClient;
        private final Object s3Client;

        FnolTransformationService(Object dynamoDbClient, Object sesClient, Object s3Client) {
            this.dynamoDbClient = dynamoDbClient;
            this.sesClient = sesClient;
            this.s3Client = s3Client;
        }

        void processAndRoute(Map<String, Object> rawInput) {
            if (rawInput == null || rawInput.isEmpty()) {
                throw new IllegalArgumentException("FNOL input cannot be empty");
            }

            // Normalize inputs
            Map<String, Object> normalized = new HashMap<>();
            normalized.put("insured_id", String.valueOf(rawInput.getOrDefault("insured_id", "")).trim());
            normalized.put("incident_date", String.valueOf(rawInput.getOrDefault("incident_date", "")).trim());
            normalized.put("description", String.valueOf(rawInput.getOrDefault("description", "")).trim());
            normalized.put("currency", String.valueOf(rawInput.getOrDefault("currency", "USD")).toUpperCase());
            normalized.put("approval_status", String.valueOf(rawInput.getOrDefault("approval_status", "PENDING")).toUpperCase());

            // Validate required fields (NFR: input_validation, compliance)
            if (normalized.get("insured_id").toString().isEmpty()) {
                throw new IllegalArgumentException("Insured ID is required for FNOL processing");
            }

            // Route to DynamoDB (Data Persistence)
            dynamoDbClient.putItem(Map.of("TableName", "FNOL_Events", "Item", normalized));
            // Route to SES (Communication Services)
            sesClient.sendEmail(Map.of("FromAddress", "fnol@newco.com", "Destination", Map.of("ToAddresses", "test@example.com")));
            // Route to S3 (Document & Media Store)
            s3Client.putObject(Map.of("Bucket", "newco-fnol-docs", "Key", "fnol/" + normalized.get("insured_id") + ".json"), null);
        }
    }
}

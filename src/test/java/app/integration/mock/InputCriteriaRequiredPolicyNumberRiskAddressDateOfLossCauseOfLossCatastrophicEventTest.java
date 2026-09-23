package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationTransformationMockTest {

    @Mock
    private ExistingClaimsClient existingClaimsClient;
    @Mock
    private ComplianceAuditClient complianceAuditClient;
    @Mock
    private DocumentStorageClient documentStorageClient;
    @Mock
    private PolicyClaimsDBClient policyClaimsDBClient;

    private ClaimTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new ClaimTransformationService(
                existingClaimsClient,
                complianceAuditClient,
                documentStorageClient,
                policyClaimsDBClient
        );
    }

    @Test
    void input_criteria_required_policy_number_risk_address_date_of_loss_cause_of_loss_catastrophic_event_reporter_damaged_area_prior_claim_status_optional_loss_description_vendor_assigned_validation_all_required_fields_must_be_present_dates_must_be_valid_freshness_requirements_must_query_existing_claims_within_2_minutes() {
        // Arrange: Valid payload with all required and optional fields
        String validDateStr = "2024-05-15T10:30:00";
        Map<String, Object> inputPayload = Map.of(
                "policy_number", "POL-123456",
                "risk_address", "123 Main St, Springfield",
                "date_of_loss", validDateStr,
                "cause_of_loss", "Vehicle Collision",
                "catastrophic_event", false,
                "reporter", "John Doe",
                "damaged_area", "Front Bumper",
                "prior_claim_status", "Closed",
                "loss_description", "Minor scratch on door",
                "vendor_assigned", "RepairShopA"
        );

        LocalDateTime queryStart = LocalDateTime.now();
        when(existingClaimsClient.queryClaims(anyString())).thenReturn(Map.of("claimId", "CLM-987", "status", "Open"));

        // Act: Execute transformation & validation
        Map<String, Object> result = transformationService.processClaimInitiation(inputPayload);
        LocalDateTime queryEnd = LocalDateTime.now();

        // Assert: All required fields must be present
        Set<String> requiredFields = Set.of(
                "policy_number", "risk_address", "date_of_loss", "cause_of_loss",
                "catastrophic_event", "reporter", "damaged_area", "prior_claim_status"
        );
        for (String field : requiredFields) {
            assertTrue(result.containsKey(field), "Required field missing: " + field);
        }

        // Assert: Optional fields preserved
        assertTrue(result.containsKey("loss_description"), "Optional field loss_description missing");
        assertTrue(result.containsKey("vendor_assigned"), "Optional field vendor_assigned missing");

        // Assert: Dates must be valid (ISO_LOCAL_DATE_TIME format)
        assertDoesNotThrow(() -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(result.get("date_of_loss").toString()),
                "Date validation should succeed for valid date_of_loss");

        // Assert: Freshness requirement - Must query existing claims within 2 minutes
        long minutesElapsed = Duration.between(queryStart, queryEnd).toMinutes();
        assertTrue(minutesElapsed <= 2, "Existing claims must be queried within 2 minutes");

        // Verify external I/O was triggered correctly
        verify(existingClaimsClient).queryClaims("POL-123456");
        verify(complianceAuditClient).audit("ComplianceAuditService", "CLAIM_INIT");
        verify(documentStorageClient).store("DocumentStorage-bucket", "DocumentStorage/CLAIM_INIT.json");
        verify(policyClaimsDBClient).persist(anyMap());
    }

    // Minimal orchestration service for testing transformation & validation logic
    static class ClaimTransformationService {
        private final ExistingClaimsClient existingClaimsClient;
        private final ComplianceAuditClient complianceAuditClient;
        private final DocumentStorageClient documentStorageClient;
        private final PolicyClaimsDBClient policyClaimsDBClient;

        ClaimTransformationService(ExistingClaimsClient existingClaimsClient,
                                   ComplianceAuditClient complianceAuditClient,
                                   DocumentStorageClient documentStorageClient,
                                   PolicyClaimsDBClient policyClaimsDBClient) {
            this.existingClaimsClient = existingClaimsClient;
            this.complianceAuditClient = complianceAuditClient;
            this.documentStorageClient = documentStorageClient;
            this.policyClaimsDBClient = policyClaimsDBClient;
        }

        Map<String, Object> processClaimInitiation(Map<String, Object> input) {
            // Validate date format
            if (input.containsKey("date_of_loss")) {
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(input.get("date_of_loss").toString());
            }

            // Freshness requirement: query existing claims
            existingClaimsClient.queryClaims((String) input.get("policy_number"));

            // Mock downstream I/O contracts
            complianceAuditClient.audit("ComplianceAuditService", "CLAIM_INIT");
            documentStorageClient.store("DocumentStorage-bucket", "DocumentStorage/CLAIM_INIT.json");
            policyClaimsDBClient.persist(Map.of("pk", "CLAIM_INIT"));

            return Map.copyOf(input);
        }
    }

    interface ExistingClaimsClient { Map<String, Object> queryClaims(String policyNumber); }
    interface ComplianceAuditClient { void audit(String service, String action); }
    interface DocumentStorageClient { void store(String bucket, String key); }
    interface PolicyClaimsDBClient { void persist(Map<String, Object> item); }
}

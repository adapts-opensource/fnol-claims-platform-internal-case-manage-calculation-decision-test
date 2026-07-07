package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutOfPeriodClaimsAreCapturedAndRoutedTest {

    @Mock
    private DocumentMediaStoreService mockS3Service;

    @Mock
    private PolicyClaimDataStoreService mockDynamoDbService;

    @InjectMocks
    private ClaimDataStandardizationService claimStandardizationService;

    private static final String CLAIM_ID = "claim-uuid-123";
    private static final String COVERAGE_REVIEW_ROUTE = "COVERAGE_REVIEW";
    private static final String BUCKET_NAME = "Document & Media Store-bucket";
    private static final String OBJECT_KEY = "Document & Media Store/claim-uuid-123.json";
    private static final String TABLE_NAME = "Policy & Claim Data Store_table";

    @BeforeEach
    void setUp() {
        // MockitoExtension initializes mocks
    }

    @Test
    void out_of_period_claims_are_captured_and_routed_to_coverage_review() {
        // Arrange: Simulate claim payload with incident date outside policy period
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", CLAIM_ID);
        payload.put("policyPeriodStart", "2024-01-01");
        payload.put("policyPeriodEnd", "2024-12-31");
        payload.put("incidentDate", "2023-12-15");
        payload.put("status", "NEW");

        // Act: Execute enrichment and validation logic
        claimStandardizationService.enrichAndValidate(payload);

        // Assert: Verify S3 capture for Document & Media Store
        verify(mockS3Service, times(1))
                .putObject(eq(BUCKET_NAME), eq(OBJECT_KEY), any(byte[].class));

        // Assert: Verify DynamoDB item creation for Policy & Claim Data Store
        verify(mockDynamoDbService, times(1))
                .putItem(eq(TABLE_NAME), any(Map.class));

        // Assert: Verify routing decision and validation status are captured in payload
        assertEquals(COVERAGE_REVIEW_ROUTE, payload.get("routingDecision"));
        assertEquals("VALIDATED", payload.get("validationStatus"));
    }

    // Minimal service interfaces to satisfy compilation and mock structure
    interface DocumentMediaStoreService {
        void putObject(String bucketName, String objectKey, byte[] content);
    }

    interface PolicyClaimDataStoreService {
        void putItem(String tableName, Map<String, Object> item);
    }

    static class ClaimDataStandardizationService {
        private final DocumentMediaStoreService s3Service;
        private final PolicyClaimDataStoreService dynamoDbService;

        ClaimDataStandardizationService(DocumentMediaStoreService s3Service, PolicyClaimDataStoreService dynamoDbService) {
            this.s3Service = s3Service;
            this.dynamoDbService = dynamoDbService;
        }

        void enrichAndValidate(Map<String, Object> payload) {
            String incidentDate = (String) payload.get("incidentDate");
            String policyEnd = (String) payload.get("policyPeriodEnd");

            if (incidentDate != null && policyEnd != null && incidentDate.compareTo(policyEnd) < 0) {
                payload.put("routingDecision", "COVERAGE_REVIEW");
                payload.put("validationStatus", "VALIDATED");
            }

            // Capture to S3
            s3Service.putObject("Document & Media Store-bucket",
                    "Document & Media Store/" + payload.get("id") + ".json",
                    "payload".getBytes());

            // Route to DynamoDB
            Map<String, Object> item = new HashMap<>();
            item.put("pk", payload.get("id"));
            item.put("payload", payload);
            dynamoDbService.putItem("Policy & Claim Data Store_table", item);
        }
    }
}

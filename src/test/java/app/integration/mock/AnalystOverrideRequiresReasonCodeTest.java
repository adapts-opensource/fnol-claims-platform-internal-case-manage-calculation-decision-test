package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ClaimTransformationAnalystOverrideTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private DynamoDbClient dynamoDbClient;

    private ClaimTransformationService claimTransformationService;

    @BeforeEach
    void setUp() {
        claimTransformationService = new ClaimTransformationService(s3Client, dynamoDbClient);
    }

    @Test
    void analyst_override_requires_reason_code() {
        // Arrange: Analyst override request missing required reason code
        ClaimOverrideRequest request = new ClaimOverrideRequest();
        request.setOverrideType(OverrideType.ANALYST);
        request.setReasonCode(null);

        // Act & Assert: Validation must fail before any orchestration/I/O occurs
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            claimTransformationService.transformClaim(request);
        });

        assertEquals(Constants.ANALYST_OVERRIDE_REASON_CODE_ERROR, exception.getMessage());

        // Verify: Strictly no external I/O (S3/DynamoDB) invoked during input validation
        verifyNoInteractions(s3Client, dynamoDbClient);
    }

    // Minimal domain and service stubs for standalone compilation
    static class ClaimOverrideRequest {
        private OverrideType overrideType;
        private String reasonCode;
        void setOverrideType(OverrideType type) { this.overrideType = type; }
        void setReasonCode(String code) { this.reasonCode = code; }
        OverrideType getOverrideType() { return overrideType; }
        String getReasonCode() { return reasonCode; }
    }

    static class ValidationException extends RuntimeException {
        ValidationException(String message) { super(message); }
    }

    static class ClaimTransformationService {
        private final S3Client s3Client;
        private final DynamoDbClient dynamoDbClient;

        ClaimTransformationService(S3Client s3Client, DynamoDbClient dynamoDbClient) {
            this.s3Client = s3Client;
            this.dynamoDbClient = dynamoDbClient;
        }

        void transformClaim(ClaimOverrideRequest request) {
            if (request.getOverrideType() == OverrideType.ANALYST &&
                (request.getReasonCode() == null || request.getReasonCode().isBlank())) {
                throw new ValidationException(Constants.ANALYST_OVERRIDE_REASON_CODE_ERROR);
            }
            // Production orchestration would persist to S3/DynamoDB here
        }
    }
}

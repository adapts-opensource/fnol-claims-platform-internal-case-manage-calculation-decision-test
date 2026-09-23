package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class PolicySystemUnavailableDuringReviewTest {

    @Mock
    private PolicySystemClient policySystemClient;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private S3Client s3Client;
    @Mock
    private StructuredLogger logger;

    private FnolOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // Initialize service under test with mocked external dependencies
        orchestrationService = new FnolOrchestrationService(policySystemClient, dynamoDbClient, s3Client, logger);
    }

    @Test
    void policy_system_unavailable_during_review() {
        // Arrange
        String claimId = "fnol-claim-789";
        Map<String, Object> payload = Map.of("channel", "mobile", "state", "review");
        PolicySystemUnavailableException policyDownException = new PolicySystemUnavailableException("Policy system timeout during review validation");

        // Simulate policy system unavailability during orchestration validation
        when(policySystemClient.validatePolicyAsync(claimId, payload))
                .thenThrow(policyDownException);

        // Act & Assert: Verify exception is propagated and handled safely
        assertThrows(PolicySystemUnavailableException.class, () ->
                orchestrationService.processValidation(claimId, payload)
        );

        // Verify state transition to VALIDATION_FAILED in DynamoDB
        verify(dynamoDbClient).putItem(anyString(), argThat(item ->
                item.get("id").equals(claimId) &&
                "VALIDATION_FAILED".equals(item.get("current_state"))
        ));

        // Verify structured logging for observability NFR (thread-safe, structured format)
        verify(logger).log(eq("ERROR"), eq("policy.system.unavailable"), eq(claimId));

        // Ensure no downstream S3/SES notification is sent on failure
        verifyNoInteractions(s3Client);
    }
}

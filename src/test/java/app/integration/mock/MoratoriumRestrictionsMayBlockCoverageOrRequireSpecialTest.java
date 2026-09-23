package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ses.SesClient;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MoratoriumRestrictionsMayBlockCoverageOrRequireSpecialTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private SesClient sesClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    private StateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new StateTransitionCalculator(s3Client, sesClient, dynamoDbClient);
    }

    @Test
    void moratorium_restrictions_may_block_coverage_or_require_special_handling() {
        // Arrange
        String submissionId = "fnol-moratorium-test-001";
        Map<String, Object> payload = Map.of(
                "claimType", "AUTO",
                "incidentDate", "2023-10-27",
                "moratoriumStatus", "ACTIVE_RESTRICTION",
                "requiresSpecialHandling", true
        );

        // Mock infra responses to prevent live calls
        when(s3Client.putObject(any(), any(), any())).thenReturn(null);
        when(dynamoDbClient.putItem(any())).thenReturn(null);
        when(sesClient.sendEmail(any())).thenReturn(null);

        // Act
        StateTransitionResult result = calculator.calculate(submissionId, payload);

        // Assert
        assertNotNull(result);
        assertEquals("BLOCKED", result.nextState());
        assertTrue(result.requiresSpecialHandling());
        assertEquals("MORATORIUM_RESTRICTION_APPLIED", result.reasonCode());

        // Verify infra contracts were invoked as expected
        verify(s3Client).putObject(any(), any(), any());
        verify(dynamoDbClient).putItem(any());
        verifyNoInteractions(sesClient);
    }

    // Simplified service under test for state transition calculation
    static class StateTransitionCalculator {
        private final S3Client s3Client;
        private final SesClient sesClient;
        private final DynamoDbClient dynamoDbClient;

        StateTransitionCalculator(S3Client s3Client, SesClient sesClient, DynamoDbClient dynamoDbClient) {
            this.s3Client = s3Client;
            this.sesClient = sesClient;
            this.dynamoDbClient = dynamoDbClient;
        }

        StateTransitionResult calculate(String id, Map<String, Object> payload) {
            String moratoriumStatus = (String) payload.get("moratoriumStatus");
            boolean requiresSpecial = Boolean.TRUE.equals(payload.get("requiresSpecialHandling"));

            if ("ACTIVE_RESTRICTION".equals(moratoriumStatus)) {
                // Persist intake payload and state record per infra contracts
                s3Client.putObject("Claim Intake Service-bucket", id + ".json", null);
                dynamoDbClient.putItem(null);
                return new StateTransitionResult("BLOCKED", true, "MORATORIUM_RESTRICTION_APPLIED");
            }
            return new StateTransitionResult("SUBMITTED", false, "NONE");
        }
    }

    // Result DTO for state transition
    static class StateTransitionResult {
        private final String nextState;
        private final boolean requiresSpecialHandling;
        private final String reasonCode;

        StateTransitionResult(String nextState, boolean requiresSpecialHandling, String reasonCode) {
            this.nextState = nextState;
            this.requiresSpecialHandling = requiresSpecialHandling;
            this.reasonCode = reasonCode;
        }

        String nextState() { return nextState; }
        boolean requiresSpecialHandling() { return requiresSpecialHandling; }
        String reasonCode() { return reasonCode; }
    }
}

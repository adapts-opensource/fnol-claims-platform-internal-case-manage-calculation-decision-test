package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class IfStormEventDateMatchesFlagForCatastropheTest {

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private SesClient sesClient;
    @Mock
    private CatastropheDateMatcher catastropheDateMatcher;

    @InjectMocks
    private InsuredEngagementOrchestrator insuredEngagementOrchestrator;

    private Map<String, Object> incidentPayload;

    @BeforeEach
    void setUp() {
        // Initialize isolated payload per test execution to ensure thread safety
        incidentPayload = new HashMap<>();
        incidentPayload.put("incident_id", "INC-7890");
        incidentPayload.put("storm_event_date", LocalDate.of(2023, 11, 20).toString());
        incidentPayload.put("claim_id", "CLM-4567");
        incidentPayload.put("exposure_id", "EXP-1234");
    }

    @Test
    void if_storm_event_date_matches_flag_for_catastrophe_tracking() {
        // Arrange: Mock external date matching logic
        when(catastropheDateMatcher.isMatch(any(LocalDate.class))).thenReturn(true);

        // Act: Trigger the orchestration decision
        Map<String, Object> decisionResult = insuredEngagementOrchestrator.evaluateDecision(incidentPayload);

        // Assert: Verify catastrophe tracking flag is set
        assertTrue((boolean) decisionResult.get("catastrophe_tracking_flag"),
                "Expected catastrophe tracking flag to be true when storm date matches");

        // Assert: Verify DynamoDB persistence for tracking flag (NFR: observability, compliance)
        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));

        // Assert: Verify SES notification for insured engagement (NFR: availability, TLS transit)
        verify(sesClient).sendEmail(any(SendEmailRequest.class));
    }
}

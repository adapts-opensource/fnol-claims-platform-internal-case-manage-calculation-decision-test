package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppliesWhenRuleUpdateRequestReceivedTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    @Mock
    private SesClient sesClient;

    @InjectMocks
    private MultiChannelFnolValidationService validationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and dependency injection
    }

    @Test
    void applies_when_rule_update_request_received() {
        // Arrange: Initialize submission state and rule update request
        Map<String, Object> submissionState = new HashMap<>();
        submissionState.put("id", "fnol-12345");
        submissionState.put("payload", Map.of("channel", "WEB", "claimType", "AUTO"));

        RuleUpdateRequest request = new RuleUpdateRequest();
        request.setRuleId("VALIDATION_RULE_01");
        request.setTrigger("RULE_UPDATE_RECEIVED");
        request.setPayload(Map.of("mode", "STRICT", "validateChannels", true));

        // Act: Evaluate validation rule against the state
        boolean ruleApplies = validationService.evaluateValidationRule(request, submissionState);

        // Assert: Verify rule applies when update request is received
        assertTrue(ruleApplies, "Validation rule must apply when rule update request is received");
        assertEquals(Map.of("mode", "STRICT", "validateChannels", true), request.getPayload());

        // Verify: Ensure no live AWS/HTTP calls are made during validation
        verifyNoInteractions(dynamoDbClient, s3Client, sesClient);
    }
}

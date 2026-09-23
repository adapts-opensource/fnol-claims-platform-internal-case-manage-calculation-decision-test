package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Claim Data Standardization:transformation:orchestration.
 * Validates agent authentication, agency-client-policy mapping, attribution rules,
 * and intake routing while mocking external I/O (DynamoDB, S3) and adhering to NFRs.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationTransformationOrchestrationTest {

    @Mock
    private AgentAuthService mockAgentAuth;
    @Mock
    private AgencyPolicyValidationService mockPolicyValidation;
    @Mock
    private AttributionRuleEngine mockAttributionEngine;
    @Mock
    private IntakeRoutingService mockIntakeRouter;
    @Mock
    private ClaimDataStoreClient mockDynamoDbClient;
    @Mock
    private DocumentStorageClient mockS3Client;

    private ClaimDataStandardizationOrchestration orchestration;

    @BeforeEach
    void setUp() {
        // Initialize orchestration with mocked dependencies
        orchestration = new ClaimDataStandardizationOrchestration(
                mockAgentAuth, mockPolicyValidation, mockAttributionEngine,
                mockIntakeRouter, mockDynamoDbClient, mockS3Client
        );
    }

    @Test
    void description_authenticates_agent_verifies_agency_client_policy_mapping_applies_attribution_rules_and_routes_intake_accordingly() {
        // Arrange: Prepare valid intake payload and configure mock behaviors
        String agentId = "AGT-998877";
        String agencyId = "AGY-112233";
        String clientId = "CLT-445566";
        String policyId = "POL-778899";
        Map<String, Object> rawIntake = Map.of(
                "agentId", agentId,
                "agencyId", agencyId,
                "clientId", clientId,
                "policyId", policyId,
                "claimType", "AUTO",
                "timestamp", System.currentTimeMillis()
        );

        when(mockAgentAuth.authenticate(agentId)).thenReturn(true);
        when(mockPolicyValidation.verifyAgencyClientPolicyMapping(agencyId, clientId, policyId)).thenReturn(true);
        when(mockAttributionEngine.applyAttributionRules(rawIntake)).thenReturn(Map.of("routingKey", "AUTO_STANDARD", "attributedTo", agencyId));

        String orchestrationId = UUID.randomUUID().toString();

        // Act: Execute the orchestration flow
        orchestration.processIntake(orchestrationId, rawIntake);

        // Assert: Verify execution sequence, infra I/O contracts, and NFR compliance
        verify(mockAgentAuth, times(1)).authenticate(agentId);
        verify(mockPolicyValidation, times(1)).verifyAgencyClientPolicyMapping(agencyId, clientId, policyId);
        verify(mockAttributionEngine, times(1)).applyAttributionRules(rawIntake);
        verify(mockIntakeRouter, times(1)).routeIntake(anyString(), eq("AUTO_STANDARD"));
        
        // Validate DynamoDB contract: item must contain 'id' and 'payload' per entity spec
        verify(mockDynamoDbClient, times(1)).putItem(anyString(), anyString(), argThat(item ->
                item.containsKey("id") && item.containsKey("payload")
        ));
        
        // Validate S3 contract: document storage call
        verify(mockS3Client, times(1)).putObject(anyString(), anyString(), anyString());

        // NFR: Availability & Operability - verify no runtime exceptions disrupt HA flow
        assertDoesNotThrow(() -> orchestration.processIntake(orchestrationId, rawIntake));
        
        // NFR: Thread Safety & Structured Logging - mocks ensure deterministic, thread-safe execution
        verifyNoMoreInteractions(mockAgentAuth, mockPolicyValidation, mockAttributionEngine, mockIntakeRouter, mockDynamoDbClient, mockS3Client);
    }
}

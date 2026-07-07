package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AgencyMappingMustExistForClientPolicyTest {

    @Mock
    private ClaimDataStoreDynamoDbMock dynamoDbMock;

    @Mock
    private AgencyMappingLookupService agencyMappingLookupService;

    private ClaimTransformationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimTransformationOrchestrator(dynamoDbMock, agencyMappingLookupService);
    }

    @Test
    void agency_mapping_must_exist_for_client_policy() {
        // Arrange
        String claimId = "CLM-98765";
        String clientId = "CLNT-11223";
        String policyId = "POL-44556";
        Map<String, Object> payload = Map.of(
            "clientId", clientId,
            "policyId", policyId,
            "claimType", "AUTO",
            "status", "INITIATED"
        );
        ClaimDataStandardizationStateTransitionOrch claimData = new ClaimDataStandardizationStateTransitionOrch(claimId, payload);

        // Simulate missing agency mapping in the lookup service
        when(agencyMappingLookupService.resolveAgencyMapping(clientId, policyId))
            .thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(
            AgencyMappingMissingException.class,
            () -> orchestrator.standardizeClaimData(claimData),
            "Orchestration must fail when agency mapping is missing for client/policy"
        );

        assertEquals(
            "Agency mapping not found for clientId=" + clientId + ", policyId=" + policyId,
            exception.getMessage()
        );

        // Verify no downstream writes occur when validation fails
        verifyNoInteractions(dynamoDbMock);
    }

    // Minimal supporting types for test isolation
    static class ClaimDataStandardizationStateTransitionOrch {
        private final String id;
        private final Map<String, Object> payload;

        ClaimDataStandardizationStateTransitionOrch(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = payload;
        }

        String getId() { return id; }
        Map<String, Object> getPayload() { return payload; }
    }

    interface AgencyMappingLookupService {
        Optional<Map<String, String>> resolveAgencyMapping(String clientId, String policyId);
    }

    interface ClaimDataStoreDynamoDbMock {
        void putItem(String tableName, Map<String, Object> item);
    }

    static class AgencyMappingMissingException extends RuntimeException {
        AgencyMappingMissingException(String message) { super(message); }
    }

    static class ClaimTransformationOrchestrator {
        private final ClaimDataStoreDynamoDbMock dynamoDbMock;
        private final AgencyMappingLookupService agencyMappingLookupService;

        ClaimTransformationOrchestrator(ClaimDataStoreDynamoDbMock dynamoDbMock, AgencyMappingLookupService agencyMappingLookupService) {
            this.dynamoDbMock = dynamoDbMock;
            this.agencyMappingLookupService = agencyMappingLookupService;
        }

        void standardizeClaimData(ClaimDataStandardizationStateTransitionOrch claimData) {
            Map<String, Object> payload = claimData.getPayload();
            String clientId = (String) payload.get("clientId");
            String policyId = (String) payload.get("policyId");

            if (clientId == null || policyId == null) {
                throw new IllegalArgumentException("clientId and policyId must be present in payload");
            }

            Optional<Map<String, String>> mapping = agencyMappingLookupService.resolveAgencyMapping(clientId, policyId);
            if (mapping.isEmpty()) {
                throw new AgencyMappingMissingException("Agency mapping not found for clientId=" + clientId + ", policyId=" + policyId);
            }

            // Simulate successful orchestration -> would write to DynamoDB
            dynamoDbMock.putItem("Claim Data Store_table", Map.of("id", claimData.getId(), "payload", payload));
        }
    }
}

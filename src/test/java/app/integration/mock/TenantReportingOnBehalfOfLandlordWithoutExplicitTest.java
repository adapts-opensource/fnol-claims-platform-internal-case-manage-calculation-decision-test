package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Infrastructure mocks per infra_io_contracts
interface DynamoDBPersistenceClient { String getItemPayload(String tableName, String partitionKey); }
interface SesCommunicationClient { String sendMessageId(String from, String to, String region); }
interface S3DocumentStoreClient { String getObjectUri(String bucket, String objectKeyPattern); }

// Business service under test
class TenantLandlordDecisionOrchestrator {
    private final DynamoDBPersistenceClient dynamoDB;
    private final SesCommunicationClient ses;
    private final S3DocumentStoreClient s3;

    TenantLandlordDecisionOrchestrator(DynamoDBPersistenceClient dynamoDB, SesCommunicationClient ses, S3DocumentStoreClient s3) {
        this.dynamoDB = dynamoDB;
        this.ses = ses;
        this.s3 = s3;
    }

    public DecisionOutcome evaluateDecision(String tenantId, String landlordId, boolean explicitAuthorization) {
        if (!explicitAuthorization) {
            return DecisionOutcome.REJECTED;
        }
        dynamoDB.getItemPayload("Data Persistence_table", "pk");
        ses.sendMessageId("verified@newco.com", "landlord@newco.com", "us-east-1");
        s3.getObjectUri("Document & Media Store-bucket", "Document & Media Store/{entity_id}.json");
        return DecisionOutcome.APPROVED;
    }
}

@ExtendWith(MockitoExtension.class)
class TenantReportingOnBehalfOfLandlordWithoutExplicitTest {

    @Mock
    private DynamoDBPersistenceClient dynamoDBClient;

    @Mock
    private SesCommunicationClient sesClient;

    @Mock
    private S3DocumentStoreClient s3Client;

    @InjectMocks
    private TenantLandlordDecisionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Reset mock state between tests to ensure thread-safety and isolation
        reset(dynamoDBClient, sesClient, s3Client);
    }

    @Test
    void tenant_reporting_on_behalf_of_landlord_without_explicit_authorization() {
        // Arrange
        String tenantId = "tenant_001";
        String landlordId = "landlord_001";
        boolean explicitAuthorization = false;

        // Act
        DecisionOutcome outcome = orchestrator.evaluateDecision(tenantId, landlordId, explicitAuthorization);

        // Assert
        assertEquals(DecisionOutcome.REJECTED, outcome, "Claim submission should be rejected when tenant acts for landlord without explicit authorization");

        // Verify no external I/O is invoked during rejection path (NFR: security, observability, least_privilege_iam)
        verifyNoInteractions(dynamoDBClient, sesClient, s3Client);
    }
}

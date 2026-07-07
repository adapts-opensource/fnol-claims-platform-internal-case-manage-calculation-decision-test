package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;
import java.util.HashMap;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

// Data model entity
class MultiChannelFnolSubmissionStateTransitionC {
    private final String id;
    private final Map<String, Object> payload;

    public MultiChannelFnolSubmissionStateTransitionC(String id, Map<String, Object> payload) {
        this.id = id;
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}

// Repository interface for persistence
interface FnolSubmissionRepository {
    MultiChannelFnolSubmissionStateTransitionC save(MultiChannelFnolSubmissionStateTransitionC entity);
}

// Service under test
class MultiChannelFnolSubmissionService {
    private final S3Client s3Client;
    private final SesClient sesClient;
    private final DynamoDbClient dynamoDbClient;
    private final FnolSubmissionRepository repository;

    public MultiChannelFnolSubmissionService(S3Client s3Client, SesClient sesClient, DynamoDbClient dynamoDbClient, FnolSubmissionRepository repository) {
        this.s3Client = s3Client;
        this.sesClient = sesClient;
        this.dynamoDbClient = dynamoDbClient;
        this.repository = repository;
    }

    public String calculateStateTransition(String entityId, Map<String, Object> payload) {
        // Infra I/O: S3
        s3Client.putObject(PutObjectRequest.builder()
                .bucket("Claim Intake Service-bucket")
                .key("Claim Intake Service/" + entityId + ".json")
                .build(), null);

        // Infra I/O: SES
        sesClient.sendEmail(SendEmailRequest.builder()
                .fromAddress("noreply@newco.com")
                .toAddresses("claims@newco.com")
                .build());

        // Infra I/O: DynamoDB
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName("Data Store_table")
                .putAllItemAttributes(Map.of("pk", entityId))
                .build());

        // State transition calculation logic
        boolean hasPolicyNumber = payload.containsKey("policyNumber");
        boolean hasAddress = payload.containsKey("address");
        boolean hasInsuredIdentity = payload.containsKey("insuredIdentity");

        String newState = "PENDING_INTAKE";
        if (hasPolicyNumber || hasAddress || hasInsuredIdentity) {
            newState = "CALCULATED";
        }

        MultiChannelFnolSubmissionStateTransitionC entity = new MultiChannelFnolSubmissionStateTransitionC(entityId, payload);
        repository.save(entity);
        return newState;
    }
}

@ExtendWith(MockitoExtension.class)
public class StateTransitionCalculationMockTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private SesClient sesClient;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private FnolSubmissionRepository fnolSubmissionRepository;

    private MultiChannelFnolSubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new MultiChannelFnolSubmissionService(s3Client, sesClient, dynamoDbClient, fnolSubmissionRepository);
    }

    @Test
    void applies_when_claim_submission_includes_policy_number_address_or_insured_identity() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("policyNumber", "POL-123456");
        payload.put("address", "123 Main St");
        payload.put("insuredIdentity", "John Doe");
        String entityId = "entity-1";

        // Mock external I/O to prevent live AWS/production calls
        when(s3Client.putObject(any(PutObjectRequest.class), any())).thenReturn(mock(software.amazon.awssdk.services.s3.model.PutObjectResponse.class));
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(mock(software.amazon.awssdk.services.ses.model.SendEmailResponse.class));
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(mock(software.amazon.awssdk.services.dynamodb.model.PutItemResponse.class));
        when(fnolSubmissionRepository.save(any())).thenReturn(new MultiChannelFnolSubmissionStateTransitionC(entityId, payload));

        // When
        String resultState = submissionService.calculateStateTransition(entityId, payload);

        // Then
        assertEquals("CALCULATED", resultState);
        verify(s3Client).putObject(any(PutObjectRequest.class), any());
        verify(sesClient).sendEmail(any(SendEmailRequest.class));
        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        verify(fnolSubmissionRepository).save(any());
    }
}

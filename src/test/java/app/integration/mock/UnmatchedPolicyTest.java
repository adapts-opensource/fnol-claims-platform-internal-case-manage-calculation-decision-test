package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class UnmatchedPolicyValidationMockTest {

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private SesClient sesClient;
    @Mock
    private RedisClient redisClient;
    @Mock
    private ClaimRoutingValidationService validationService;

    @InjectMocks
    private ClaimInitiationService claimInitiationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection and stubbing lifecycle
    }

    @Test
    void validate_unmatched_policy_routing() {
        // Given
        String policyNumber = "INVALID-999";
        String riskAddress = "123 Oak Ave";
        LocalDate lossDate = LocalDate.of(2024, 5, 15);
        String namedInsured = "Alice Smith";

        // Mock external I/O: DynamoDB policy lookup returns no match
        when(dynamoDbClient.getItem(anyString(), anyString())).thenReturn(null);
        // Mock task creation & shell persistence
        when(validationService.createResolveTask(policyNumber)).thenReturn(true);
        when(validationService.persistIntakeShell(any(Map.class))).thenReturn(true);

        // When
        Map<String, Object> result = claimInitiationService.processClaimInitiation(policyNumber, riskAddress, lossDate, namedInsured);

        // Then
        assertEquals("Unmatched Policy", result.get("claim_status"));
        assertNull(result.get("claim_number"));
        assertTrue((Boolean) result.get("resolve_task_created"));
        assertTrue((Boolean) result.get("intake_shell_persisted"));

        // Verify interactions
        verify(dynamoDbClient, times(1)).getItem(anyString(), anyString());
        verify(validationService, times(1)).createResolveTask(policyNumber);
        verify(validationService, times(1)).persistIntakeShell(any(Map.class));
    }
}

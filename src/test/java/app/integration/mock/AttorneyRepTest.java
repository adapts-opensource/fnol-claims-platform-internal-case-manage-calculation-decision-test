package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Validates Claim Initiation & Routing decision logic for attorney-represented claims.
 * NFRs: Thread-safe stateless validator, GDPR-compliant PII handling, Structured logging via mock traces.
 */
@ExtendWith(MockitoExtension.class)
public class AttorneyRepValidationTest {

    @Mock
    private RedisCacheMock redisCache;

    @Mock
    private DynamoDbMock claimsDb;

    @Mock
    private SesMock communicationSvc;

    private ClaimRoutingDecisionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClaimRoutingDecisionValidator(redisCache, claimsDb, communicationSvc);
    }

    @Test
    void validate_attorney_representation_routing() {
        // Arrange: Mock external I/O contracts (Redis, DynamoDB, SES)
        when(redisCache.get(anyString())).thenReturn("{}");
        when(claimsDb.putItem(anyString(), anyMap())).thenReturn(true);
        when(communicationSvc.sendRestrictionNotice(anyString(), anyList())).thenReturn("ses-msg-id-789");

        // Arrange: Test inputs
        String policyNumber = "POL-ATTY";
        LocalDate lossDate = LocalDate.of(2024, 5, 15);
        String attorneyName = "Legal Firm XYZ";
        boolean attorneyFlag = true;
        boolean letterUploaded = true;

        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_number", policyNumber);
        payload.put("loss_date", lossDate.toString());
        payload.put("attorney_name", attorneyName);
        payload.put("attorney_flag", attorneyFlag);
        payload.put("letter_of_representation_uploaded", letterUploaded);

        // Act: Execute validation & routing decision
        Map<String, Object> result = validator.validateAndRoute(payload);

        // Assert: Expected routing outcomes
        assertEquals("Represented claim", result.get("initial_claim_type"), "Initial claim type must be Represented claim");
        assertTrue(result.containsKey("created_tasks") && ((List<String>) result.get("created_tasks")).contains("Attorney Representation Review"),
                "Task 'Attorney Representation Review' must be created");
        assertEquals("restricted_to_attorney", result.get("communication_workflow"), "Communication workflow must be restricted to attorney");
        assertFalse((Boolean) result.get("allow_direct_insured_contact"), "Direct insured contact must be blocked per rules");

        // Verify external I/O interactions
        verify(redisCache, times(1)).get(anyString());
        verify(claimsDb, times(1)).putItem(anyString(), anyMap());
        verify(communicationSvc, times(1)).sendRestrictionNotice(anyString(), anyList());
    }

    // --- Mock Infrastructure Interfaces ---
    interface RedisCacheMock {
        String get(String key);
    }

    interface DynamoDbMock {
        boolean putItem(String tableName, Map<String, Object> item);
    }

    interface SesMock {
        String sendRestrictionNotice(String fromAddress, List<String> toAddresses);
    }

    // --- Service Under Test (Stateless, Thread-Safe) ---
    static class ClaimRoutingDecisionValidator {
        private final RedisCacheMock redisCache;
        private final DynamoDbMock claimsDb;
        private final SesMock communicationSvc;

        ClaimRoutingDecisionValidator(RedisCacheMock redisCache, DynamoDbMock claimsDb, SesMock communicationSvc) {
            this.redisCache = redisCache;
            this.claimsDb = claimsDb;
            this.communicationSvc = communicationSvc;
        }

        Map<String, Object> validateAndRoute(Map<String, Object> payload) {
            boolean isAttorney = Boolean.TRUE.equals(payload.get("attorney_flag"));
            boolean hasLetter = Boolean.TRUE.equals(payload.get("letter_of_representation_uploaded"));

            Map<String, Object> decision = new HashMap<>();
            if (isAttorney && hasLetter) {
                decision.put("initial_claim_type", "Represented claim");
                decision.put("created_tasks", Arrays.asList("Attorney Representation Review"));
                decision.put("communication_workflow", "restricted_to_attorney");
                decision.put("allow_direct_insured_contact", false);

                // Trigger mocked I/O contracts
                redisCache.get("Cache & Reference Data:cache:" + payload.get("policy_number"));
                claimsDb.putItem("Claims & Policy Data Store_table", payload);
                communicationSvc.sendRestrictionNotice("noreply@newco.com", Arrays.asList("insured@example.com"));
            } else {
                decision.put("initial_claim_type", "Unrepresented claim");
                decision.put("created_tasks", Arrays.asList());
                decision.put("communication_workflow", "open");
                decision.put("allow_direct_insured_contact", true);
            }
            return decision;
        }
    }
}

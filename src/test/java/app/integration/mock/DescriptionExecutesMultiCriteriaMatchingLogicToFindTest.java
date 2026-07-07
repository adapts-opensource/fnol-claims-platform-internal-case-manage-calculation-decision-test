package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration tests for Claim Initiation & Routing:orchestration:decision.
 * Verifies multi-criteria policy matching logic including active/recently-expired states,
 * exact/fuzzy address matching, named insured matching, and conflict detection.
 *
 * NFR Coverage:
 * - Security: Input validation, structured logging.
 * - Observability: Verifies logging of conflicts and matching decisions.
 * - Concurrency: Mock setup supports thread-safe interaction patterns.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionMockTest {

    @Mock
    private ClaimsPolicyDataStoreClient policyDataStore;

    @Mock
    private CacheReferenceDataClient referenceDataCache;

    @Mock
    private InputValidator inputValidator;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Logger structuredLogger;

    @InjectMocks
    private ClaimInitiationOrchestrator orchestrator;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @Captor
    private ArgumentCaptor<String> cacheKeyCaptor;

    @BeforeEach
    void setUp() {
        // Reset mocks to ensure isolation between test scenarios
        reset(policyDataStore, referenceDataCache, inputValidator, securityContext, structuredLogger);
    }

    @Test
    void description_executes_multi_criteria_matching_logic_to_find_active_or_recently_expired_policies_handles_exact_matches_fuzzy_address_matches_and_named_insured_matching_identifies_conflicts_where_multiple_policies_match() {
        // Arrange: Prepare payload with insured details triggering multi-criteria matching
        Map<String, Object> payload = new HashMap<>();
        payload.put("insuredName", "John A. Doe");
        payload.put("addressLine1", "123 Main Street");
        payload.put("city", "Springfield");
        payload.put("state", "IL");
        payload.put("zipCode", "62704");
        payload.put("vehicleVin", "1HGBH41JXMN109186");

        // NFR: Security - Input Validation
        when(inputValidator.validate(anyMap())).thenReturn(true);
        
        // NFR: Security - Least Privilege / Access Check
        when(securityContext.hasAccess(anyString(), anyString())).thenReturn(true);

        // Arrange: Mock Reference Data Cache (Redis) for address normalization/validation
        String cacheKey = "Cache & Reference Data:cache:address_validation:123 Main Street,Springfield,IL,62704";
        when(referenceDataCache.get(anyString())).thenAnswer(invocation -> {
            cacheKeyCaptor.capture();
            return "NORMALIZED:123 Main St,Springfield,IL,62704";
        });
        when(referenceDataCache.get(eq(cacheKey))).thenReturn("VALID");

        // Arrange: Mock DynamoDB Policy Data Store
        // Policy 1: Active, Exact Name, Exact Address
        Map<String, Object> policy1 = new HashMap<>();
        policy1.put("pk", "POLICY#1001");
        policy1.put("status", "ACTIVE");
        policy1.put("insuredName", "John A. Doe");
        policy1.put("address", "123 Main St,Springfield,IL,62704");
        policy1.put("effectiveDate", "2023-01-01");
        policy1.put("expirationDate", "2024-01-01");

        // Policy 2: Recently Expired, Exact Name, Fuzzy Address (Trailing space/typo simulation)
        Map<String, Object> policy2 = new HashMap<>();
        policy2.put("pk", "POLICY#1002");
        policy2.put("status", "EXPIRED");
        policy2.put("expiryDate", "2023-11-15"); // Within recent window
        policy2.put("insuredName", "John A. Doe");
        policy2.put("address", "123 Main Street, Springfield, IL 62704"); // Fuzzy match candidate
        policy2.put("effectiveDate", "2022-11-15");
        policy2.put("expirationDate", "2023-11-15");

        // Policy 3: Active, Exact Name, Different Address (Conflict scenario)
        Map<String, Object> policy3 = new HashMap<>();
        policy3.put("pk", "POLICY#1003");
        policy3.put("status", "ACTIVE");
        policy3.put("insuredName", "John A. Doe");
        policy3.put("address", "456 Elm St,Springfield,IL,62705"); // Conflict: Same name, different address
        policy3.put("effectiveDate", "2023-06-01");
        policy3.put("expirationDate", "2024-06-01");

        // Mock DynamoDB query returning multiple matches to trigger conflict logic
        when(policyDataStore.query(any(QueryRequest.class)))
            .thenReturn(Arrays.asList(policy1, policy2, policy3));

        // Act: Execute decision orchestration
        ClaimDecisionResult result = orchestrator.processDecision(payload);

        // Assert: Verify Input Validation was called
        verify(inputValidator).validate(payloadCaptor.capture());
        assertEquals("John A. Doe", payloadCaptor.getValue().get("insuredName"));

        // Assert: Verify Security Context Check
        verify(securityContext).hasAccess("CLAIM_INITIATION", "DECISION_ROUTING");

        // Assert: Verify Reference Data Cache interaction
        verify(referenceDataCache).get(eq(cacheKey));
        assertTrue(cacheKeyCaptor.getValue().startsWith("Cache & Reference Data:cache:"));

        // Assert: Verify DynamoDB Query execution
        verify(policyDataStore).query(any(QueryRequest.class));

        // Assert: Verify Result Structure
        assertNotNull(result);
        assertTrue(result.isConflict(), "Expected conflict due to multiple matching policies");
        assertEquals(3, result.getMatches().size(), "Expected 3 policies to be returned in conflict resolution");

        // Assert: Verify Matching Criteria Logic
        List<PolicyMatch> matches = result.getMatches();
        
        // Check for Exact Match (Policy 1)
        PolicyMatch exactMatch = matches.stream()
            .filter(m -> "POLICY#1001".equals(m.getPolicyId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Exact match policy not found"));
        assertEquals(MatchScore.HIGH, exactMatch.getMatchScore());
        assertEquals(MatchType.EXACT_ADDRESS, exactMatch.getMatchType());

        // Check for Fuzzy/Recent Match (Policy 2)
        PolicyMatch recentFuzzyMatch = matches.stream()
            .filter(m -> "POLICY#1002".equals(m.getPolicyId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Recently expired fuzzy match not found"));
        assertEquals(MatchScore.MEDIUM, recentFuzzyMatch.getMatchScore());
        assertEquals(MatchType.FUZZY_ADDRESS_RECENT_EXPIRY, recentFuzzyMatch.getMatchType());

        // Check for Conflict Match (Policy 3)
        PolicyMatch conflictMatch = matches.stream()
            .filter(m -> "POLICY#1003".equals(m.getPolicyId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Conflict policy not found"));
        assertEquals(MatchScore.LOW, conflictMatch.getMatchScore());
        assertEquals(MatchType.NAME_ONLY_CONFLICT, conflictMatch.getMatchType());

        // Assert: Verify Structured Logging (Observability)
        verify(structuredLogger).warn(
            argThat(logMessage -> logMessage.contains("CONFLICT_DETECTED") && 
                                   logMessage.contains("MULTIPLE_POLICIES_MATCH")),
            anyMap()
        );
    }
}

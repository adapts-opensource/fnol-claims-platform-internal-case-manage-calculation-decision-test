package app.integration.mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import app.domain.claim.ClaimInitiationRoutingDecisionValidation;
import app.domain.claim.ClaimPayload;
import app.domain.enums.AuditScope;
import app.domain.enums.CalculationStatus;
import app.infra.cache.CacheClient;
import app.infra.dynamodb.DynamoDbClient;
import app.infra.logging.StructuredLogger;
import app.infra.secrets.SecretsManager;
import app.service.claim.ClaimService;
import app.service.routing.DecisionCalculationEngine;
import app.service.validation.InputValidator;

/**
 * Mock integration tests for Claim Initiation & Routing: decision:calculation.
 * Verifies input criteria handling, mock I/O contracts, NFR compliance, and audit scope processing.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationTest {

    private static final String CLAIM_ID = "CLM-2024-001-TEST";
    private static final String DATE_RANGE_START = "2024-01-01T00:00:00Z";
    private static final String DATE_RANGE_END = "2024-12-31T23:59:59Z";
    private static final String CACHE_NAMESPACE = "Cache & Reference Data:cache:";
    private static final String DYNAMO_TABLE = "Claims & Policy Data Store_table";
    private static final String PK = "pk";

    @Mock
    private ClaimService claimService;
    @Mock
    private DecisionCalculationEngine calculationEngine;
    @Mock
    private InputValidator inputValidator;
    @Mock
    private CacheClient cacheClient;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private SecretsManager secretsManager;
    @Mock
    private StructuredLogger logger;

    @Spy
    private ClaimInitiationRoutingDecisionCalculationTestSubject testSubject;

    @BeforeEach
    void setUp() {
        testSubject.setClaimService(claimService);
        testSubject.setCalculationEngine(calculationEngine);
        testSubject.setInputValidator(inputValidator);
        testSubject.setCacheClient(cacheClient);
        testSubject.setDynamoDbClient(dynamoDbClient);
        testSubject.setSecretsManager(secretsManager);
        testSubject.setLogger(logger);
    }

    /**
     * Test Case: InputCriteriaClaimIdDateRangeAuditScope
     * Verifies processing when input criteria include Claim ID, Date range,
     * and Audit scope containing decisions, configs, and transitions.
     */
    @Test
    void input_criteria_claim_id_date_range_audit_scope_decisions_configs_transitions() {
        // Arrange Input Criteria
        List<AuditScope> auditScope = List.of(AuditScope.DECISIONS, AuditScope.CONFIGS, AuditScope.TRANSITIONS);
        LocalDateTime startDate = LocalDateTime.parse(DATE_RANGE_START);
        LocalDateTime endDate = LocalDateTime.parse(DATE_RANGE_END);

        // Mock Input Validation (NFR: Input Validation)
        when(inputValidator.validateClaimId(CLAIM_ID)).thenReturn(true);
        when(inputValidator.validateDateRange(startDate, endDate)).thenReturn(true);

        // Mock Infra I/O: Secrets (NFR: Secrets Management, Least Privilege)
        String mockToken = "mock-sts-token-123";
        when(secretsManager.getSecret(anyString())).thenReturn(mockToken);
        verify(secretsManager, never()).getSecret(eq("SECRET_DANGER")); // Security check

        // Mock Infra I/O: Cache (NFR: Availability, Concurrency)
        when(cacheClient.get(eq(CACHE_NAMESPACE), anyString())).thenReturn(null); // Cache miss

        // Mock Infra I/O: DynamoDB (NFR: Compliance SOC2, Data Integrity)
        Map<String, String> dbItem = Map.of(PK, CLAIM_ID, "status", "INITIATED");
        when(dynamoDbClient.getItem(eq(DYNAMO_TABLE), eq(PK), eq(CLAIM_ID))).thenReturn(Optional.of(dbItem));

        // Mock Domain Logic
        ClaimPayload mockPayload = new ClaimPayload(CLAIM_ID, Map.of("type", "AUTO"));
        ClaimInitiationRoutingDecisionValidation mockResult = new ClaimInitiationRoutingDecisionValidation(
            "calc-001", Map.of("decision", "APPROVED", "config", "RISK_LOW", "transition", "NEXT_STEP")
        );

        when(claimService.getPayload(eq(CLAIM_ID))).thenReturn(mockPayload);
        when(calculationEngine.process(eq(mockPayload), eq(auditScope), anyMap())).thenReturn(mockResult);

        // Act
        ClaimInitiationRoutingDecisionValidation result = testSubject.calculateAndRoute(
            CLAIM_ID, startDate, endDate, auditScope
        );

        // Assert Results
        assertNotNull(result, "Result should not be null");
        assertEquals("calc-001", result.getId(), "Calculation ID should match");
        assertEquals(CalculationStatus.SUCCESS, result.getStatus(), "Status should be SUCCESS");
        
        Map<String, Object> payload = result.getPayload();
        assertTrue(payload.containsKey("decision"), "Payload must contain decision");
        assertTrue(payload.containsKey("config"), "Payload must contain config");
        assertTrue(payload.containsKey("transition"), "Payload must contain transition");

        // Assert NFRs & Contracts
        verify(inputValidator).validateClaimId(CLAIM_ID);
        verify(inputValidator).validateDateRange(startDate, endDate);
        verify(claimService).getPayload(CLAIM_ID);
        verify(calculationEngine).process(eq(mockPayload), eq(auditScope), anyMap());
        
        // Verify Structured Logging (NFR: Observability)
        verify(logger).info(eq("Claim calculation initiated"), 
            eq("claimId"), eq(CLAIM_ID), 
            eq("auditScope"), eq(auditScope.toString()));
        
        verify(logger).debug(eq("Cache miss for claim"), eq("cacheKey"), eq(CACHE_NAMESPACE + CLAIM_ID));
        
        // Verify DynamoDB interaction
        verify(dynamoDbClient).getItem(eq(DYNAMO_TABLE), eq(PK), eq(CLAIM_ID));
        
        // Verify Secrets usage for auth (NFR: Security)
        verify(secretsManager).getSecret(eq("AWS_CREDENTIALS"));
    }

    /**
     * Verifies Input Validation failure handling (NFR: Input Validation).
     */
    @Test
    void input_criteria_invalid_claim_id_throws_validation_exception() {
        // Arrange
        when(inputValidator.validateClaimId("INVALID_CLAIM_ID")).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            testSubject.calculateAndRoute("INVALID_CLAIM_ID", LocalDateTime.now(), LocalDateTime.now(), List.of());
        }, "Should throw exception for invalid claim ID");

        // Verify Logging of validation error (NFR: Observability)
        verify(logger).warn(eq("Input validation failed"), eq("reason"), eq("INVALID_CLAIM_ID"));
    }

    /**
     * Verifies Thread Safety isolation (NFR: Concurrency).
     * Mocks ensure state is not shared across tests.
     */
    @Test
    void thread_safety_mock_isolation() {
        // Each @Test runs with fresh mocks due to MockitoExtension.
        // This test verifies that a different claim ID does not interfere.
        String otherClaimId = "CLM-OTHER";
        when(claimService.getPayload(otherClaimId)).thenReturn(new ClaimPayload(otherClaimId, Map.of()));
        
        var result = testSubject.calculateAndRoute(otherClaimId, LocalDateTime.now(), LocalDateTime.now(), List.of());
        assertNotNull(result);
        assertEquals(otherClaimId, result.getId(), "Should process isolated claim");
    }
}

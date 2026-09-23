package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock-based unit tests for Claim Initiation & Routing:decision:calculation.
 * Verifies routing logic under mocked infra contracts while enforcing NFRs:
 * - Thread safety: stateless SUT, JUnit 5 per-test instance isolation
 * - Input validation: strict payload schema enforcement
 * - Observability: structured key-value logging with MDC context
 * - Security: no PII in logs, mocked secrets/credentials, least-privilege infra mocks
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private Logger logger;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDBClaimsStore dynamoDBClaimsStore;

    @Mock
    private SesNotificationService sesNotificationService;

    private ClaimRoutingDecisionCalculator calculator;

    private static final String CACHE_NAMESPACE = "Cache & Reference Data:cache:";
    private static final String DYNAMO_TABLE = "Claims & Policy Data Store_table";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    @BeforeEach
    void setUp() {
        calculator = new ClaimRoutingDecisionCalculator(logger, redisCacheService, dynamoDBClaimsStore, sesNotificationService);
        MDC.clear(); // Ensure thread-safe logging context isolation per test
    }

    @Test
    void date_of_loss_outside_policy_period_route_to_coverage_review() {
        // Arrange: construct valid payload per claim_initiation___routing_decision_validation model
        String claimId = "claim-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("dateOfLoss", "2022-01-15"); // Outside policy period
        payload.put("policyStartDate", "2023-06-01");
        payload.put("policyEndDate", "2024-06-01");

        // Mock infra I/O contracts (Redis & DynamoDB)
        when(redisCacheService.get(eq(CACHE_NAMESPACE + "validation_rules"))).thenReturn("ENABLED");
        when(dynamoDBClaimsStore.getItem(eq(DYNAMO_TABLE), eq("pk_" + claimId))).thenReturn(Map.of("coverageType", "AUTO"));

        MDC.put("claimId", claimId); // Structured logging context

        // Act: calculate routing decision
        Map<String, Object> result = calculator.calculateRoutingDecision(payload);

        // Assert: verify routing target and payload structure
        assertEquals("COVERAGE_REVIEW", result.get("routingTarget"), "Should route to coverage review when date of loss is outside policy period");
        assertEquals("VALIDATED", result.get("status"));
        assertFalse((boolean) result.get("isDateOfLossValid"), "Date validation should flag out-of-period loss");
        assertEquals(payload, result.get("payload"));

        // Verify infra interactions & structured logging
        verify(logger).info("Claim routing decision calculated", "claimId", claimId, "routingTarget", "COVERAGE_REVIEW");
        verify(redisCacheService).get(eq(CACHE_NAMESPACE + "validation_rules"));
        verify(dynamoDBClaimsStore).getItem(eq(DYNAMO_TABLE), eq("pk_" + claimId));
        verifyNoInteractions(sesNotificationService, "SES should not be invoked during routing calculation");
    }

    /**
     * System Under Test: Routing decision calculator.
     * Stateless, thread-safe, validates inputs, uses structured logging.
     */
    static class ClaimRoutingDecisionCalculator {
        private final Logger logger;
        private final RedisCacheService redisCacheService;
        private final DynamoDBClaimsStore dynamoDBClaimsStore;
        private final SesNotificationService sesNotificationService;

        ClaimRoutingDecisionCalculator(Logger logger, RedisCacheService redisCacheService,
                                       DynamoDBClaimsStore dynamoDBClaimsStore,
                                       SesNotificationService sesNotificationService) {
            this.logger = logger;
            this.redisCacheService = redisCacheService;
            this.dynamoDBClaimsStore = dynamoDBClaimsStore;
            this.sesNotificationService = sesNotificationService;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> calculateRoutingDecision(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            String dateOfLossStr = (String) payload.get("dateOfLoss");
            String policyStartStr = (String) payload.get("policyStartDate");
            String policyEndStr = (String) payload.get("policyEndDate");

            // Input validation (NFR: input_validation)
            if (claimId == null || dateOfLossStr == null || policyStartStr == null || policyEndStr == null) {
                throw new IllegalArgumentException("Payload must contain id, dateOfLoss, policyStartDate, and policyEndDate");
            }

            LocalDate dateOfLoss = LocalDate.parse(dateOfLossStr, DATE_FMT);
            LocalDate policyStart = LocalDate.parse(policyStartStr, DATE_FMT);
            LocalDate policyEnd = LocalDate.parse(policyEndStr, DATE_FMT);

            // Structured logging (NFR: observability)
            logger.info("Claim routing decision calculated", "claimId", claimId, "dateOfLoss", dateOfLossStr);

            // Infra I/O: cache & reference data lookup
            redisCacheService.get(CACHE_NAMESPACE + "validation_rules");
            dynamoDBClaimsStore.getItem(DYNAMO_TABLE, "pk_" + claimId);

            // Routing logic
            boolean isOutsidePeriod = dateOfLoss.isBefore(policyStart) || dateOfLoss.isAfter(policyEnd);
            String routingTarget = isOutsidePeriod ? "COVERAGE_REVIEW" : "STANDARD_UNDERWRITING";

            Map<String, Object> result = new HashMap<>();
            result.put("id", claimId);
            result.put("routingTarget", routingTarget);
            result.put("status", "VALIDATED");
            result.put("isDateOfLossValid", !isOutsidePeriod);
            result.put("payload", payload);

            return result;
        }
    }

    // Mocked infra interfaces aligned with infra_io_contracts
    interface RedisCacheService { String get(String key); }
    interface DynamoDBClaimsStore { Map<String, Object> getItem(String tableName, String pk); }
    interface SesNotificationService { String send(String from, java.util.List<String> to, String region); }
}

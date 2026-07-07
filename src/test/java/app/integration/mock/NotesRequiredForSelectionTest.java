package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimInitiationRoutingDecisionValidationTest {

    private static final Logger logger = LoggerFactory.getLogger(ClaimInitiationRoutingDecisionValidationTest.class);

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    private RoutingDecisionValidator validator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new RoutingDecisionValidator(redisCacheService, dynamoDbService);
        logger.info("Test setup completed. Mocks initialized for Redis and DynamoDB.");
    }

    @Nested
    @DisplayName("NotesRequiredForSelection")
    class NotesRequiredForSelection {

        @Test
        @DisplayName("notes_required_for_selection")
        void notes_required_for_selection() {
            // Arrange: Payload missing required notes field
            String claimId = "CLM-7890";
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", claimId);
            payload.put("notes", null);

            // Act & Assert: Validation must fail when notes are absent
            assertThrows(ValidationException.class, () -> validator.validate(payload));
            logger.info("Validation correctly rejected claim {} due to missing notes.", claimId);
        }

        @Test
        @DisplayName("notes_required_for_selection")
        void notes_required_for_selection_with_valid_payload() {
            // Arrange: Payload includes notes
            String claimId = "CLM-1234";
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", claimId);
            payload.put("notes", "Insured reported rear-end collision on interstate.");

            // Act
            Map<String, Object> result = validator.validate(payload);

            // Assert
            assertNotNull(result, "Validation result should not be null");
            assertEquals("APPROVED", result.get("routingDecision"));
            assertEquals(claimId, result.get("id"));
            logger.info("Validation passed for claim {} and triggered routing decision.", claimId);
        }
    }

    // Infrastructure Service Interfaces (Mocked via Mockito to avoid live AWS calls)
    interface RedisCacheService {
        void put(String namespace, String key, int ttl);
    }

    interface DynamoDbService {
        void putItem(String tableName, Map<String, Object> item);
    }

    // Domain Orchestration Logic
    static class RoutingDecisionValidator {
        private final RedisCacheService cacheService;
        private final DynamoDbService dynamoDbService;

        RoutingDecisionValidator(RedisCacheService cacheService, DynamoDbService dynamoDbService) {
            this.cacheService = cacheService;
            this.dynamoDbService = dynamoDbService;
        }

        Map<String, Object> validate(Map<String, Object> payload) {
            if (payload == null) {
                throw new ValidationException("Payload cannot be null.");
            }

            String notes = payload.get("notes") != null ? payload.get("notes").toString() : null;
            if (notes == null || notes.isBlank()) {
                throw new ValidationException("Notes are required for selection.");
            }

            // Mocked infra I/O contracts (Redis & DynamoDB)
            cacheService.put("Cache & Reference Data:cache:", payload.get("id").toString(), 3600);
            dynamoDbService.putItem("Claims & Policy Data Store_table", payload);

            Map<String, Object> decision = new HashMap<>();
            decision.put("id", payload.get("id"));
            decision.put("routingDecision", "APPROVED");
            return decision;
        }
    }

    static class ValidationException extends RuntimeException {
        ValidationException(String message) {
            super(message);
        }
    }
}

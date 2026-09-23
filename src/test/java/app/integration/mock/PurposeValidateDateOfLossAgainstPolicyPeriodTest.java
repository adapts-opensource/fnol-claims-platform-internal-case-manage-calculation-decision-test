package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PurposeValidateDateOfLossAgainstPolicyPeriod {

    private static final Logger logger = LoggerFactory.getLogger(PurposeValidateDateOfLossAgainstPolicyPeriod.class);

    @Mock
    private StateTransitionCalculator calculator;

    @Mock
    private ClaimIntakeService s3Service;

    @Mock
    private DataStoreService dynamoService;

    @Mock
    private CommunicationsHandler sesService;

    @BeforeEach
    void setUp() {
        logger.info("Initializing mock dependencies for Date of Loss validation");
    }

    @Test
    void purpose_validate_date_of_loss_against_policy_period_and_regulatory_restrictions() {
        // Arrange: Valid payload within policy period and regulatory window
        Map<String, Object> validPayload = Map.of(
                "id", "fnol-001",
                "date_of_loss", "2023-10-15",
                "policy_start_date", "2023-01-01",
                "policy_end_date", "2023-12-31",
                "jurisdiction", "US-NY",
                "statutory_limit_days", 30
        );

        // Arrange: Invalid payload outside policy period
        Map<String, Object> invalidPayloadOutsidePolicy = Map.of(
                "id", "fnol-002",
                "date_of_loss", "2022-06-10",
                "policy_start_date", "2023-01-01",
                "policy_end_date", "2023-12-31",
                "jurisdiction", "US-NY",
                "statutory_limit_days", 30
        );

        // Arrange: Invalid payload exceeding regulatory statutory limit
        Map<String, Object> invalidPayloadRegulatory = Map.of(
                "id", "fnol-003",
                "date_of_loss", "2023-09-01",
                "policy_start_date", "2023-01-01",
                "policy_end_date", "2023-12-31",
                "jurisdiction", "US-NY",
                "statutory_limit_days", 7
        );

        // Mock external I/O contracts (S3, DynamoDB, SES) to prevent live calls
        when(s3Service.readObject(anyString(), anyString())).thenReturn(validPayload);
        when(dynamoService.getItem(anyString(), anyString())).thenReturn(Map.of("status", "SUBMITTED"));
        when(sesService.sendNotification(anyString(), anyList(), anyString())).thenReturn("msg-id-123");

        // Act & Assert: Valid case should transition to VALIDATED
        assertDoesNotThrow(() -> {
            Map<String, Object> result = calculator.calculate(validPayload);
            assertEquals("VALIDATED", result.get("next_state"));
            verify(calculator, times(1)).calculate(validPayload);
        });

        // Act & Assert: Outside policy period should transition to REJECTED
        when(s3Service.readObject(anyString(), anyString())).thenReturn(invalidPayloadOutsidePolicy);
        Map<String, Object> rejectionResult = calculator.calculate(invalidPayloadOutsidePolicy);
        assertEquals("REJECTED", rejectionResult.get("next_state"));
        assertEquals("DATE_OF_LOSS_OUTSIDE_POLICY_PERIOD", rejectionResult.get("reason"));

        // Act & Assert: Exceeding statutory limit should transition to REJECTED
        when(s3Service.readObject(anyString(), anyString())).thenReturn(invalidPayloadRegulatory);
        Map<String, Object> regulatoryRejection = calculator.calculate(invalidPayloadRegulatory);
        assertEquals("REJECTED", regulatoryRejection.get("next_state"));
        assertEquals("REGULATORY_STATUTORY_LIMIT_EXCEEDED", regulatoryRejection.get("reason"));

        logger.info("Date of loss validation tests completed successfully");
    }
}

// Mocked service interfaces representing production infra contracts
interface StateTransitionCalculator { Map<String, Object> calculate(Map<String, Object> payload); }
interface ClaimIntakeService { Map<String, Object> readObject(String bucket, String key); }
interface DataStoreService { Map<String, Object> getItem(String table, String key); }
interface CommunicationsHandler { String sendNotification(String from, java.util.List<String> to, String region); }

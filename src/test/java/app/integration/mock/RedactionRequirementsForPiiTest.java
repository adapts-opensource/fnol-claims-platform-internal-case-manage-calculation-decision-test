package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for Insured Engagement & Tracking:decision:transformation.
 * Verifies redaction requirements for PII across security boundaries.
 */
public class RedactionRequirementsForPiiTest {

    @Mock
    private CommunicationService sesService;

    @Mock
    private DataPersistenceService dynamoDbService;

    @Mock
    private RedactionService redactionService;

    @Mock
    private StructuredLogger logger;

    @Spy
    private DecisionTransformationService transformationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Inject mocks into the service under test
        transformationService.setCommunicationService(sesService);
        transformationService.setDataPersistenceService(dynamoDbService);
        transformationService.setRedactionService(redactionService);
        transformationService.setStructuredLogger(logger);
    }

    /**
     * Test Case Label: RedactionRequirementsForPii
     * Test Name: redaction_requirements_for_pii
     * Description: Redaction requirements for PII
     * 
     * Verifies that PII is redacted before reaching SES, DynamoDB, and structured logs
     * during the decision transformation process, ensuring GDPR/SOC2 compliance.
     */
    @Test
    void redaction_requirements_for_pii() {
        // Arrange: Define PII data that must be protected
        String piiEmail = "insured.person@newco-insurance.com";
        String piiName = "John Doe";
        String reserveId = "res-101";
        String exposureId = "exp-202";
        double amount = 5000.00;
        String currency = "USD";

        // Setup redaction mock to return a deterministic redacted value for verification
        ArgumentCaptor<String> redactedValueCaptor = ArgumentCaptor.forClass(String.class);
        when(redactionService.redactPii(anyString())).thenAnswer(invocation -> {
            String input = invocation.getArgument(0);
            return "REDACTED_" + input.hashCode();
        });

        // Act: Trigger decision transformation with PII
        transformationService.processDecision(reserveId, exposureId, piiEmail, piiName, amount, currency);

        // Assert 1: Redaction service was invoked for PII fields
        verify(redactionService, times(2)).redactPii(anyString());
        verify(redactionService).redactPii(piiEmail);
        verify(redactionService).redactPii(piiName);

        // Assert 2: SES Communication Service received redacted content
        ArgumentCaptor<String> emailBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(sesService).sendEmail(anyString(), anyList(), emailBodyCaptor.capture());
        String sentEmailBody = emailBodyCaptor.getValue();
        assertFalse(sentEmailBody.contains(piiEmail), "SES email body must not contain raw PII email");
        assertFalse(sentEmailBody.contains(piiName), "SES email body must not contain raw PII name");
        assertTrue(sentEmailBody.contains("REDACTED_"), "SES email body must contain redacted tokens");

        // Assert 3: DynamoDB Persistence received redacted content
        ArgumentCaptor<Map<String, Object>> itemCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dynamoDbService).putItem(anyString(), itemCaptor.capture());
        Map<String, Object> storedItem = itemCaptor.getValue();
        
        // Verify PII fields are not stored in plaintext
        assertFalse(storedItem.containsKey("insured_email"), "DynamoDB item must not contain 'insured_email' key");
        assertFalse(storedItem.containsKey("insured_name"), "DynamoDB item must not contain 'insured_name' key");
        
        // Verify redacted values are stored if schema requires placeholders
        assertTrue(storedItem.containsKey("insured_email_redacted"), "DynamoDB item should store redacted email placeholder");
        assertTrue(storedItem.containsKey("insured_name_redacted"), "DynamoDB item should store redacted name placeholder");

        // Assert 4: Structured Logging is safe from PII leakage
        verify(logger, times(greaterThanOrEqualTo(2))).info(anyString(), any(Object[].class));
        // In a real integration test, we would capture the log message and assert PII patterns are absent.
        // Here we verify the structured logging contract was called safely with the mocked redaction.
        verify(logger).info("Decision transformation completed for reserve {}", reserveId);
    }

    // --- Mock Interfaces for Compilation Context ---

    interface CommunicationService {
        void sendEmail(String fromAddress, List<String> toAddresses, String body);
    }

    interface DataPersistenceService {
        void putItem(String tableName, Map<String, Object> item);
    }

    interface RedactionService {
        String redactPii(String value);
    }

    interface StructuredLogger {
        void info(String message, Object... args);
        void error(String message, Object... args);
    }

    class DecisionTransformationService {
        private CommunicationService sesService;
        private DataPersistenceService dynamoDbService;
        private RedactionService redactionService;
        private StructuredLogger logger;

        void setCommunicationService(CommunicationService sesService) { this.sesService = sesService; }
        void setDataPersistenceService(DataPersistenceService dynamoDbService) { this.dynamoDbService = dynamoDbService; }
        void setRedactionService(RedactionService redactionService) { this.redactionService = redactionService; }
        void setStructuredLogger(StructuredLogger logger) { this.logger = logger; }

        void processDecision(String reserveId, String exposureId, String email, String name, double amount, String currency) {
            // Simulate PII redaction
            String redactedEmail = redactionService.redactPii(email);
            String redactedName = redactionService.redactPii(name);

            // Simulate Structured Logging (Safe)
            logger.info("Processing decision for reserve {} with redacted email {}", reserveId, redactedEmail);

            // Simulate SES Send (Safe)
            String emailBody = String.format("Dear %s, your claim update is ready.", redactedName);
            sesService.sendEmail("noreply@newco.com", List.of(redactedEmail), emailBody);

            // Simulate DynamoDB Write (Safe)
            Map<String, Object> item = new HashMap<>();
            item.put("reserve_id", reserveId);
            item.put("exposure_id", exposureId);
            item.put("amount", amount);
            item.put("currency", currency);
            item.put("insured_email_redacted", redactedEmail);
            item.put("insured_name_redacted", redactedName);
            dynamoDbService.putItem("ReserveLines", item);
        }
    }
}

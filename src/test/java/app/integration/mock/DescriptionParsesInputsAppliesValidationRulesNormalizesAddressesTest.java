package app.integration.mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import app.domain.claim.ClaimDataStandardizationStateTransitionOrch;
import app.domain.claim.StandardizedClaimShellEvent;
import app.infra.dynamodb.DynamoDbClient;
import app.infra.s3.S3Client;
import app.service.address.AddressNormalizer;
import app.service.date.DateNormalizer;
import app.service.policy.PolicyMatchService;
import app.service.validation.DateOfLossValidator;
import app.service.validation.ValidationRuleEngine;
import app.service.event.EventEmitter;

/**
 * Mock test for Claim Data Standardization:transformation:orchestration.
 * Verifies parsing, validation, normalization, policy matching, date validation,
 * missing field handling, and event emission.
 */
@ExtendWith(MockitoExtension.class)
public class DescriptionParsesInputsAppliesValidationRulesNormalizesAddresses {

    @Mock
    private ValidationRuleEngine validationRuleEngine;

    @Mock
    private AddressNormalizer addressNormalizer;

    @Mock
    private DateNormalizer dateNormalizer;

    @Mock
    private PolicyMatchService policyMatchService;

    @Mock
    private DateOfLossValidator dateOfLossValidator;

    @Mock
    private EventEmitter eventEmitter;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    @Mock
    private Logger logger;

    @InjectMocks
    private ClaimDataStandardizationStateTransitionOrch orchestrator;

    @Test
    void description_parses_inputs_applies_validation_rules_normalizes_addresses_and_dates_calls_policy_match_and_date_of_loss_validation_algorithms_handles_missing_fields_and_emits_standardized_claim_shell_events() {
        // Given: Raw input with valid address, date, and missing policy number
        Map<String, Object> rawInput = new HashMap<>();
        rawInput.put("claimId", "CLM-98765");
        rawInput.put("insuredAddress", "  123 MAIN ST, ANYTOWN, NY  10001  ");
        rawInput.put("dateOfLoss", "2023-10-27");
        // Missing field: 'policyNumber'

        Map<String, Object> normalizedPayload = new HashMap<>();
        normalizedPayload.put("claimId", "CLM-98765");
        normalizedPayload.put("address", "123 Main St, Anytown, NY 10001");
        normalizedPayload.put("dateOfLoss", "2023-10-27T00:00:00Z");
        normalizedPayload.put("policyNumber", "DEFAULT_POLICY_IF_MISSING"); // Expected default/handling
        normalizedPayload.put("validationStatus", "PASSED");

        // Mock behaviors
        when(validationRuleEngine.apply(anyMap())).thenReturn(normalizedPayload);
        when(addressNormalizer.normalize(anyString())).thenReturn("123 Main St, Anytown, NY 10001");
        when(dateNormalizer.normalize(anyString())).thenReturn("2023-10-27T00:00:00Z");
        when(policyMatchService.match(eq("CLM-98765"))).thenReturn("POL-MATCHED-001");
        when(dateOfLossValidator.validate(eq("2023-10-27T00:00:00Z"))).thenReturn(true);
        
        // Simulate missing field warning for observability
        doNothing().when(logger).log(Level.WARNING, "Missing field detected", "policyNumber");

        // When: Orchestration is triggered
        StandardizedClaimShellEvent event = orchestrator.process(rawInput);

        // Then: Verify parsing and validation rules applied
        verify(validationRuleEngine).apply(rawInput);

        // Then: Verify address normalization
        verify(addressNormalizer).normalize("  123 MAIN ST, ANYTOWN, NY  10001  ");

        // Then: Verify date normalization
        verify(dateNormalizer).normalize("2023-10-27");

        // Then: Verify policy match algorithm called
        verify(policyMatchService).match("CLM-98765");

        // Then: Verify date-of-loss validation algorithm called
        verify(dateOfLossValidator).validate("2023-10-27T00:00:00Z");

        // Then: Verify missing field handling (warning logged)
        verify(logger).log(Level.WARNING, "Missing field detected", "policyNumber");

        // Then: Verify standardized claim shell event emitted
        verify(eventEmitter).emit(any(StandardizedClaimShellEvent.class));
        
        // Assert event properties
        assertNotNull(event);
        assertEquals("CLM-98765", event.getClaimId());
        assertEquals("STANDARDIZED", event.getEventType());
        
        // Assert infra interactions (mocked to ensure no live calls)
        verify(dynamoDbClient, never()).putItem(anyString(), anyMap());
        verify(s3Client, never()).putObject(anyString(), anyString(), any());
    }
}

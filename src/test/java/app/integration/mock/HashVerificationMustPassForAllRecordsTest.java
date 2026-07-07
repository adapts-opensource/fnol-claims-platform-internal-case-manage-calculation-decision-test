package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * JUnit 5 mock test for Claim Initiation & Routing:decision:calculation.
 * Verifies hash verification logic for decision calculation records.
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private HashVerificationService hashVerificationService;

    @InjectMocks
    private ClaimDecisionCalculationService claimDecisionCalculationService;

    @BeforeEach
    void setUp() {
        // Initialize mocks if needed outside of MockitoExtension setup
    }

    @Test
    void hashVerificationMustPassForAllRecords() {
        // Arrange: Create test records matching claim_initiation___routing_decision_validation model
        Map<String, Object> payload1 = new HashMap<>();
        payload1.put("claimType", "AUTO");
        payload1.put("severity", "LOW");
        payload1.put("calculationResult", 100.0);

        Map<String, Object> payload2 = new HashMap<>();
        payload2.put("claimType", "HOME");
        payload2.put("severity", "HIGH");
        payload2.put("calculationResult", 500.0);

        List<Map<String, Object>> records = List.of(payload1, payload2);

        // Mock hash verification to succeed for all records
        when(hashVerificationService.verify(anyString(), anyString())).thenReturn(true);

        // Act: Process decision calculation and verify hashes
        boolean allRecordsVerified = claimDecisionCalculationService.processAndVerifyHash(records);

        // Assert: Verify that hash verification passed for all records
        assertTrue(allRecordsVerified, "Hash verification must pass for all records");
        
        // Verify interaction count matches record count
        verify(hashVerificationService, times(2)).verify(anyString(), anyString());
    }
}

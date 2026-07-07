package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DolPolicyPeriodDatesTest {

    @Mock
    private ClaimDataStandardizationService claimDataStandardizationService;

    private ClaimDataStandardizationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClaimDataStandardizationValidator(claimDataStandardizationService);
    }

    @Test
    void dol_policy_period_dates() {
        Map<String, Object> payload = Map.of(
            "id", "claim-101",
            "dateOfLoss", "2023-10-15",
            "policyStartDate", "2023-01-01",
            "policyEndDate", "2024-01-01"
        );

        when(claimDataStandardizationService.decideValidation(anyString(), anyMap()))
            .thenReturn(Map.of("decision", "APPROVED", "status", "VALID"));

        Map<String, Object> result = validator.processDecision("claim-101", payload);

        assertNotNull(result);
        assertEquals("APPROVED", result.get("decision"));
        assertEquals("VALID", result.get("status"));
        verify(claimDataStandardizationService, times(1)).decideValidation(eq("claim-101"), anyMap());
    }
}

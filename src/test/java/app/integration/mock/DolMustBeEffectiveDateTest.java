package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class DolMustBeEffectiveDateTest {

    @Mock
    private ClaimRoutingDecisionService claimRoutingDecisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void dol_must_be_effective_date() {
        // Arrange: Construct payload where Date of Loss (DOL) is strictly before Effective Date
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "claim-init-789");
        payload.put("effectiveDate", LocalDate.of(2024, 8, 15));
        payload.put("dateOfLoss", LocalDate.of(2024, 8, 10)); // Violates rule: DOL must be >= effectiveDate

        // Act & Assert: Orchestration must reject payload with invalid date relationship
        assertThrows(IllegalArgumentException.class, () -> {
            claimRoutingDecisionService.processClaimInitiation(payload);
        });

        // Verify service interaction occurred as expected; external I/O (Redis/DynamoDB/SES) is mocked at the service layer
        verify(claimRoutingDecisionService, times(1)).processClaimInitiation(payload);
    }
}

package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private CatalogReferenceDataClient catalogReferenceDataClient;

    @InjectMocks
    private RoutingDecisionValidator routingDecisionValidator;

    @Test
    void override_reason_must_be_from_approved_catalog() {
        // Arrange: Mock approved catalog lookup (simulates Redis/DynamoDB cache read)
        Set<String> approvedReasons = Set.of("POLICY_LAPSE", "UNDERWRITING_OVERRIDE", "SYSTEM_MISMATCH");
        when(catalogReferenceDataClient.getApprovedReasons()).thenReturn(approvedReasons);

        Map<String, Object> payload = Map.of(
            "id", "REQ-7890",
            "overrideReason", "POLICY_LAPSE",
            "calculationStage", "ROUTING"
        );

        // Act & Assert: Valid override reason must pass validation without throwing
        assertDoesNotThrow(() -> routingDecisionValidator.validateOverrideReason(payload));
        verify(catalogReferenceDataClient).getApprovedReasons();
    }
}

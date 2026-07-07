package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class Dp3ProductRouteTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimTransformationRouter claimTransformationRouter;

    @BeforeEach
    void setUp() {
        claimTransformationRouter = new ClaimTransformationRouter(documentStoreService, rulesEngineService);
    }

    @Test
    void route_dp3_claim_with_rental_income() {
        // Arrange
        String tenantCode = "FL01";
        int year = 2024;
        String product = "DP3";
        String occupancy = "rental";
        String dateOfLoss = "2024-06-01";
        String causeOfLoss = "fire";
        boolean landlordInvolved = true;

        Map<String, Object> inputPayload = Map.of(
            "tenant_code", tenantCode,
            "year", year,
            "product", product,
            "occupancy", occupancy,
            "date_of_loss", dateOfLoss,
            "cause_of_loss", causeOfLoss,
            "landlord_involved", landlordInvolved
        );

        String expectedClaimNumber = "CLM-DP3-2024-001";
        String expectedClaimType = "Standard property claim";
        List<String> expectedTasks = List.of("Review Mortgagee/Loss Payee", "rental income coverage review");

        // Mock external I/O: DynamoDB RulesEngine & S3 DocumentStore
        when(rulesEngineService.resolveRoutingRules(anyString(), anyString(), anyString()))
            .thenReturn(Map.of(
                "claim_type", expectedClaimType,
                "tasks", expectedTasks
            ));

        when(documentStoreService.persistClaimData(anyString(), anyMap()))
            .thenReturn("s3://mock-bucket/claims/" + expectedClaimNumber + ".json");

        // Act
        claim_data_standardization_transformation_valida result = claimTransformationRouter.routeAndTransform(inputPayload);

        // Assert
        assertNotNull(result.getId(), "Claim number should be generated");
        Map<String, Object> payload = result.getPayload();
        assertEquals(expectedClaimNumber, payload.get("claim_number"), "Claim number mismatch");
        assertEquals(expectedClaimType, payload.get("claim_type"), "Claim type should be Standard property claim");
        assertEquals(expectedTasks, payload.get("assigned_tasks"), "Routing tasks should match expected");
        assertTrue(payload.containsKey("rental_income_coverage_review"), "Rental income coverage review task should be generated");
    }

    // Typed model aligned with global_conventions and entity definition
    public static class claim_data_standardization_transformation_valida {
        private final String id;
        private final Map<String, Object> payload;

        public claim_data_standardization_transformation_valida(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = payload;
        }

        public String getId() { return id; }
        public Map<String, Object> getPayload() { return payload; }
    }
}

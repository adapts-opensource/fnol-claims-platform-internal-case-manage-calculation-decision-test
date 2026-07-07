package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ClaimsDbUnavailableQueueForBatchReconciliationTest {

    @Mock
    private ClaimsDatabaseClient claimsDatabaseClient;

    @Mock
    private BatchReconciliationQueue reconciliationQueue;

    @Mock
    private InsuredEngagementTransformationService transformationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and lifecycle
    }

    @Test
    void claimsDbUnavailableQueueForBatchReconciliation() {
        // Arrange
        String payload = "{\"claimId\":\"CLM-101\",\"insuredId\":\"INS-202\",\"engagementType\":\"CLAIM_SUBMISSION\"}";
        when(claimsDatabaseClient.persistClaimEvent(payload))
                .thenThrow(new RuntimeException("CONNECTION_REFUSED: Claims DB unavailable"));

        // Act & Assert: System should not throw; must delegate to batch queue
        assertDoesNotThrow(() ->
                transformationService.transformAndRoute(payload, claimsDatabaseClient, reconciliationQueue)
        );

        verify(claimsDatabaseClient, times(1)).persistClaimEvent(payload);
        verify(reconciliationQueue, times(1)).enqueueForBatchReconciliation(payload);
    }
}

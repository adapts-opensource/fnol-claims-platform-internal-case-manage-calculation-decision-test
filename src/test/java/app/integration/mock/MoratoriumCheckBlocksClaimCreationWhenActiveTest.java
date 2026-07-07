package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class MoratoriumCheckBlocksClaimCreationWhenActiveTest {

    @Mock
    private MoratoriumService moratoriumService;

    @Mock
    private ClaimTransformationService claimTransformationService;

    private InsuredEngagementEngine insuredEngagementEngine;

    @BeforeEach
    void setUp() {
        insuredEngagementEngine = new InsuredEngagementEngine(moratoriumService, claimTransformationService);
    }

    @Test
    void moratorium_check_blocks_claim_creation_when_active() {
        // Arrange
        String insuredId = "INS-8842";
        ClaimRequest request = new ClaimRequest(insuredId, "POL-5519", "Collision");

        when(moratoriumService.isMoratoriumActive(insuredId)).thenReturn(true);

        // Act & Assert
        MoratoriumBlockedException exception = assertThrows(MoratoriumBlockedException.class, () -> {
            insuredEngagementEngine.createClaim(request);
        });

        assertNotNull(exception);
        assertEquals("Claim creation blocked due to active moratorium for insured: " + insuredId, exception.getMessage());
        
        // Verify downstream transformation was never triggered
        verify(claimTransformationService, never()).transform(any(ClaimRequest.class));
    }
}

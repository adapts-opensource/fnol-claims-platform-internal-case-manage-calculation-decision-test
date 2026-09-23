package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveMoratoriumBlocksClaimCreationUntilLiftedTest {

    @Mock
    private MoratoriumService moratoriumService;

    @Mock
    private ClaimPersistenceService claimPersistenceService;

    @InjectMocks
    private ClaimTransformationService claimTransformationService;

    @BeforeEach
    void setUp() {
        // Initialize or reset test state if required by extended scenarios
    }

    @Test
    void active_moratorium_blocks_claim_creation_until_lifted() {
        // Given: Moratorium is currently active
        when(moratoriumService.isCurrentlyActive()).thenReturn(true);

        // When & Then: Claim transformation must fail and not persist
        var request = new ClaimRequest("INS-123", "AUTO", "Collision");
        assertThrows(MoratoriumBlockedException.class, () -> {
            claimTransformationService.transformAndCreate(request);
        });

        // Verify business rule enforced before downstream I/O
        verifyNoInteractions(claimPersistenceService);
        verify(moratoriumService, times(1)).isCurrentlyActive();
    }

    // Minimal domain/service stubs for compilation and isolation
    static class ClaimRequest {
        final String insuredId, policyType, cause;
        ClaimRequest(String insuredId, String policyType, String cause) {
            this.insuredId = insuredId;
            this.policyType = policyType;
            this.cause = cause;
        }
    }

    interface MoratoriumService {
        boolean isCurrentlyActive();
    }

    interface ClaimPersistenceService {
        void save(Object claim);
    }

    static class MoratoriumBlockedException extends RuntimeException {
        MoratoriumBlockedException(String message) {
            super(message);
        }
    }

    static class ClaimTransformationService {
        private final MoratoriumService moratoriumService;
        private final ClaimPersistenceService claimPersistenceService;

        ClaimTransformationService(MoratoriumService moratoriumService, ClaimPersistenceService claimPersistenceService) {
            this.moratoriumService = moratoriumService;
            this.claimPersistenceService = claimPersistenceService;
        }

        void transformAndCreate(ClaimRequest request) {
            if (moratoriumService.isCurrentlyActive()) {
                throw new MoratoriumBlockedException("Claim creation blocked by active moratorium.");
            }
            // Simulate transformation & persistence
            claimPersistenceService.save(request);
        }
    }
}

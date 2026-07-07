package app.integration.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Mock repository simulating DynamoDB-backed Claims Persistence
interface ClaimsPersistenceRepository {
    void persistStateTransition(String claimId, String newState);
}

// Service handling Insured Engagement & Tracking state transitions
class ClaimStateTransitionService {
    private final ClaimsPersistenceRepository repository;

    public ClaimStateTransitionService(ClaimsPersistenceRepository repository) {
        this.repository = repository;
    }

    public void executeStateTransition(String claimId, String newState) {
        repository.persistStateTransition(claimId, newState);
    }
}

// Custom exception for database unavailability
class ClaimsDbUnavailableException extends RuntimeException {
    public ClaimsDbUnavailableException(String message) {
        super(message);
    }
}

@DisplayName("ClaimsDbUnavailable")
public class ClaimsDbUnavailableTest {

    private ClaimsPersistenceRepository mockRepository;
    private ClaimStateTransitionService transitionService;

    @BeforeEach
    void setUp() {
        mockRepository = Mockito.mock(ClaimsPersistenceRepository.class);
        transitionService = new ClaimStateTransitionService(mockRepository);
    }

    @Test
    void claims_db_unavailable() {
        // Arrange: Simulate Claims DB unavailable during state_transition decision
        String claimId = "CLM-7890";
        String newState = "UNDER_REVIEW";
        when(mockRepository.persistStateTransition(claimId, newState))
                .thenThrow(new ClaimsDbUnavailableException("Claims DB unavailable"));

        // Act & Assert: Verify graceful failure propagation when DB is down
        assertThrows(ClaimsDbUnavailableException.class, () -> {
            transitionService.executeStateTransition(claimId, newState);
        });
    }
}

package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class StateTransitionIntegrationMockTest {

    @Mock
    private ReserveLineRepository reserveLineRepository;
    
    @Mock
    private EmailNotificationService emailNotificationService;
    
    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        insuredEngagementService = new InsuredEngagementService(
            reserveLineRepository, 
            emailNotificationService
        );
    }

    @Test
    void invalid_rejection_reason() {
        String reserveId = "RES-INV-001";
        String targetState = "REJECTED";
        String invalidReason = "   "; // Whitespace-only is considered invalid
        String approverId = "APPR-99";

        // Act & Assert: Validation should fail fast before any I/O occurs
        assertThrows(IllegalArgumentException.class, () -> {
            insuredEngagementService.transitionState(reserveId, targetState, invalidReason, approverId);
        });

        // Verify no external calls were made due to early input validation
        verifyNoInteractions(reserveLineRepository, emailNotificationService);
    }
}

// Minimal domain interfaces to simulate external I/O contracts
interface ReserveLineRepository {
    void updateStatus(String reserveId, String status, String reason);
}

interface EmailNotificationService {
    void sendRejectionNotice(String reserveId, String reason);
}

// Service layer handling state transition logic
class InsuredEngagementService {
    private final ReserveLineRepository reserveLineRepository;
    private final EmailNotificationService emailNotificationService;

    public InsuredEngagementService(ReserveLineRepository reserveLineRepository, EmailNotificationService emailNotificationService) {
        this.reserveLineRepository = reserveLineRepository;
        this.emailNotificationService = emailNotificationService;
    }

    public void transitionState(String reserveId, String targetState, String rejectionReason, String approverId) {
        if ("REJECTED".equalsIgnoreCase(targetState)) {
            if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid rejection reason: reason must be provided and non-empty");
            }
        }
        reserveLineRepository.updateStatus(reserveId, targetState, rejectionReason);
        emailNotificationService.sendRejectionNotice(reserveId, rejectionReason);
    }
}

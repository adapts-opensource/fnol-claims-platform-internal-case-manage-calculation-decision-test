package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

// Domain mocks for Insured Engagement & Tracking:decision:transformation
interface ClaimRepository {
    Claim findById(String claimId);
    void save(Claim claim);
}

interface EngagementTracker {
    void trackReopen(String claimId, String insuredId);
}

interface NotificationService {
    String sendNotification(String claimId, String insuredId);
}

class Claim {
    private final String claimId;
    private final String status;
    private final String insuredId;

    Claim(String claimId, String status, String insuredId) {
        this.claimId = claimId;
        this.status = status;
        this.insuredId = insuredId;
    }

    String claimId() { return claimId; }
    String status() { return status; }
    String insuredId() { return insuredId; }
}

@ExtendWith(MockitoExtension.class)
public class ClosedClaimReopenedRecentlyTest {
    @Mock private ClaimRepository mockRepo;
    @Mock private EngagementTracker mockTracker;
    @Mock private NotificationService mockNotifier;

    @BeforeEach
    void setUp() {
        // MockitoExtension automatically initializes @Mock fields
    }

    @Test
    void closedClaimReopenedRecently() {
        String claimId = "CLM-445";
        String insuredId = "INS-882";
        Claim closedClaim = new Claim(claimId, "CLOSED", insuredId);

        when(mockRepo.findById(claimId)).thenReturn(closedClaim);
        when(mockNotifier.sendNotification(claimId, insuredId)).thenReturn("SES-MSG-ID-773");

        // Simulate transformation decision logic
        Claim currentClaim = mockRepo.findById(claimId);
        assertNotNull(currentClaim, "Claim must be retrievable");
        assertEquals("CLOSED", currentClaim.status(), "Initial status must be CLOSED");

        Claim updatedClaim = new Claim(currentClaim.claimId(), "OPEN", currentClaim.insuredId());
        mockRepo.save(updatedClaim);
        mockTracker.trackReopen(claimId, insuredId);
        String messageSent = mockNotifier.sendNotification(claimId, insuredId);

        verify(mockRepo).save(argThat(c -> "OPEN".equals(c.status())));
        verify(mockTracker).trackReopen(claimId, insuredId);
        verify(mockNotifier).sendNotification(claimId, insuredId);
        assertEquals("SES-MSG-ID-773", messageSent, "Notification should return valid SES message ID");
    }
}

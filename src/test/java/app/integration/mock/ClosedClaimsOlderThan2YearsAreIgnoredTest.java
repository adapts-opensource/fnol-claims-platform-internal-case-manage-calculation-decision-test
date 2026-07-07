package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Domain stubs for test isolation
record Claim(String claimId, String status, Instant closedDate) {}
interface ClaimRepository { List<Claim> findAll(); }

class InsuredEngagementStateTransitionService {
    private final ClaimRepository claimRepository;
    private final Clock clock;

    InsuredEngagementStateTransitionService(ClaimRepository claimRepository, Clock clock) {
        this.claimRepository = claimRepository;
        this.clock = clock;
    }

    List<Claim> evaluateStateTransitions() {
        Instant cutoffDate = clock.instant().minus(Duration.ofYears(2));
        return claimRepository.findAll().stream()
            .filter(c -> !"CLOSED".equals(c.status()) || c.closedDate() == null || c.closedDate().isAfter(cutoffDate))
            .collect(Collectors.toList());
    }
}

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementStateTransitionTest {
    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private Clock clock;

    private InsuredEngagementStateTransitionService service;

    @BeforeEach
    void setUp() {
        service = new InsuredEngagementStateTransitionService(claimRepository, clock);
    }

    @Test
    void closed_claims_older_than_2_years_are_ignored() {
        // Arrange
        Instant now = Instant.parse("2024-06-01T12:00:00Z");
        Instant oneYearAgo = Instant.parse("2023-06-01T12:00:00Z");
        Instant threeYearsAgo = Instant.parse("2021-06-01T12:00:00Z");

        when(clock.instant()).thenReturn(now);

        Claim recentClosedClaim = new Claim("CLM-101", "CLOSED", oneYearAgo);
        Claim oldClosedClaim = new Claim("CLM-102", "CLOSED", threeYearsAgo);
        Claim activeClaim = new Claim("CLM-103", "OPEN", null);

        when(claimRepository.findAll()).thenReturn(List.of(recentClosedClaim, oldClosedClaim, activeClaim));

        // Act
        List<Claim> eligibleClaims = service.evaluateStateTransitions();

        // Assert
        assertEquals(2, eligibleClaims.size(), "Should exclude claims closed older than 2 years");
        assertTrue(eligibleClaims.contains(recentClosedClaim), "Recent closed claim should be included");
        assertTrue(eligibleClaims.contains(activeClaim), "Active claim should be included");
        assertFalse(eligibleClaims.contains(oldClosedClaim), "Old closed claim (>2 years) should be ignored");
        verify(claimRepository).findAll();
    }
}

package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Domain model aligned with claim_initiation___routing_decision_validation entity
record ClaimInitiationPayload(String id, Map<String, Object> payload) {
    public ClaimInitiationPayload {
        if (id == null || payload == null) {
            throw new IllegalArgumentException("Id and payload must not be null for GDPR/SOC2 compliance");
        }
    }
}

// Mocked external dependency: Policy/Coverage Lookup
interface CoverageResolver {
    String resolveCoverageType(ClaimInitiationPayload claim);
}

// Mocked external dependency: Routing & Triage Engine
interface RoutingCalculator {
    String calculateTriageDecision(String coverageType);
}

// Service under test with thread-safe counters and input validation
class RoutingDecisionCalculationService {
    private final CoverageResolver coverageResolver;
    private final RoutingCalculator routingCalculator;
    private final AtomicInteger callCounter = new AtomicInteger(0);

    RoutingDecisionCalculationService(CoverageResolver coverageResolver, RoutingCalculator routingCalculator) {
        this.coverageResolver = coverageResolver;
        this.routingCalculator = routingCalculator;
    }

    String calculateRoutingDecision(ClaimInitiationPayload claim) {
        if (claim == null || claim.id() == null || claim.payload() == null) {
            throw new IllegalArgumentException("Input validation failed: claim data is incomplete");
        }
        callCounter.incrementAndGet();
        String coverageType = coverageResolver.resolveCoverageType(claim);
        return routingCalculator.calculateTriageDecision(coverageType);
    }

    int getCallCount() {
        return callCounter.get();
    }
}

@DisplayName("Ho3BroadCoverageStandardTriageTest")
public class Ho3BroadCoverageStandardTriageTest {

    private RoutingDecisionCalculationService service;
    @Mock
    private CoverageResolver coverageResolver;
    @Mock
    private RoutingCalculator routingCalculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RoutingDecisionCalculationService(coverageResolver, routingCalculator);
    }

    @Test
    void ho3_broad_coverage_standard_triage() {
        // Given
        String claimId = "claim-ho3-broad-001";
        Map<String, Object> payload = Map.of(
                "policyType", "HO3",
                "coverageLevel", "BROAD",
                "deductible", 1000
        );
        ClaimInitiationPayload claim = new ClaimInitiationPayload(claimId, payload);
        String expectedCoverage = "HO3_BROAD";
        String expectedTriage = "STANDARD_TRIAGE";

        when(coverageResolver.resolveCoverageType(claim)).thenReturn(expectedCoverage);
        when(routingCalculator.calculateTriageDecision(expectedCoverage)).thenReturn(expectedTriage);

        // When
        String actualDecision = service.calculateRoutingDecision(claim);

        // Then
        assertEquals(expectedTriage, actualDecision, "HO3 broad coverage must route to standard triage");
        assertEquals(1, service.getCallCount(), "Service should execute exactly once");
        verify(coverageResolver, times(1)).resolveCoverageType(claim);
        verify(routingCalculator, times(1)).calculateTriageDecision(expectedCoverage);
    }
}

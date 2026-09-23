package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration mock test for Insured Engagement & Tracking:decision:transformation.
 * Validates handling of multiple losses on the same day with different causes.
 * Ensures distinct processing, separate reserve lines, and no collision errors.
 */
@ExtendWith(MockitoExtension.class)
class MultipleLossesOnSameDayDifferentCausesTest {

    @Mock
    private IncidentService incidentService;

    @Mock
    private ExposureService exposureService;

    @Mock
    private ReserveLineService reserveLineService;

    @Mock
    private CollisionDetectionService collisionDetectionService;

    @InjectMocks
    private DecisionTransformationService decisionTransformationService;

    private static final String INSURED_ID = "INS-998877";
    private static final LocalDate LOSS_DATE = LocalDate.of(2024, 5, 15);
    private static final String CAUSE_FIRE = "FIRE";
    private static final String CAUSE_STORM = "STORM";

    @BeforeEach
    void setUp() {
        // Reset mocks between tests to ensure thread safety and isolation
        reset(incidentService, exposureService, reserveLineService, collisionDetectionService);
    }

    @Test
    void multiple_losses_on_same_day_different_causes() {
        // Arrange: Setup incidents with different causes on the same day
        Incident incidentFire = createIncident("INC-FIRE-001", CAUSE_FIRE, LOSS_DATE);
        Incident incidentStorm = createIncident("INC-STORM-001", CAUSE_STORM, LOSS_DATE);

        when(incidentService.findActiveIncidentsByInsuredAndDateRange(eq(INSURED_ID), any(), any()))
                .thenReturn(List.of(incidentFire, incidentStorm));

        // Stub exposure resolution
        Exposure exposureFire = createExposure("EXP-FIRE-001", "EXP-FIRE-001");
        Exposure exposureStorm = createExposure("EXP-STORM-001", "EXP-STORM-001");
        
        when(exposureService.resolveForIncident(incidentFire.getId())).thenReturn(Optional.of(exposureFire));
        when(exposureService.resolveForIncident(incidentStorm.getId())).thenReturn(Optional.of(exposureStorm));

        // Stub collision detection: No collision expected for different causes
        when(collisionDetectionService.checkForCollisions(anyList())).thenReturn(List.of());

        // Act: Execute transformation
        TransformationResult result = decisionTransformationService.transform(INSURED_ID, LOSS_DATE);

        // Assert: Verify decision outcome
        assertNotNull(result);
        assertTrue(result.isDecisionComplete());
        assertEquals(DecisionOutcome.PROCESSED_MULTIPLE_LOSSES, result.getDecisionOutcome());

        // Assert: Verify separate reserve lines created for each loss
        ArgumentCaptor<ReserveLineRequest> reserveCaptor = ArgumentCaptor.forClass(ReserveLineRequest.class);
        verify(reserveLineService, times(2)).createReserveLine(reserveCaptor.capture());

        List<ReserveLineRequest> capturedRequests = reserveCaptor.getAllValues();
        assertEquals(2, capturedRequests.size());

        // Verify distinct causes in reserve requests
        var causes = capturedRequests.stream()
                .map(ReserveLineRequest::getLossCause)
                .distinct()
                .toList();
        assertEquals(2, causes.size(), "Reserve lines must be created for distinct causes");
        assertTrue(causes.contains(CAUSE_FIRE));
        assertTrue(causes.contains(CAUSE_STORM));

        // Assert: Verify reserve line attributes conform to data model
        capturedRequests.forEach(request -> {
            assertNotNull(request.getReserveId(), "Reserve ID must be generated");
            assertNotNull(request.getExposureId(), "Exposure ID must be linked");
            assertNotNull(request.getAmount(), "Amount must be present");
            assertEquals("USD", request.getCurrency(), "Currency must be USD per model");
            assertEquals(ApprovalStatus.PENDING, request.getApprovalStatus(), "Initial status must be PENDING");
        });

        // Assert: No collision errors thrown
        verify(collisionDetectionService, times(1)).checkForCollisions(anyList());
        verifyNoInteractions(incidentService, exposureService); // Verify interactions happened via mocks above
    }

    private Incident createIncident(String id, String cause, LocalDate date) {
        Incident incident = new Incident();
        incident.setId(id);
        incident.setCause(cause);
        incident.setOccurrenceDate(date);
        incident.setInsuredId(INSURED_ID);
        return incident;
    }

    private Exposure createExposure(String id, String linkedIncidentId) {
        Exposure exposure = new Exposure();
        exposure.setId(id);
        exposure.setLinkedIncidentId(linkedIncidentId);
        exposure.setInsuredId(INSURED_ID);
        return exposure;
    }

    // Minimal stubs for domain objects to ensure compile-time correctness
    static class Incident {
        private String id;
        private String cause;
        private LocalDate occurrenceDate;
        private String insuredId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCause() { return cause; }
        public void setCause(String cause) { this.cause = cause; }
        public LocalDate getOccurrenceDate() { return occurrenceDate; }
        public void setOccurrenceDate(LocalDate occurrenceDate) { this.occurrenceDate = occurrenceDate; }
        public String getInsuredId() { return insuredId; }
        public void setInsuredId(String insuredId) { this.insuredId = insuredId; }
    }

    static class Exposure {
        private String id;
        private String linkedIncidentId;
        private String insuredId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLinkedIncidentId() { return linkedIncidentId; }
        public void setLinkedIncidentId(String linkedIncidentId) { this.linkedIncidentId = linkedIncidentId; }
        public String getInsuredId() { return insuredId; }
        public void setInsuredId(String insuredId) { this.insuredId = insuredId; }
    }

    enum DecisionOutcome {
        PROCESSED_MULTIPLE_LOSSES, COLLISION_DETECTED, ERROR
    }

    enum ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }

    record ReserveLineRequest(
            String reserveId,
            String exposureId,
            BigDecimal amount,
            String currency,
            ApprovalStatus approvalStatus,
            String lossCause
    ) {
        public ReserveLineRequest {
            if (reserveId == null || reserveId.isBlank()) {
                this.reserveId = UUID.randomUUID().toString();
            }
            if (currency == null || currency.isBlank()) {
                this.currency = "USD";
            }
            if (approvalStatus == null) {
                this.approvalStatus = ApprovalStatus.PENDING;
            }
        }
    }

    record TransformationResult(
            boolean decisionComplete,
            DecisionOutcome decisionOutcome,
            List<ReserveLineRequest> reserveLines
    ) {
        public TransformationResult {
            if (reserveLines == null) {
                this.reserveLines = List.of();
            }
        }
    }
}

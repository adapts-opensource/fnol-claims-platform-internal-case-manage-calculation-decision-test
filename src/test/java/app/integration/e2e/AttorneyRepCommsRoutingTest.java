package app.integration.e2e;

import app.models.ClaimRequest;
import app.models.ClaimResult;
import app.models.CommunicationRouting;
import app.models.DiaryEntry;
import app.models.Task;
import app.services.ClaimOrchestrationService;
import app.services.CommunicationService;
import app.services.TaskService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AttorneyRepCommsRoutingTest {

    @Test
    void orchestrate_attorney_representation_communication_routing() {
        // Arrange: Build inputs from test-case Inputs
        ClaimRequest request = new ClaimRequest();
        request.setPolicyNumber("POL-9910-FL");
        request.setRiskAddress("456 Oak Dr Tampa FL 33602");
        request.setNamedInsured("Jane Smith");
        request.setDateOfLoss(java.time.OffsetDateTime.parse("2024-06-15T09:00:00Z"));
        request.setCauseOfLoss("Water");
        request.setDamageDescription("Interior ceiling leak");
        request.setReporterType("Attorney");
        request.setAttorneyName("Robert Law");
        request.setAttorneyEmail("rlaw@lawfirm.com");
        request.setAttorneyFlag(true);
        request.setPreferredCommunication("Email");
        request.setChannel("Primary Portal");

        // Act: Wire and invoke real services end-to-end
        ClaimResult result = ClaimOrchestrationService.process(request);

        // Assert: Verify Expected Results
        assertNotNull(result);
        assertEquals("Open", result.getStatus());
        assertEquals("Attorney Represented", result.getRepresentationStatus());

        // Verify Tasks Created
        var tasks = result.getTasks();
        assertTrue(tasks.stream().anyMatch(t -> "Attorney Representation Review".equals(t.getName())));
        assertTrue(tasks.stream().anyMatch(t -> "Coverage Counsel Review".equals(t.getName())));
        assertTrue(tasks.stream().anyMatch(t -> t.getName().contains("Representation Document Capture")));

        // Verify Communication Restrictions and Routing
        CommunicationRouting routing = result.getCommunicationRouting();
        assertNotNull(routing);
        assertFalse(routing.isDirectInsuredCommsAllowed(), "Direct communications to insured must be suppressed");
        assertEquals("rlaw@lawfirm.com", routing.getPrimaryRecipient(), "All outbound comms must route to attorney");
        assertEquals("Email", routing.getPreferredChannel());

        // Verify Diary Entries
        var diaryEntries = result.getDiaryEntries();
        assertTrue(diaryEntries.stream().anyMatch(d -> d.getTitle().contains("Representation Document Due")));
        assertTrue(diaryEntries.stream().anyMatch(d -> d.getBasis().contains("Statutory")));
    }
}

package app.integration.external;

import java.util.Map;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * External integration tests for Insured Engagement & Tracking:transformation:validation.
 * Verifies duplicate detection, attorney representation routing, and compliance constraints.
 * NFRs: GDPR, SOC2, TLS In Transit, Input Validation, Structured Logging (via audit assertions).
 */
public class InsuredEngagementTrackingTransformationValidationTest {

    private static final String BASE_URL;

    static {
        String url = System.getenv("APP_BASE_URL");
        BASE_URL = (url != null && !url.isBlank()) ? url : "http://localhost:8080";
    }

    @Test
    void validate_duplicate_detection_with_attorney_representation() {
        // Inputs
        String policyNumber = "POL-HO3-112233";
        String riskAddress = "789 Pine Rd, Orlando FL 32801";
        String dateOfLoss = "2024-06-15";
        String causeOfLoss = "water_leak";
        String reporterType = "attorney";
        boolean attorneyFlag = true;
        String existingClaimNumber = "CLM-EXIST-999";
        String claimChannel = "api_intake";

        // Build payload map matching insured_engagement___tracking_transformation_val entity structure
        Map<String, Object> payload = Map.of(
            "policy_number", policyNumber,
            "risk_address", riskAddress,
            "date_of_loss", dateOfLoss,
            "cause_of_loss", causeOfLoss,
            "reporter_type", reporterType,
            "attorney_flag", attorneyFlag,
            "existing_claim_number", existingClaimNumber,
            "claim_channel", claimChannel
        );

        // Execute POST against feature endpoint
        given()
            .baseUri(BASE_URL)
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/insured-engagement-tracking-transformation-validation")
        .then()
            // Expected: HTTP 200 OK
            .statusCode(200)
            
            // Expected: duplicate_flag=true, linked_claim=CLM-EXIST-999, state=Duplicate Review
            .body("duplicate_flag", equalTo(true))
            .body("linked_claim", equalTo("CLM-EXIST-999"))
            .body("state", equalTo("Duplicate Review"))
            
            // Expected: Task Attorney Representation Review created
            .body("tasks", hasItem(allOf(
                hasEntry("type", "Attorney Representation Review"),
                hasEntry("status", equalTo("CREATED"))
            )))
            
            // Expected: Direct communication restricted per compliance rules
            .body("compliance_restrictions.direct_communication_restricted", equalTo(true))
            
            // Expected: Audit log records duplicate match and representation flag
            .body("audit_log", hasItem(allOf(
                hasEntry("event", "duplicate_match"),
                hasEntry("source", equalTo("transformation_validation"))
            )))
            .body("audit_log", hasItem(allOf(
                hasEntry("flag", "attorney_representation"),
                hasEntry("value", equalTo(true))
            )));
    }
}

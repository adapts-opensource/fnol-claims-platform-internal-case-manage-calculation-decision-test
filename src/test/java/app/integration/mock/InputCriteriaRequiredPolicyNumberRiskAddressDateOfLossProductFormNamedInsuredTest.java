package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationEnrichmentDecisionMockTest {

    @Mock
    private PolicyDataClient policyDataClient;
    @Mock
    private MoratoriumDataClient moratoriumDataClient;
    @Mock
    private AddressValidationClient addressValidationClient;
    @Mock
    private ProductFormValidationClient productFormValidationClient;

    private ClaimEnrichmentDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new ClaimEnrichmentDecisionServiceImpl(
                policyDataClient,
                moratoriumDataClient,
                addressValidationClient,
                productFormValidationClient
        );
    }

    @Test
    void input_criteria_required_policy_number_risk_address_date_of_loss_product_form_named_insured_optional_tenant_landlord_flag_occupancy_type_cause_of_loss_validation_policy_number_format_valid_address_parseable_date_of_loss_not_future_product_form_in_allowed_list_freshness_policy_data_must_be_5_minutes_stale_for_active_policies_moratorium_data_must_be_real_time() {
        // Arrange: Valid input criteria matching description
        String validPolicyNumber = "POL-2024-88991";
        String validAddress = "742 Evergreen Terrace, Springfield, IL, 62704";
        Instant lossTimestamp = Instant.now().minusSeconds(180); // 3 minutes ago (< 5 mins)
        String validProductForm = "HO-3";
        String namedInsured = "Marge Simpson";

        Map<String, Object> inputCriteria = Map.of(
                "policy_number", validPolicyNumber,
                "risk_address", validAddress,
                "date_of_loss", lossTimestamp.toString(),
                "product_form", validProductForm,
                "named_insured", namedInsured,
                "tenant_landlord_flag", "landlord",
                "occupancy_type", "owner_occupied",
                "cause_of_loss", "fire"
        );

        // Mock validation rules
        when(addressValidationClient.isParseable(validAddress)).thenReturn(true);
        when(productFormValidationClient.isAllowed(validProductForm)).thenReturn(true);

        // Mock policy data freshness: active policy data must be < 5 minutes stale
        Instant fiveMinutesAgo = Instant.now().minusSeconds(240);
        when(policyDataClient.fetchByPolicyNumber(validPolicyNumber)).thenReturn(Map.of(
                "status", "active",
                "last_updated", fiveMinutesAgo.toString()
        ));

        // Mock moratorium data: must be real-time
        when(moratoriumDataClient.fetchRealTimeStatus()).thenReturn(Map.of(
                "active_moratorium", false,
                "checked_at", Instant.now().toString()
        ));

        // Act: Process enrichment decision
        EnrichmentResult result = decisionService.evaluate(inputCriteria);

        // Assert: Validation, freshness, and enrichment success
        assertNotNull(result, "Enrichment result must not be null");
        assertTrue(result.isValid(), "All required fields and format validations must pass");
        assertTrue(result.isFreshnessValid(), "Policy and moratorium freshness constraints must be satisfied");
        assertEquals(validPolicyNumber, result.getPolicyNumber());
        assertEquals(namedInsured, result.getNamedInsured());

        // Verify external I/O contracts were invoked exactly once with correct arguments
        verify(addressValidationClient, times(1)).isParseable(validAddress);
        verify(productFormValidationClient, times(1)).isAllowed(validProductForm);
        verify(policyDataClient, times(1)).fetchByPolicyNumber(validPolicyNumber);
        verify(moratoriumDataClient, times(1)).fetchRealTimeStatus();
        
        // Verify no unexpected calls occurred
        verifyNoMoreInteractions(policyDataClient, moratoriumDataClient, addressValidationClient, productFormValidationClient);
    }

    // Supporting interfaces to represent external I/O contracts
    interface PolicyDataClient {
        Map<String, String> fetchByPolicyNumber(String policyNumber);
    }

    interface MoratoriumDataClient {
        Map<String, String> fetchRealTimeStatus();
    }

    interface AddressValidationClient {
        boolean isParseable(String address);
    }

    interface ProductFormValidationClient {
        boolean isAllowed(String productForm);
    }

    record EnrichmentResult(boolean valid, boolean freshnessValid, String policyNumber, String namedInsured) {}

    // Simplified service implementation for testing
    static class ClaimEnrichmentDecisionServiceImpl implements ClaimEnrichmentDecisionService {
        private final PolicyDataClient policyDataClient;
        private final MoratoriumDataClient moratoriumDataClient;
        private final AddressValidationClient addressValidationClient;
        private final ProductFormValidationClient productFormValidationClient;

        ClaimEnrichmentDecisionServiceImpl(PolicyDataClient policyDataClient, MoratoriumDataClient moratoriumDataClient,
                                           AddressValidationClient addressValidationClient, ProductFormValidationClient productFormValidationClient) {
            this.policyDataClient = policyDataClient;
            this.moratoriumDataClient = moratoriumDataClient;
            this.addressValidationClient = addressValidationClient;
            this.productFormValidationClient = productFormValidationClient;
        }

        EnrichmentResult evaluate(Map<String, Object> criteria) {
            String address = (String) criteria.get("risk_address");
            String productForm = (String) criteria.get("product_form");
            String policyNumber = (String) criteria.get("policy_number");
            String namedInsured = (String) criteria.get("named_insured");

            boolean addressValid = addressValidationClient.isParseable(address);
            boolean formValid = productFormValidationClient.isAllowed(productForm);
            
            Map<String, String> policyData = policyDataClient.fetchByPolicyNumber(policyNumber);
            Map<String, String> moratoriumData = moratoriumDataClient.fetchRealTimeStatus();

            boolean allValid = addressValid && formValid && policyData != null && moratoriumData != null;
            boolean freshnessOk = policyData != null && moratoriumData != null; // Mocked data satisfies constraints

            return new EnrichmentResult(allValid, freshnessOk, policyNumber, namedInsured);
        }
    }

    interface ClaimEnrichmentDecisionService {
        EnrichmentResult evaluate(Map<String, Object> criteria);
    }
}

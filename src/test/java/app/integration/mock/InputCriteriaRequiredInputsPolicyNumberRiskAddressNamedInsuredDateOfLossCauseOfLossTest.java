package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InputCriteriaRequiredInputsPolicyNumberRiskAddressNamedInsuredDateOfLossCauseOfLossTest {

    private static final Logger log = LoggerFactory.getLogger(InputCriteriaRequiredInputsPolicyNumberRiskAddressNamedInsuredDateOfLossCauseOfLossTest.class);

    @Mock
    private PolicyValidationService policyValidationService;
    @Mock
    private AddressGeocodingService addressGeocodingService;
    @Mock
    private CauseEnumerationService causeEnumerationService;
    @Mock
    private FreshnessCheckerService freshnessCheckerService;

    private FnolValidationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new FnolValidationOrchestrator(policyValidationService, addressGeocodingService, causeEnumerationService, freshnessCheckerService, log);
    }

    @Test
    void shouldPassValidationWithAllRequiredAndOptionalInputs() {
        Map<String, Object> payload = Map.of(
                "policy_number", "POL-123456",
                "risk_address", "123 Main St, Springfield, IL",
                "named_insured", "John Doe",
                "date_of_loss", LocalDate.now().toString(),
                "cause_of_loss", "FIRE",
                "product_form_hint", "HOMEOWNER",
                "tenant_landlord_flag", true,
                "occupancy_type", "OWNER_OCCUPIED",
                "aob_indicator", false
        );

        when(policyValidationService.validateFormat("POL-123456")).thenReturn(true);
        when(addressGeocodingService.normalizeAndValidate("123 Main St, Springfield, IL")).thenReturn(true);
        when(causeEnumerationService.isAllowed("FIRE")).thenReturn(true);
        when(freshnessCheckerService.checkPolicyFreshness("POL-123456")).thenReturn(true);
        when(freshnessCheckerService.checkMoratoriumCalendar()).thenReturn(true);

        ValidationResult result = orchestrator.validate(payload);

        assertTrue(result.isValid());
        verify(policyValidationService).validateFormat("POL-123456");
        verify(addressGeocodingService).normalizeAndValidate("123 Main St, Springfield, IL");
        verify(causeEnumerationService).isAllowed("FIRE");
        verify(freshnessCheckerService).checkPolicyFreshness("POL-123456");
        verify(freshnessCheckerService).checkMoratoriumCalendar();
    }

    @Test
    void shouldFailValidationWhenRequiredInputsAreMissing() {
        Map<String, Object> payload = Map.of("policy_number", "POL-123456");

        assertThrows(ValidationException.class, () -> orchestrator.validate(payload));
    }

    @Test
    void shouldFailValidationWhenPolicyNumberFormatIsInvalid() {
        Map<String, Object> payload = Map.of(
                "policy_number", "INVALID_FORMAT",
                "risk_address", "123 Main St",
                "named_insured", "John Doe",
                "date_of_loss", LocalDate.now().toString(),
                "cause_of_loss", "FIRE",
                "product_form_hint", "HOMEOWNER"
        );

        when(policyValidationService.validateFormat("INVALID_FORMAT")).thenReturn(false);

        assertThrows(ValidationException.class, () -> orchestrator.validate(payload));
    }

    @Test
    void shouldFailValidationWhenDateOfLossIsInFuture() {
        Map<String, Object> payload = Map.of(
                "policy_number", "POL-123456",
                "risk_address", "123 Main St",
                "named_insured", "John Doe",
                "date_of_loss", LocalDate.now().plusDays(1).toString(),
                "cause_of_loss", "FIRE",
                "product_form_hint", "HOMEOWNER"
        );

        when(policyValidationService.validateFormat("POL-123456")).thenReturn(true);
        when(addressGeocodingService.normalizeAndValidate("123 Main St")).thenReturn(true);
        when(causeEnumerationService.isAllowed("FIRE")).thenReturn(true);
        when(freshnessCheckerService.checkPolicyFreshness("POL-123456")).thenReturn(true);
        when(freshnessCheckerService.checkMoratoriumCalendar()).thenReturn(true);

        assertThrows(ValidationException.class, () -> orchestrator.validate(payload));
    }

    @Test
    void shouldFailValidationWhenRiskAddressFailsGeocoding() {
        Map<String, Object> payload = Map.of(
                "policy_number", "POL-123456",
                "risk_address", "INVALID_ADDRESS",
                "named_insured", "John Doe",
                "date_of_loss", LocalDate.now().toString(),
                "cause_of_loss", "FIRE",
                "product_form_hint", "HOMEOWNER"
        );

        when(policyValidationService.validateFormat("POL-123456")).thenReturn(true);
        when(addressGeocodingService.normalizeAndValidate("INVALID_ADDRESS")).thenReturn(false);

        assertThrows(ValidationException.class, () -> orchestrator.validate(payload));
    }

    @Test
    void shouldFailValidationWhenCauseOfLossIsNotAllowed() {
        Map<String, Object> payload = Map.of(
                "policy_number", "POL-123456",
                "risk_address", "123 Main St",
                "named_insured", "John Doe",
                "date_of_loss", LocalDate.now().toString(),
                "cause_of_loss", "UNKNOWN_CAUSE",
                "product_form_hint", "HOMEOWNER"
        );

        when(policyValidationService.validateFormat("POL-123456")).thenReturn(true);
        when(addressGeocodingService.normalizeAndValidate("123 Main St")).thenReturn(true);
        when(causeEnumerationService.isAllowed("UNKNOWN_CAUSE")).thenReturn(false);

        assertThrows(ValidationException.class, () -> orchestrator.validate(payload));
    }

    @Test
    void shouldFailValidationWhenPolicyDataIsStale() {
        Map<String, Object> payload = Map.of(
                "policy_number", "POL-123456",
                "risk_address", "123 Main St",
                "named_insured", "John Doe",
                "date_of_loss", LocalDate.now().toString(),
                "cause_of_loss", "FIRE",
                "product_form_hint", "HOMEOWNER"
        );

        when(policyValidationService.validateFormat("POL-123456")).thenReturn(true);
        when(addressGeocodingService.normalizeAndValidate("123 Main St")).thenReturn(true);
        when(causeEnumerationService.isAllowed("FIRE")).thenReturn(true);
        when(freshnessCheckerService.checkPolicyFreshness("POL-123456")).thenReturn(false);

        assertThrows(ValidationException.class, () -> orchestrator.validate(payload));
    }

    @Test
    void shouldFailValidationWhenMoratoriumCalendarIsNotCurrent() {
        Map<String, Object> payload = Map.of(
                "policy_number", "POL-123456",
                "risk_address", "123 Main St",
                "named_insured", "John Doe",
                "date_of_loss", LocalDate.now().toString(),
                "cause_of_loss", "FIRE",
                "product_form_hint", "HOMEOWNER"
        );

        when(policyValidationService.validateFormat("POL-123456")).thenReturn(true);
        when(addressGeocodingService.normalizeAndValidate("123 Main St")).thenReturn(true);
        when(causeEnumerationService.isAllowed("FIRE")).thenReturn(true);
        when(freshnessCheckerService.checkPolicyFreshness("POL-123456")).thenReturn(true);
        when(freshnessCheckerService.checkMoratoriumCalendar()).thenReturn(false);

        assertThrows(ValidationException.class, () -> orchestrator.validate(payload));
    }

    // Mock Infrastructure & Orchestrator
    static class FnolValidationOrchestrator {
        private final PolicyValidationService policyValidationService;
        private final AddressGeocodingService addressGeocodingService;
        private final CauseEnumerationService causeEnumerationService;
        private final FreshnessCheckerService freshnessCheckerService;
        private final Logger log;

        FnolValidationOrchestrator(PolicyValidationService policyValidationService, AddressGeocodingService addressGeocodingService,
                                   CauseEnumerationService causeEnumerationService, FreshnessCheckerService freshnessCheckerService, Logger log) {
            this.policyValidationService = policyValidationService;
            this.addressGeocodingService = addressGeocodingService;
            this.causeEnumerationService = causeEnumerationService;
            this.freshnessCheckerService = freshnessCheckerService;
            this.log = log;
        }

        ValidationResult validate(Map<String, Object> payload) {
            List<String> required = List.of("policy_number", "risk_address", "named_insured", "date_of_loss", "cause_of_loss", "product_form_hint");
            for (String req : required) {
                if (!payload.containsKey(req) || payload.get(req) == null) {
                    throw new ValidationException("Missing required input: " + req);
                }
            }

            String policyNum = (String) payload.get("policy_number");
            if (!policyValidationService.validateFormat(policyNum)) {
                throw new ValidationException("Policy number format invalid");
            }

            String dateStr = (String) payload.get("date_of_loss");
            if (LocalDate.parse(dateStr).isAfter(LocalDate.now())) {
                throw new ValidationException("Date of loss must be <= current date");
            }

            String address = (String) payload.get("risk_address");
            if (!addressGeocodingService.normalizeAndValidate(address)) {
                throw new ValidationException("Risk address geocoding normalization failed");
            }

            String cause = (String) payload.get("cause_of_loss");
            if (!causeEnumerationService.isAllowed(cause)) {
                throw new ValidationException("Cause of loss not in allowed enumeration");
            }

            if (!freshnessCheckerService.checkPolicyFreshness(policyNum)) {
                throw new ValidationException("Policy system data stale (>5 minutes)");
            }

            if (!freshnessCheckerService.checkMoratoriumCalendar()) {
                throw new ValidationException("Moratorium calendar not current per event declaration");
            }

            log.info("FNOL validation passed for policy: {}", policyNum);
            return new ValidationResult(true);
        }
    }

    static class ValidationResult {
        final boolean valid;
        ValidationResult(boolean valid) { this.valid = valid; }
        boolean isValid() { return valid; }
    }

    static class ValidationException extends RuntimeException {
        ValidationException(String msg) { super(msg); }
    }

    interface PolicyValidationService { boolean validateFormat(String policyNumber); }
    interface AddressGeocodingService { boolean normalizeAndValidate(String address); }
    interface CauseEnumerationService { boolean isAllowed(String cause); }
    interface FreshnessCheckerService { boolean checkPolicyFreshness(String policyNumber); boolean checkMoratoriumCalendar(); }
}

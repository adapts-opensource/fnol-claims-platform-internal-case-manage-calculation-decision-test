# Plan — FNOL Claims Platform

Package family: `test_package`

## Features and user stories

### Feature: Internal Case Management:calculation:decision

The FNOL capability serves as the authoritative entry point for the claims lifecycle. It ingests loss reports via web, mobile, IVR, agent portal, and system integrations. The platform performs real-time policy matching against active/recently expired policies, applies multi-factor similarity scoring for duplicate detection, classifies initial claim types, assigns routing priorities, generates statutory acknowledgments, calculates initial reserves based on claim type and exposure, and constructs a Guidewire-style Claim/Incident/Exposure model. All decisions are auditable, explainable, configurable, and built for high-volume catastrophic event handling with strict SLA adherence.


#### US-001: Policyholder/Agent submits FNOL intake

**Persona:** Claims Intake Specialist (Agent/Policyholder)

**Persona type:** primary_business_user

**Trigger:** Policyholder or authorized agent reports a loss via web portal, mobile app, IVR, or phone

**Business value:** Reduces intake friction, ensures accurate data capture, accelerates statutory acknowledgment, and improves customer experience during stressful events.

**Priority:** P0

**Preconditions:** ['User is authenticated or provided with valid policy/risk identifiers', 'Channel integration is online and accepting submissions']

**Story:** As a policyholder or agent, I want to submit a complete loss report across multiple channels so that the claim can be accurately matched to my policy, validated, and routed to the appropriate handling path without delay.

**Algorithm to be tested — Multi-Channel Intake Validation & Normalization Algorithm**

- Purpose: Validate, normalize, and enrich incoming FNOL data before persistence
- Applies when: Intake submission received from any channel
- Description: Parses raw intake payload, validates required fields, normalizes addresses/dates, enriches with channel metadata, and prepares for policy matching
- Input criteria:
  - Required inputs:
    - policy_number_or_risk_address
    - date_of_loss
    - cause_of_loss
    - reporter_type
    - contact_information
  - Optional inputs:
    - estimated_damages
    - damaged_area_description
    - prior_claim_reference
  - Input validation:
    - Date of loss must not be in the future
    - Policy number must match format regex for FL property forms
    - Cause of loss must map to reference taxonomy
    - Required fields cannot be null or empty
  - Freshness requirements:
    - Real-time processing required (< 2 seconds)
  - Success outputs:
    - Normalized intake record with correlation_id
    - {'Validation status': 'PASSED'}
  - Failure outputs:
    - {'Validation status': 'FAILED with field-level errors'}
  - Status updates:
    - {'IntakeStatus': 'SUBMITTED -> VALIDATED'}
  - Emitted events:
    - fnol.intake.validated
  - User-visible outputs:
    - Confirmation message with intake reference number


#### US-002: Resolve ambiguous policy matches

**Persona:** FNOL Triage Analyst

**Persona type:** operations_user

**Trigger:** System identifies multiple active policies matching the reported risk/address/insured

**Business value:** Prevents misallocation of reserves, ensures accurate coverage determination, and reduces rework from downstream handling errors.

**Priority:** P0

**Preconditions:** ['Intake record is validated', 'Policy match algorithm returns >1 candidate with confidence score > threshold but < 100%']

**Story:** As a triage analyst, I want to review and resolve ambiguous policy matches so that the claim is correctly attributed to the right policy, coverage, and handling path.

**Algorithm to be tested — Policy Match Resolution Scoring Algorithm**

- Purpose: Rank and resolve multiple candidate policies based on weighted attributes
- Applies when: Multiple policy candidates returned for a single intake
- Description: Calculates match confidence using policy number exactness, address proximity, insured name similarity, effective/expiration dates, product form, and occupancy type
- Input criteria:
  - Required inputs:
    - reported_policy_number
    - reported_risk_address
    - named_insured_identity
    - date_of_loss
    - product_form
    - occupancy_relationship
  - Optional inputs:
    - tenant_landlord_flag
    - prior_claim_history
  - Input validation:
    - {'All candidate policies must be active or expired within [Example': '30 days]'}
    - Address must be normalized before comparison
  - Freshness requirements:
    - Policy status must reflect real-time PAS data
  - Success outputs:
    - Resolved policy ID with confidence score
    - Task ID if manual review required
  - Failure outputs:
    - {'No match': 'Create Unmatched FNOL shell'}
  - Status updates:
    - {'PolicyMatchStatus': 'PENDING -> RESOLVED or REVIEW_REQUIRED or UNMATCHED'}
  - Emitted events:
    - fnol.policy.matched
    - fnol.policy.review_requested
  - User-visible outputs:
    - Task queue item for analyst with candidate comparison view


#### US-003: Detect and review potential duplicate claims

**Persona:** Claims Handler/Adjuster

**Persona type:** secondary_business_user

**Trigger:** System identifies overlapping loss reports for same risk, date, cause, or event

**Business value:** Prevents double-paying claims, maintains accurate loss history, and streamlines file management.

**Priority:** P0

**Preconditions:** ['Intake validated and policy matched', 'Duplicate detection algorithm returns similarity score > threshold']

**Story:** As a claims handler, I want to review potential duplicate claims so that I can merge, reopen, or reject duplicates without creating fragmented files or compromising audit trails.

**Algorithm to be tested — Duplicate Claim Similarity Scoring Algorithm**

- Purpose: Identify likely duplicate claims using multi-factor similarity
- Applies when: New intake processed after policy match
- Description: Compares policy number, risk address, date of loss, cause of loss, catastrophe event, reporter, damaged area, and prior claim status against open/recent claims
- Input criteria:
  - Required inputs:
    - policy_number
    - risk_address
    - date_of_loss
    - cause_of_loss
  - Optional inputs:
    - catastrophe_event_id
    - reporter_identity
    - damaged_area_description
    - prior_claim_status
  - Input validation:
    - {'Only open, reopened, or recently closed claims (within [Example': '12 months]) are considered'}
    - Dates must be within plausible loss windows
  - Freshness requirements:
    - Claims database must reflect real-time status
  - Success outputs:
    - Review task with candidate claim ID and similarity breakdown
    - Auto-merge suggestion if > 0.95
  - Failure outputs:
    - N/A (algorithm always produces a probability)
  - Status updates:
    - {'DuplicateStatus': 'DETECTED -> UNDER_REVIEW or MERGED or CONTINUED'}
  - Emitted events:
    - fnol.duplicate.detected
    - fnol.duplicate.reviewed
  - User-visible outputs:
    - Duplicate review panel with side-by-side claim comparison


#### US-004: Assign initial claim type and routing path

**Persona:** FNOL Triage Analyst

**Persona type:** operations_user

**Trigger:** Policy matched and duplicate check completed

**Business value:** Accelerates handling time, ensures appropriate resource allocation, and maintains SLA compliance.

**Priority:** P1

**Preconditions:** ['Policy matched and duplicate resolved', 'Classification rules are loaded and active']

**Story:** As a triage analyst, I want the system to automatically classify the claim type and assign a handling path so that the claim reaches the right handler or vendor quickly.

**Algorithm to be tested — Initial Claim Classification & Routing Algorithm**

- Purpose: Assign claim type and routing path based on loss characteristics
- Applies when: Duplicate check passed
- Description: Evaluates cause of loss, damage extent, coverage type, statutory flags, and vendor eligibility to assign classification and routing
- Input criteria:
  - Required inputs:
    - cause_of_loss
    - estimated_damages
    - coverage_type
    - statutory_flags
  - Optional inputs:
    - vendor_eligibility
    - litigation_indicator
  - Input validation:
    - Cause must map to classification taxonomy
    - Estimated damages must be numeric or null
  - Freshness requirements:
    - Classification rules must reflect current regulatory version
  - Success outputs:
    - {'ClaimType': 'Standard property claim'}
    - Routing assignment ID
  - Failure outputs:
    - N/A (always produces a classification)
  - Status updates:
    - {'ClaimStatus': 'CLASSIFIED -> ROUTED'}
  - Emitted events:
    - fnol.classification.assigned
    - fnol.routing.created
  - User-visible outputs:
    - Classification badge and assigned queue


#### US-005: Generate statutory acknowledgments

**Persona:** Compliance Auditor

**Persona type:** compliance_or_audit_user

**Trigger:** Claim successfully classified and routed

**Business value:** Prevents regulatory penalties, maintains consumer trust, and ensures exam-ready documentation.

**Priority:** P0

**Preconditions:** ['Claim is in ROUTED status', 'Acknowledgment templates are configured']

**Story:** As a compliance auditor, I want the system to automatically generate and deliver statutory acknowledgments within regulatory timeframes so that we remain compliant with Florida prompt pay and FNOL reporting requirements.

**Algorithm to be tested — Statutory Acknowledgment Generation Algorithm**

- Purpose: Create and deliver required regulatory notifications within SLA
- Applies when: Claim reaches ROUTED status
- Description: Pulls claim metadata, applies jurisdictional rules, generates acknowledgment content, and dispatches via configured channels
- Input criteria:
  - Required inputs:
    - claim_id
    - reporter_contact
    - jurisdiction
  - Optional inputs:
    - preferred_channel
    - language_preference
  - Input validation:
    - Jurisdiction must match FL property rules
    - Contact info must be valid
  - Freshness requirements:
    - Templates must reflect current regulatory version
  - Success outputs:
    - Acknowledgment ID
    - {'Delivery status': 'SENT'}
  - Failure outputs:
    - {'Delivery status': 'FAILED -> RETRY'}
  - Status updates:
    - {'AcknowledgmentStatus': 'PENDING -> SENT or FAILED'}
  - Emitted events:
    - fnol.acknowledgment.generated
    - fnol.acknowledgment.sent
  - User-visible outputs:
    - Acknowledgment sent to reporter


#### US-006: Manage classification and routing configuration

**Persona:** Rules & Configuration Manager

**Persona type:** admin_or_configuration_user

**Trigger:** Business requires update to claim classification or routing rules

**Business value:** Reduces time-to-market for rule changes, minimizes deployment risk, and ensures business agility.

**Priority:** P1

**Story:** As a configuration manager, I want to update classification and routing rules without code deployment so that the system can adapt to regulatory or business changes quickly.

package app.services;

import app.utilities.E2eRuntimeContext;
import app.models.Claim;
import app.models.ClaimOutcome;
import app.models.ClaimState;

public class OrchestrationDecisionService {

    public ClaimOutcome decideAndRoute(String arg0) {
        return new ClaimOutcome();
    }

    public ClaimState decide(String arg0) {
        return new ClaimState();
    }

    public Claim submitAndOrchestrate(String arg0) {
        return new Claim();
    }

}

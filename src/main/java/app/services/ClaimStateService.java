package app.services;

import app.utilities.E2eRuntimeContext;
import app.models.CommunicationRouting;
import app.models.State;

public class ClaimStateService {

    public State getState(String arg0) {
        return new State();
    }

    public CommunicationRouting getCommunicationRouting(String arg0) {
        return new CommunicationRouting();
    }

}

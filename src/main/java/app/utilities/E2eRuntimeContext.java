package app.utilities;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class E2eRuntimeContext {
    private static final Map<String, Object> records = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> workflowFlags = new ConcurrentHashMap<>();

    private E2eRuntimeContext() {}

    public static void registerRecord(String location, Object record) {
        records.put(location, record);
    }

    public static Optional<Object> findRecord(String location) {
        return Optional.ofNullable(records.get(location));
    }

    public static void markWorkflowStarted(String key) {
        workflowFlags.put(key + ":started", true);
    }

    public static void markValidationCompleted(String key) {
        workflowFlags.put(key + ":validated", true);
    }

    public static boolean isWorkflowStarted(String key) {
        return Boolean.TRUE.equals(workflowFlags.get(key + ":started"));
    }

    public static boolean isValidationCompleted(String key) {
        return Boolean.TRUE.equals(workflowFlags.get(key + ":validated"));
    }
}

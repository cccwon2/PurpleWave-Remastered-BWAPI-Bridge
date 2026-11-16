package probe;

/**
 * Observable probe lifecycle states. CONNECTED is intentionally omitted from
 * automatic emission: JBWAPI's public {@code BWEventListener} has no IPC-connect
 * callback, only match lifecycle ({@code onStart}/{@code onEnd}/{@code onFrame}).
 */
public enum ProbeState {
    PROCESS_STARTED,
    WAITING_FOR_BRIDGE,
    MATCH_STARTED,
    MATCH_ENDED,
    FAILED
}

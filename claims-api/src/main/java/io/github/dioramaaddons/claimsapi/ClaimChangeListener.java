package io.github.dioramaaddons.claimsapi;

/** Called on the server thread after a claim is created, modified or removed. */
@FunctionalInterface
public interface ClaimChangeListener {
    void onClaimChanged(ClaimChange change);

    enum Type { CREATED, UPDATED, REMOVED }

    /**
     * @param claim snapshot after the change; for {@link Type#REMOVED} the last known state
     */
    record ClaimChange(Type type, ClaimInfo claim) {}
}

package ca.xef5000.playerprofiles.api.data;

public class PlayerIdentity {
    private final IdentityData originalIdentity;
    private IdentityData appliedIdentity;

    public PlayerIdentity(IdentityData originalIdentity) {
        this.originalIdentity = originalIdentity;
        this.appliedIdentity = originalIdentity;
    }

    public IdentityData getOriginalIdentity() {
        return originalIdentity;
    }

    public IdentityData getAppliedIdentity() {
        return appliedIdentity;
    }

    public void setAppliedIdentity(IdentityData appliedIdentity) {
        this.appliedIdentity = appliedIdentity;
    }
}

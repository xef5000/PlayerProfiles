package ca.xef5000.playerprofiles.permissions;

import ca.xef5000.playerprofiles.PlayerProfiles;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.MutableContextSet;
import net.luckperms.api.model.user.User;
import org.checkerframework.checker.nullness.qual.NonNull;

public class ProfileContextCalculator implements ContextCalculator<User> {

    private final PlayerProfiles plugin;

    public ProfileContextCalculator(PlayerProfiles plugin) {
        this.plugin = plugin;
    }

    @Override
    public void calculate(@NonNull User user, @NonNull ContextConsumer contextConsumer) {
        contextConsumer.accept("profile", user.getUniqueId().toString());
    }

    @Override
    public @NonNull ContextSet estimatePotentialContexts() {
        return MutableContextSet.of("profile", "default");
    }
}

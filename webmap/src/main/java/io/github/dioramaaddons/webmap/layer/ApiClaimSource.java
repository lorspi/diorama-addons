package io.github.dioramaaddons.webmap.layer;

import io.github.dioramaaddons.claimsapi.ClaimChangeListener;
import io.github.dioramaaddons.claimsapi.ClaimInfo;
import io.github.dioramaaddons.claimsapi.ClaimsApi;
import io.github.dioramaaddons.claimsapi.ClaimsApiProvider;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Claims from Diorama Addons: Claims, through claims-api (bundled in this jar). */
final class ApiClaimSource implements ClaimSource {
    private final ClaimChangeListener listener = change -> dirty = true;
    private @Nullable ClaimsApi api;
    private volatile boolean dirty = true;

    @Override
    public boolean refresh() {
        ClaimsApi now = ClaimsApiProvider.get().orElse(null);
        if (now != api) {
            if (api != null) {
                api.removeListener(listener);
            }
            api = now;
            if (api != null) {
                api.addListener(listener);
            }
            dirty = true;
        }
        return api != null;
    }

    @Override
    public boolean available() {
        return api != null;
    }

    @Override
    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    @Override
    public Collection<ClaimInfo> claims() {
        return api == null ? List.of() : api.allClaims();
    }

    @Override
    public void stop() {
        if (api != null) {
            api.removeListener(listener);
            api = null;
        }
    }
}

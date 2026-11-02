package io.github.dioramaaddons.webmap.layer;

import com.mojang.logging.LogUtils;
import io.github.dioramaaddons.claimsapi.ClaimInfo;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Claims from the Mochos Esenciales mod, which ships its own copy of claims-api in another package. It is read
 * by reflection so that this mod neither depends on it nor bundles it; the only contract is the shape of that
 * API (same names as claims-api). If it ever changes shape, the layer logs one warning and stays off.
 */
final class MochosClaimSource implements ClaimSource {
    static final String MOD_ID = "mochos_esenciales";
    private static final String PKG = "io.github.mochos.esenciales.claims.api.";
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Without a working listener the claims are read again this often (ticks). */
    private static final int POLL_TICKS = 600;

    private final Method providerGet;
    private final Method allClaims;
    private final Method addListener;
    private final Method removeListener;
    private final Class<?> listenerType;
    private final Method id, name, ownerId, ownerName, dimension, chunks;
    private final Object listenerProxy;
    private @Nullable Object api;
    private boolean listening;
    private volatile boolean dirty = true;
    private boolean broken;
    private int sincePoll;

    private MochosClaimSource() throws ReflectiveOperationException {
        ClassLoader loader = MochosClaimSource.class.getClassLoader();
        Class<?> provider = Class.forName(PKG + "ClaimsApiProvider", true, loader);
        Class<?> apiType = Class.forName(PKG + "ClaimsApi", true, loader);
        Class<?> info = Class.forName(PKG + "ClaimInfo", true, loader);
        listenerType = Class.forName(PKG + "ClaimChangeListener", true, loader);
        providerGet = provider.getMethod("get");
        allClaims = apiType.getMethod("allClaims");
        addListener = apiType.getMethod("addListener", listenerType);
        removeListener = apiType.getMethod("removeListener", listenerType);
        id = info.getMethod("id");
        name = info.getMethod("name");
        ownerId = info.getMethod("ownerId");
        ownerName = info.getMethod("ownerName");
        dimension = info.getMethod("dimension");
        chunks = info.getMethod("chunks");
        listenerProxy = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] {listenerType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "onClaimChanged" -> {
                        dirty = true;
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "webmap claims listener";
                    default -> null;
                });
    }

    /** Null when Mochos Esenciales is not installed or its claims API cannot be read. */
    static @Nullable MochosClaimSource create() {
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded(MOD_ID)) {
            return null;
        }
        try {
            return new MochosClaimSource();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("Web map: Mochos Esenciales is installed but its claims API could not be read; its land claims "
                    + "will not be shown ({})", e.toString());
            return null;
        }
    }

    @Override
    public boolean refresh() {
        if (broken) {
            return false;
        }
        try {
            Object now = ((Optional<?>) providerGet.invoke(null)).orElse(null);
            if (now != api) {
                detach();
                api = now;
                if (api != null) {
                    try {
                        addListener.invoke(api, listenerProxy);
                        listening = true;
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        listening = false;
                    }
                }
                dirty = true;
            } else if (api != null && !listening && ++sincePoll * 100 >= POLL_TICKS) {
                sincePoll = 0;
                dirty = true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
        return api != null;
    }

    @Override
    public boolean available() {
        return api != null && !broken;
    }

    @Override
    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    @Override
    public Collection<ClaimInfo> claims() {
        if (api == null || broken) {
            return List.of();
        }
        try {
            Collection<?> all = (Collection<?>) allClaims.invoke(api);
            List<ClaimInfo> out = new ArrayList<>(all.size());
            for (Object c : all) {
                Set<Long> packed = new HashSet<>();
                for (Object o : (Collection<?>) chunks.invoke(c)) {
                    packed.add((Long) o);
                }
                out.add(new ClaimInfo((UUID) id.invoke(c), (String) name.invoke(c), (UUID) ownerId.invoke(c),
                        (String) ownerName.invoke(c), (String) dimension.invoke(c), packed));
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return List.of();
        }
    }

    @Override
    public void stop() {
        detach();
        api = null;
    }

    private void detach() {
        if (api != null && listening) {
            try {
                removeListener.invoke(api, listenerProxy);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // The provider is going away anyway.
            }
        }
        listening = false;
    }

    private void fail(Exception e) {
        broken = true;
        api = null;
        dirty = true;
        LOGGER.warn("Web map: reading land claims from Mochos Esenciales failed; that layer is off until restart", e);
    }
}

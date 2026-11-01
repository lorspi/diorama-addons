package io.github.dioramaaddons.clientmap.core;

/** How something is shared between players, set by the server. */
public enum ShareMode {
    /** Not shared at all. */
    DISABLED,
    /** One shared pool for every player. */
    GLOBAL,
    /** One pool per group of friends; players without a group keep their own. */
    GROUPS
}

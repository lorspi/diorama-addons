package io.github.dioramaaddons.claims.core;

import java.util.Locale;

/** Outcome of a claim operation. Each value maps to a translation key. */
public enum ClaimResult {
    SUCCESS,
    ALREADY_CLAIMED,
    ALREADY_YOURS,
    NOT_CLAIMED,
    NOT_OWNER,
    CHUNK_LIMIT,
    CLAIM_LIMIT,
    MEMBER_LIMIT,
    NOT_CONTIGUOUS,
    WOULD_SPLIT,
    TOO_CLOSE_TO_SPAWN,
    WRONG_DIMENSION,
    INVALID_NAME,
    CLAIM_NOT_FOUND,
    MEMBER_NOT_FOUND,
    CANNOT_ADD_OWNER;

    public boolean ok() {
        return this == SUCCESS;
    }

    public String translationKey() {
        return "dioaddons_claims.result." + name().toLowerCase(Locale.ROOT);
    }
}

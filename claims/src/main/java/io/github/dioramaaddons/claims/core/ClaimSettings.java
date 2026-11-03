package io.github.dioramaaddons.claims.core;

/**
 * Per-claim settings: a welcome and a farewell notification for whoever enters or leaves the claim, and a visitor
 * alarm for the owner and/or the members. Texts may use formatting codes and placeholders (see {@link TextFormat}).
 * An empty text means no notification; an empty title means the claim's name is used.
 */
public record ClaimSettings(String welcomeTitle, String welcome, String farewellTitle, String farewell,
        boolean alarmOwner, boolean alarmMembers) {
    public static final int MAX_TITLE = 64;
    public static final int MAX_TEXT = 256;
    public static final ClaimSettings DEFAULT = new ClaimSettings("", "", "", "", false, false);

    public ClaimSettings {
        welcomeTitle = clip(welcomeTitle, MAX_TITLE);
        welcome = clip(welcome, MAX_TEXT);
        farewellTitle = clip(farewellTitle, MAX_TITLE);
        farewell = clip(farewell, MAX_TEXT);
    }

    /** Control characters and the section sign are removed, spaces trimmed, and the text cut to {@code max}. */
    public static String clip(String s, int max) {
        String c = s == null ? "" : s.replaceAll("[\\p{Cntrl}§]", "").strip();
        return c.length() > max ? c.substring(0, max).strip() : c;
    }

    public boolean isDefault() {
        return equals(DEFAULT);
    }

    public boolean hasWelcome() {
        return !welcome.isEmpty();
    }

    public boolean hasFarewell() {
        return !farewell.isEmpty();
    }

    public boolean alarms() {
        return alarmOwner || alarmMembers;
    }

    public ClaimSettings withWelcome(String title, String text) {
        return new ClaimSettings(title, text, farewellTitle, farewell, alarmOwner, alarmMembers);
    }

    public ClaimSettings withFarewell(String title, String text) {
        return new ClaimSettings(welcomeTitle, welcome, title, text, alarmOwner, alarmMembers);
    }

    public ClaimSettings withAlarms(boolean owner, boolean members) {
        return new ClaimSettings(welcomeTitle, welcome, farewellTitle, farewell, owner, members);
    }
}

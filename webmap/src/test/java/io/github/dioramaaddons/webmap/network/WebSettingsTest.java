package io.github.dioramaaddons.webmap.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WebSettingsTest {
    @Test
    void sanitizedTrimsAndDropsControlCharacters() {
        WebSettings s = new WebSettings("  My\u0000 Map\n ", "\tserver-icon.png ", true, false, true, false).sanitized();
        assertEquals("My Map", s.title());
        assertEquals("server-icon.png", s.favicon());
        assertEquals(true, s.claims());
        assertEquals(false, s.players());
    }

    @Test
    void sanitizedCapsLength() {
        WebSettings s = new WebSettings("x".repeat(500), "y".repeat(2000), false, false, false, false).sanitized();
        assertEquals(WebSettings.MAX_TITLE, s.title().length());
        assertEquals(WebSettings.MAX_FAVICON, s.favicon().length());
    }
}

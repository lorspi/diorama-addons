package io.github.dioramaaddons.claims.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TextFormatTest {
    @Test
    void placeholdersAreFilledAndUnknownOnesKept() {
        assertEquals("Hi Ana, welcome to Farm {nope}",
                TextFormat.fill("Hi {player}, welcome to {claim} {nope}", Map.of("player", "Ana", "claim", "Farm")));
    }

    @Test
    void colourAndStyleCodesSplitRuns() {
        List<TextFormat.Run> runs = TextFormat.runs("&aGreen &lbold&r plain &#FF8800orange");
        assertEquals(4, runs.size());
        assertEquals(new TextFormat.Run("Green ", 0x55FF55, 0), runs.get(0));
        assertEquals(new TextFormat.Run("bold", 0x55FF55, TextFormat.BOLD), runs.get(1));
        assertEquals(new TextFormat.Run(" plain ", -1, 0), runs.get(2));
        assertEquals(new TextFormat.Run("orange", 0xFF8800, 0), runs.get(3));
        assertEquals("Green bold plain orange", TextFormat.strip("&aGreen &lbold&r plain &#FF8800orange"));
    }
}

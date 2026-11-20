package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatSpamTest {

    @Test
    void blocksMessagesOverTheRate() {
        ChatSpam spam = new ChatSpam();
        for (int i = 0; i < 3; i++) {
            assertEquals(ChatSpam.Verdict.OK, spam.check("message " + i, i * 100L, 3, 5000, 0));
        }
        assertEquals(ChatSpam.Verdict.TOO_FAST, spam.check("another", 400, 3, 5000, 0));
        // The first message has left the window.
        assertEquals(ChatSpam.Verdict.OK, spam.check("later", 5001, 3, 5000, 0));
    }

    @Test
    void blockedMessagesDoNotCountTowardTheRate() {
        ChatSpam spam = new ChatSpam();
        spam.check("a", 0, 2, 1000, 0);
        spam.check("b", 10, 2, 1000, 0);
        for (int i = 0; i < 10; i++) {
            assertEquals(ChatSpam.Verdict.TOO_FAST, spam.check("c" + i, 20 + i, 2, 1000, 0));
        }
        assertEquals(ChatSpam.Verdict.OK, spam.check("d", 1010, 2, 1000, 0));
    }

    @Test
    void blocksRepeatsIgnoringCaseAndPunctuation() {
        ChatSpam spam = new ChatSpam();
        assertEquals(ChatSpam.Verdict.OK, spam.check("Buy my stuff!", 0, 10, 1000, 30_000));
        assertEquals(ChatSpam.Verdict.REPEATED, spam.check("buy  my stuff", 5000, 10, 1000, 30_000));
        assertEquals(ChatSpam.Verdict.OK, spam.check("buy my stuff", 31_000, 10, 1000, 30_000));
        assertEquals(ChatSpam.Verdict.OK, spam.check("something else", 32_000, 10, 1000, 30_000));
    }

    @Test
    void punctuationOnlyMessagesCompareAsTyped() {
        assertEquals("!!!", ChatSpam.normalize(" !!! "));
        assertEquals("hello", ChatSpam.normalize("H e-l l o!"));
    }

    @Test
    void countsStrikesWithinTheWindow() {
        ChatSpam spam = new ChatSpam();
        assertEquals(1, spam.strike(0, 60_000));
        assertEquals(2, spam.strike(1000, 60_000));
        assertEquals(1, spam.strike(70_000, 60_000));
        spam.clearStrikes();
        assertEquals(1, spam.strike(70_001, 60_000));
    }
}

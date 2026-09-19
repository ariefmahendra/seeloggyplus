package com.seeloggyplus.util;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class FxTextHighlighterTest {

    private HBox row(String text, Pattern pattern) throws Exception {
        return WaitForAsyncUtils.asyncFx(() -> FxTextHighlighter.build(text, pattern)).get();
    }

    private String plainText(HBox box) {
        StringBuilder sb = new StringBuilder();
        for (Node node : box.getChildren()) {
            if (node instanceof Label label) {
                sb.append(label.getText());
            }
        }
        return sb.toString();
    }

    private List<String> highlighted(HBox box) {
        return box.getChildren().stream()
                .filter(node -> node.getStyleClass().contains(FxTextHighlighter.HIGHLIGHT_STYLE_CLASS))
                .map(node -> ((Label) node).getText())
                .toList();
    }

    @Test
    void highlightsLiteralOccurrencesCaseInsensitively() throws Exception {
        HBox box = row("Timeout detected then TIMEOUT again",
                Pattern.compile(Pattern.quote("timeout"), Pattern.CASE_INSENSITIVE));
        assertEquals(List.of("Timeout", "TIMEOUT"), highlighted(box));
        assertEquals("Timeout detected then TIMEOUT again", plainText(box));
    }

    @Test
    void caseSensitivePatternSkipsDifferentCase() throws Exception {
        HBox box = row("Timeout timeout", Pattern.compile(Pattern.quote("timeout")));
        assertEquals(List.of("timeout"), highlighted(box));
    }

    @Test
    void highlightsRegexMatches() throws Exception {
        HBox box = row("port 8080 and 9090", Pattern.compile("\\d+"));
        assertEquals(List.of("8080", "9090"), highlighted(box));
    }

    @Test
    void nullPatternKeepsTextUnchangedWithoutHighlight() throws Exception {
        HBox box = row("plain line", null);
        assertTrue(highlighted(box).isEmpty());
        assertEquals("plain line", plainText(box));
    }

    @Test
    void noMatchKeepsTextUnchanged() throws Exception {
        HBox box = row("plain line", Pattern.compile(Pattern.quote("missing")));
        assertTrue(highlighted(box).isEmpty());
        assertEquals("plain line", plainText(box));
    }

    @Test
    void zeroLengthRegexDoesNotHangAndPreservesText() throws Exception {
        HBox box = row("banana", Pattern.compile("a*"));
        assertEquals("banana", plainText(box));
        assertFalse(box.getChildren().isEmpty());
    }
}

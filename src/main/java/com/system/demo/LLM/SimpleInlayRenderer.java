package com.system.demo.LLM;

import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorFontType;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 灰色 inline 渲染器
 */
public class SimpleInlayRenderer implements EditorCustomElementRenderer {
    private final String text;

    public SimpleInlayRenderer(String text) {
        this.text = text;
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
        FontMetrics fm = inlay.getEditor().getContentComponent()
                .getFontMetrics(inlay.getEditor().getColorsScheme().getFont(EditorFontType.PLAIN));
        return fm.stringWidth(text);
    }

    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g,
                      @NotNull Rectangle targetRegion, @NotNull TextAttributes textAttributes) {
        g.setColor(Color.GRAY);
        g.drawString(text, targetRegion.x, targetRegion.y + g.getFontMetrics().getAscent());
    }
}

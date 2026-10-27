package org.halim.acsgui.sgui.sglib;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;

import java.awt.*;


/** The colors of the code view's Java tokens, set here and not taken from a library theme, on the workspace's dark ground. */
public class CodeColors {

public static final Color
	PLAIN = new Color(0xA9B7C6),
	KEYWORD = new Color(0xCC7832),
	TYPE = new Color(0x4EC9B0),
	STRING = new Color(0x6A8759),
	NUMBER = new Color(0x6897BB),
	COMMENT = new Color(0x808080),
	DOCUMENTATION = new Color(0x629755),
	ANNOTATION = new Color(0xBBB529),
	METHOD = new Color(0xFFC66D),
	OPERATOR = new Color(0xD0D0D0);

/** Java highlighting on the workspace's ground, every token type colored as above. */
public static void apply(RSyntaxTextArea area) {
	area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
	area.setBackground(Utilities.WP_BG);
	area.setForeground(PLAIN);
	area.setCaretColor(PLAIN);
	area.setCurrentLineHighlightColor(new Color(255, 255, 255, 14));
	area.setSelectionColor(new Color(0x214283));
	area.setAntiAliasingEnabled(true);
	SyntaxScheme scheme = area.getSyntaxScheme();
	for(int type = 0; type < scheme.getStyleCount(); type++) {
		if(scheme.getStyle(type) != null) { scheme.getStyle(type).foreground = PLAIN; }
	}
	paint(scheme, KEYWORD, Token.RESERVED_WORD, Token.RESERVED_WORD_2, Token.LITERAL_BOOLEAN);
	paint(scheme, TYPE, Token.DATA_TYPE);
	paint(scheme, STRING, Token.LITERAL_STRING_DOUBLE_QUOTE, Token.LITERAL_CHAR);
	paint(scheme, NUMBER, Token.LITERAL_NUMBER_DECIMAL_INT, Token.LITERAL_NUMBER_FLOAT, Token.LITERAL_NUMBER_HEXADECIMAL);
	paint(scheme, COMMENT, Token.COMMENT_EOL, Token.COMMENT_MULTILINE);
	paint(scheme, DOCUMENTATION, Token.COMMENT_DOCUMENTATION, Token.COMMENT_KEYWORD, Token.COMMENT_MARKUP);
	paint(scheme, ANNOTATION, Token.ANNOTATION);
	paint(scheme, METHOD, Token.FUNCTION);
	paint(scheme, OPERATOR, Token.OPERATOR, Token.SEPARATOR);
	area.revalidate();
	area.repaint();
}

static void paint(SyntaxScheme scheme, Color color, int... types) {
	for(int type : types) { if(scheme.getStyle(type) != null) { scheme.getStyle(type).foreground = color; } }
}

}

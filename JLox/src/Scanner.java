import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scanner {
	private final String source;
	private final List<Token> tokens = new ArrayList<>();
	private int start = 0;
	private int current = 0;
	private int line = 1;
	private static final Map<String, TokenType> keywords;

	static {
		keywords = new HashMap<>();
		keywords.put("and", TokenType.AND);
		keywords.put("class", TokenType.CLASS);
		keywords.put("else", TokenType.ELSE);
		keywords.put("false", TokenType.FALSE);
		keywords.put("for", TokenType.FOR);
		keywords.put("fun", TokenType.FUN);
		keywords.put("if", TokenType.IF);
		keywords.put("nil", TokenType.NIL);
		keywords.put("or", TokenType.OR);
		keywords.put("print", TokenType.PRINT);
		keywords.put("return", TokenType.RETURN);
		keywords.put("super", TokenType.SUPER);
		keywords.put("this", TokenType.THIS);
		keywords.put("true", TokenType.TRUE);
		keywords.put("var", TokenType.VAR);
		keywords.put("while", TokenType.WHILE);
	}

	Scanner(String source) {
		this.source = source;
	}

	/*
	 * The scanner works its way through the source code, adding tokens until it
	 * runs out of characters. Then it appends one final �end of file� token. That
	 * isn�t strictly needed, but it makes our parser a little cleaner.
	 */
	List<Token> scanTokens() {
		while (!isAtEnd()) {
			start = current;
			scanToken();
		}
		tokens.add(new Token(TokenType.EOF, "", null, line));
		return tokens;
	}

	/* Each turn of the loop, we scan a single token. */
	private void scanToken() {
		char c = advance();
		switch (c) {
		case '(':
			addToken(TokenType.LEFT_PAREN);
			break;
		case ')':
			addToken(TokenType.RIGHT_PAREN);
			break;
		case '{':
			addToken(TokenType.LEFT_BRACE);
			break;
		case '}':
			addToken(TokenType.RIGHT_BRACE);
			break;
		case ',':
			addToken(TokenType.COMMA);
			break;
		case '.':
			addToken(TokenType.DOT);
			break;
		case '-':
			addToken(TokenType.MINUS);
			break;
		case '+':
			addToken(TokenType.PLUS);
			break;
		case ';':
			addToken(TokenType.SEMICOLON);
			break;
		case '*':
			addToken(TokenType.STAR);
			break;
		case '!':
			addToken(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
			break;
		case '=':
			addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
			break;
		case '<':
			addToken(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
			break;
		case '>':
			addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
			break;
		case '/':
			if (match('/')) {
				// A comment goes until the end of the line.
				while (peek() != '\n' && !isAtEnd())
					advance();
			} else {
				addToken(TokenType.SLASH);
			}
			break;
		case ' ':
		case '\r':
		case '\t':
			break;
		case '\n':
			line++;
			break;
		case '"':
			string();
			break;
		default:
			if (isDigit(c)) {
				number();
			} else if (isAlpha(c)) {
				identifier();
			} else {
				Lox.error(line, "Unexpected character.");
			}
			break;
		}
	}

	// Return true if the passed character is a number.
	private boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	// Substring the variable name (identifier), get it's token type
	// from our map, and add it to the ArrayList of tokens.
	private void identifier() {
		while (isAlphaNumeric(peek()))
			advance();
		String text = source.substring(start, current);
		TokenType type = keywords.get(text);
		if (type == null)
			type = TokenType.IDENTIFIER;
		addToken(type);
	}

	// Return true if the character passed is alphabet.
	private boolean isAlpha(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
	}

	// Return true if the character passed is alphabet or number.
	private boolean isAlphaNumeric(char c) {
		return isAlpha(c) || isDigit(c);
	}

	// Substring the number value from the source code and add it to the ArrayList of tokens.
	private void number() {
		while (isDigit(peek()))
			advance();
		// Look for a fractional part.
		if (peek() == '.' && isDigit(peekNext())) {
			// Consume the "."
			advance();
			while (isDigit(peek()))
				advance();
		}
		addToken(TokenType.NUMBER, Double.parseDouble(source.substring(start, current)));
	}

	// Return the next character without moving the cursor.
	private char peekNext() {
		if (current + 1 >= source.length())
			return '\0';
		return source.charAt(current + 1);
	}

	// Work on the string until it reach the " at the end of it.
	// Substring it and add it to the ArrayList of tokens.
	private void string() {
		while (peek() != '"' && !isAtEnd()) {
			if (peek() == '\n')
				line++;
			advance();
		}
		if (isAtEnd()) {
			Lox.error(line, "Unterminated string.");
			return;
		}
		// The closing ".
		advance();
		// Trim the surrounding quotes.
		String value = source.substring(start + 1, current - 1);
		addToken(TokenType.STRING, value);
	}

	// Return current character without moving the cursor.
	private char peek() {
		if (isAtEnd())
			return '\0';
		return source.charAt(current);
	}

	// Compare the current character with the expected one.
	private boolean match(char expected) {
		if (isAtEnd())
			return false;
		if (source.charAt(current) != expected)
			return false;
		current++;
		return true;
	}

	// Return current character and move cursor one character more.
	private char advance() {
		current++;
		return source.charAt(current - 1);
	}

	private void addToken(TokenType type) {
		addToken(type, null);
	}

	// Substring a token from the source code and add it to the ArrayList of tokens.
	private void addToken(TokenType type, Object literal) {
		String text = source.substring(start, current);
		tokens.add(new Token(type, text, literal, line));
	}

	// Returns true if your cursor reaches the end of the file.
	private boolean isAtEnd() {
		return current >= source.length();
	}
}

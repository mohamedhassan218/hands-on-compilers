import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
	static boolean hadError = false;

	public static void main(String[] args) throws IOException {
		if (args.length > 1) {
			System.out.println("Usage: jlox [script]");
			System.exit(64);
		} else if (args.length == 1) {
			runFile(args[0]);
		} else {
			runPrompt();
		}
	}

	/*
	 * In case you want to use our interpreter from the command line, you pass the
	 * path of the file, we call this method, which calls run() method to run it.
	 */
	private static void runFile(String path) throws IOException {
		byte[] bytes = Files.readAllBytes(Paths.get(path));
		run(new String(bytes, Charset.defaultCharset()));

		if (hadError)
			System.exit(64);
	}

	/*
	 * In case you want to use our interpreter interactively, you fire it up, and
	 * enter and execute code, one line at a time.
	 * 
	 * To kill an interactive command-line app, you usually type Control-D. Doing so
	 * signals an “end-of-file” condition to the program. When that happens,
	 * readLine() returns null, so we check for that to exit the loop.
	 */
	private static void runPrompt() throws IOException {
		InputStreamReader input = new InputStreamReader(System.in);
		BufferedReader reader = new BufferedReader(input);
		for (;;) {
			System.out.print("> ");
			String line = reader.readLine();
			if (line == null)
				break;
			run(line);
			hadError = false;
		}
	}

	/* Method responsible to run source code. */
	private static void run(String source) throws IOException {
		Scanner scanner = new Scanner(source);
		List<Token> tokens = scanner.scanTokens();

		// Just print the tokens
		for (Token token : tokens)
			System.out.println(token);
	}

	/* Method to call the report message to print out the error's details. */
	static void error(int line, String message) {
		report(line, "", message);
	}

	/* Method to print out the error with details through the standard error. */
	private static void report(int line, String where, String message) {
		System.err.println("[line " + line + "] Error" + where + ": " + message);
		hadError = true;
	}

}

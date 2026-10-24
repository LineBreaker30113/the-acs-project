package org.halim.acsessgui.acsess;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Answer;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Refusal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


/**
 * The acsess program, run as a terminal runs it: its arguments on the command line, or a session's lines
 * on its standard input, and its exit status and printed answer read back. Nothing here speaks the frame
 * wire, so this program needs no part of ACSES to build, and talks to whatever acsed that acsess finds
 * through {@code $ACSED_SOCKET} or the runtime folder, the one a terminal's acsess talks to.
 * <p>
 * A command costs a program run and two round trips; a session costs one run, a probe per line and one
 * connection, which is why a table is read as one session. A value acsess prints is taken as printed:
 * an array an element a line, a void request as "done". A refusal's text is acsess's, or acsed's reason.
 * */
public class Acsess {

static final long TIMEOUT_SECONDS = 120;

/** The program and the words before the arguments, such as a path to {@code bin/acsess}. */
final List<String> program;

public Acsess(List<String> program) { this.program = List.copyOf(program); }

record Run(int status, String out, String err) {}

/** One command: the printed answer when done; a Refusal carrying what acsess printed otherwise. */
public String command(List<String> arguments) {
	Run run = run(arguments, null);
	if(run.status != 0) { throw new Refusal(run.err.isEmpty() ? run.out : run.err); }
	return run.out;
}

public String command(String... arguments) { return command(List.of(arguments)); }

/**
 * The requests as one session, sent as one connection when its Commit line comes. A line acsess cannot
 * translate is not queued, and the answers would then not line up with the requests, so that is a Refusal
 * of the whole session, with acsess's reason.
 * */
public List<Answer> session(List<List<String>> requests, boolean continueOnRefusal) {
	ArrayList<String> lines = new ArrayList<>();
	for(List<String> request : requests) { lines.add(line(request)); }
	return typedSession(lines, continueOnRefusal);
}

/** Lines as a person types them into a session, split by acsess's own rules; the console's way in. */
public List<Answer> typedSession(List<String> lines, boolean continueOnRefusal) {
	if(lines.isEmpty()) { return List.of(); }
	StringBuilder input = new StringBuilder();
	for(String line : lines) { input.append(line).append('\n'); }
	input.append(continueOnRefusal ? "Commit +continueOnRefusal" : "Commit").append('\n');
	int requests = lines.size();
	Run run = run(List.of("beginSession"), input.toString());
	String printed = run.status == 0 ? run.out : run.err;
	if(run.status == 0 && !run.err.isEmpty()) { throw new Refusal(run.err); }
	ArrayList<Answer> answers = new ArrayList<>();
	StringBuilder stray = new StringBuilder();
	String typed = null;
	StringBuilder text = null;
	for(String line : printed.split("\n", -1)) {
		if(line.startsWith("> ")) {
			if(typed != null) { answers.add(answer(typed, text)); }
			typed = line.substring(2);
			text = null;
		} else if(typed != null && line.startsWith("  ")) {
			if(text == null) { text = new StringBuilder(); } else { text.append('\n'); }
			text.append(line, 2, line.length());
		} else if(!line.isEmpty()) {
			if(!stray.isEmpty()) { stray.append('\n'); }
			stray.append(line);
		}
	}
	if(typed != null) { answers.add(answer(typed, text)); }
	if(!stray.isEmpty() || answers.size() != requests) { throw new Refusal(stray.isEmpty() ? printed : stray.toString()); }
	return answers;
}

/**
 * acsess prints a refusal as {@code refused: <reason>} and a request after a stop as {@code not run}; a
 * done answer whose value reads the same is told apart only by the session's status, which is lost per
 * request, so such a stored string reads as a refusal here.
 * */
static Answer answer(String typed, StringBuilder text) {
	String body = text == null ? "" : text.toString();
	if(body.startsWith("refused: ")) { return new Answer(typed, false, body.substring(9)); }
	if(body.equals("not run")) { return new Answer(typed, false, body); }
	return new Answer(typed, true, body);
}

/** The arguments as one session line, each quoted the way acsess splits it back. */
static String line(List<String> arguments) {
	StringBuilder line = new StringBuilder();
	for(String argument : arguments) {
		if(!line.isEmpty()) { line.append(' '); }
		line.append(quoted(argument));
	}
	return line.toString();
}

/**
 * An argument acsess's splitting gives back unchanged: bare when it holds no blank and no quoting
 * character; a valued flag keeps its {@code name=} bare, so it stays a flag; in double quotes when it
 * fits on one line; in a triple backquote fence when it holds a line break; refused when it holds both a
 * line break and a fence.
 * */
static String quoted(String argument) {
	int equals = argument.indexOf('=');
	if(equals > 0 && argument.substring(0, equals).chars().allMatch(Character::isJavaIdentifierPart)) {
		return argument.substring(0, equals + 1) + quotedValue(argument.substring(equals + 1));
	}
	return quotedValue(argument);
}

static String quotedValue(String value) {
	if(!value.isEmpty() && value.chars().noneMatch(c -> Character.isWhitespace(c) || "\"'`\\".indexOf(c) >= 0)) { return value; }
	if(value.indexOf('\n') < 0 && value.indexOf('\r') < 0) { return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"'; }
	if(value.contains("```")) { throw new Refusal("a value holding both a line break and ``` cannot be typed to acsess"); }
	return "```" + value + "```";
}

Run run(List<String> arguments, String input) {
	ArrayList<String> command = new ArrayList<>(program);
	command.addAll(arguments);
	Process process;
	try { process = new ProcessBuilder(command).start(); }
	catch(IOException e) { throw new Refusal("cannot run " + String.join(" ", program) + ": " + e.getMessage()); }
	CompletableFuture<String> out = drained(process.getInputStream()), err = drained(process.getErrorStream());
	try(OutputStream stdin = process.getOutputStream()) {
		if(input != null) { stdin.write(input.getBytes(StandardCharsets.UTF_8)); }
	} catch(IOException e) { throw new Refusal("acsess stopped reading its input: " + e.getMessage()); }
	try {
		if(!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new Refusal("acsess gave no answer within " + TIMEOUT_SECONDS + " s");
		}
		return new Run(process.exitValue(), trimmed(out.join()), trimmed(err.join()));
	} catch(InterruptedException e) {
		process.destroyForcibly();
		Thread.currentThread().interrupt();
		throw new Refusal("interrupted while acsess ran");
	}
}

static CompletableFuture<String> drained(InputStream stream) {
	return CompletableFuture.supplyAsync(() -> {
		try(stream) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			stream.transferTo(bytes);
			return bytes.toString(StandardCharsets.UTF_8);
		} catch(IOException e) { throw new UncheckedIOException(e); }
	});
}

/** Without the one line break println ends the answer with. */
static String trimmed(String printed) { return printed.endsWith("\n") ? printed.substring(0, printed.length() - 1) : printed; }

}

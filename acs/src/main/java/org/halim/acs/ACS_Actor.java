package org.halim.acs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * A component with two sides: to the journal it exposes steps, which undo and redo replay unchecked; to
 * the outside it exposes requests, each a method marked {@link ACS_Requestable}, which keep their checks
 * and call the checkless parts. The requests travel as data streams rather than objects, since a request
 * takes any number of inputs of any type and an object per request is overhead a database of 2^25
 * records cannot carry; their names stay with whoever translates them, and only codes reach the actor.
 * <p>
 * Since multistep activities require each step being undone for restoring the former structure, we need to record the steps each.
 * A step is a unit action that changes only one thing, deterministic, doesn't matter at which order it is done, and undoable.
 * Important detail is the structure is responsible only for restoring the data, it doesn't matter how the data is represented,
 * adding an element may increase the capacity of the arraylist, but undoing is not responsible for reducing its capacity back.
 **/
public interface ACS_Actor {

/** stepPair index starts from 1 instead of 0, and negative means the inverse, and the 0 means not undoable */
void doTraceableStep(byte stepPair, int[] params, int index);
//	default boolean isOrderDependent() { return false; } out of scope

/**
 * Runs one request: {@code parameters} starts with its code, an int, then its inputs as
 * {@link DataOutputStream} writes them, a string by {@link #writeString}; its result goes to
 * {@code returns} the same way. False when a check or the method failed, its error's text then written to
 * {@code returns} as a string; an IOException is the streams' failure, not the request's. An actor that
 * takes no requests keeps this default, which refuses every one.
 * */
default boolean request(DataInputStream parameters, DataOutputStream returns) throws IOException {
	int code = parameters.readInt();
	writeString(returns, getClass().getName() + " takes no requests; asked for code " + code);
	return false;
}

/** One line per request, in code order, as {@link #writeRequestables} writes them for this actor's class. */
default void getRequestables(DataOutputStream dos) throws IOException { writeRequestables(getClass(), dos); }

/**
 * Writes {@code name(type name, ...) result} and a line break, in UTF-8, for every public method of
 * {@code actor} marked {@link ACS_Requestable}, on itself or on the contract declaring it, in code order. Names come from reflection, so the class
 * must be compiled with {@code -parameters}; a type the grammar lacks, a code repeated or skipped,
 * and a parameter without its name are refused before anything is written.
 * */
static void writeRequestables(Class<?> actor, DataOutputStream dos) throws IOException {
	List<Method> marked = Arrays.stream(actor.getMethods())
		.filter(method -> !method.isBridge() && !method.isSynthetic() && markOf(actor, method) != null)
		.sorted(Comparator.comparingInt(method -> markOf(actor, method).value()))
		.toList();
	StringBuilder lines = new StringBuilder();
	for(int code = 0; code < marked.size(); code++) {
		Method method = marked.get(code);
		int given = markOf(actor, method).value();
		if(given != code) { throw new IllegalStateException(actor.getName() + "." + method.getName() + " has code " + given + " where " + code + " is due; codes run 0 to " + (marked.size() - 1) + " once each"); }
		lines.append(method.getName()).append('(');
		Parameter[] parameters = method.getParameters();
		for(int i = 0; i < parameters.length; i++) {
			if(!parameters[i].isNamePresent()) { throw new IllegalStateException(actor.getName() + "." + method.getName() + " was compiled without -parameters"); }
			if(i > 0) { lines.append(", "); }
			lines.append(typeName(actor, method, parameters[i].getType())).append(' ').append(parameters[i].getName());
		}
		lines.append(") ").append(method.getReturnType() == void.class ? "void" : typeName(actor, method, method.getReturnType())).append('\n');
	}
	dos.write(lines.toString().getBytes(StandardCharsets.UTF_8));
}

/** The names of {@code actor}'s requests by their codes, from the marks as {@link #writeRequestables} reads them, so no name is a literal. */
static String[] requestNames(Class<?> actor) {
	java.util.ArrayList<String> names = new java.util.ArrayList<>();
	for(Method method : actor.getMethods()) {
		ACS_Requestable mark = method.isBridge() ? null : markOf(actor, method);
		if(mark == null) { continue; }
		while(names.size() <= mark.value()) { names.add(null); }
		names.set(mark.value(), method.getName());
	}
	return names.toArray(new String[0]);
}

/**
 * The mark of {@code method}: its own, or the one on the same method where an interface of {@code type}
 * declares it, since the contract marks a request and the class implementing it carries no mark.
 * */
private static ACS_Requestable markOf(Class<?> type, Method method) {
	if(type == null) { return null; }
	try {
		ACS_Requestable mark = type.getDeclaredMethod(method.getName(), method.getParameterTypes()).getAnnotation(ACS_Requestable.class);
		if(mark != null) { return mark; }
	}
	catch(NoSuchMethodException absent) { }
	for(Class<?> contract : type.getInterfaces()) {
		ACS_Requestable mark = markOf(contract, method);
		if(mark != null) { return mark; }
	}
	return markOf(type.getSuperclass(), method);
}

/** The grammar's types: the primitives but char, and String, each also as an array; a result may also be void. */
private static String typeName(Class<?> actor, Method method, Class<?> type) {
	Class<?> element = type.isArray() ? type.getComponentType() : type;
	if(element == char.class || element == void.class || !(element.isPrimitive() || element == String.class)) { throw new IllegalStateException(actor.getName() + "." + method.getName() + " takes or gives " + type.getName() + ", which the request grammar lacks"); }
	return type.getSimpleName();
}

/** A string on the wire: its UTF-8 byte length as an int, then the bytes; a null is written as {@code [ACS-NULL!!]}. */
static void writeString(DataOutputStream dos, String string) throws IOException {
	byte[] bytes = (string == null ? "[ACS-NULL!!]" : string).getBytes(StandardCharsets.UTF_8);
	dos.writeInt(bytes.length);
	dos.write(bytes);
}

/** Takes the bytes as they arrive rather than allocating the length first, so a garbled length ends in an EOFException, not in the whole heap. */
static String readString(DataInputStream dis) throws IOException {
	int length = dis.readInt();
	if(length < 0) { throw new IOException("string length " + length); }
	byte[] bytes = dis.readNBytes(length);
	if(bytes.length < length) { throw new EOFException("a string of " + length + " bytes ends after " + bytes.length); }
	String string = new String(bytes, StandardCharsets.UTF_8);
	return string.equals("[ACS-NULL!!]") ? null : string;
}

}

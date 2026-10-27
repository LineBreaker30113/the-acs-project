package org.halim.acs.database;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
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

/** The journal this actor records to; the journal answers itself. */
ACS_Journal getJournal();

/**
 * Runs one request: {@code parameters} starts with its code, an int, then its inputs as
 * {@link DataOutputStream} writes them, a string by {@link #writeString}; its result goes to
 * {@code returns} the same way. False when a check or the method failed, its error's text then written to
 * {@code returns} as a string; an IOException is the streams' failure, not the request's. This default
 * passes the code to {@link #dispatch}.
 * */
default boolean request(DataInputStream parameters, DataOutputStream returns) throws IOException { return dispatch(this, parameters.readInt(), parameters, returns); }

/**
 * Calls the method {@code actor}'s class marks with {@code code}, reading its inputs and writing its result.
 * <p>
 * Marks: {@link ACS_Requestable} codes, read as {@link #requestsOf} reads them, so a request is a method
 * and no switch repeats the marks.
 * Inputs and result: each by its type in the grammar, an array as its length then its elements.
 * Read: a request with a result and no parameter named {@code comment}, recorded on the actor's journal
 * as a query, its name and inputs, then its answer or {@code refused: } and the reason.
 * Code: already read, so an actor whose request reads more of the stream than its marked inputs takes
 * that code itself and passes the others here.
 * */
static boolean dispatch(ACS_Actor actor, int code, DataInputStream parameters, DataOutputStream returns) throws IOException {
	List<Method> requests = requestsOf(actor.getClass());
	if(code < 0 || code >= requests.size()) {
		writeString(returns, actor.getClass().getName() + " has no request " + code);
		return false;
	}
	Method method = requests.get(code);
	Class<?>[] types = method.getParameterTypes();
	Object[] inputs = new Object[types.length];
	for(int i = 0; i < types.length; i++) { inputs[i] = read(parameters, types[i]); }
	boolean read = method.getReturnType() != void.class && Arrays.stream(method.getParameters()).noneMatch(parameter -> parameter.getName().equals("comment"));
	Object result;
	try { result = invoke(actor, method, inputs); }
	catch(RuntimeException failure) {
		String reason = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getName();
		if(read) { recordQuery(actor, method, inputs, "refused: " + reason); }
		writeString(returns, reason);
		return false;
	}
	if(read) { recordQuery(actor, method, inputs, describe(result)); }
	write(returns, method.getReturnType(), result);
	return true;
}

/** The method's own failure comes out as itself, so a refusal carries its reason, not the reflection's wrapper. */
private static Object invoke(ACS_Actor actor, Method method, Object[] inputs) throws IOException {
	method.trySetAccessible();
	try { return method.invoke(actor, inputs); }
	catch(IllegalAccessException e) { throw new IllegalStateException(method + " cannot be called: " + e.getMessage(), e); }
	catch(InvocationTargetException e) {
		if(e.getCause() instanceof RuntimeException failure) { throw failure; }
		if(e.getCause() instanceof IOException failure) { throw failure; }
		if(e.getCause() instanceof Error failure) { throw failure; }
		throw new IllegalStateException(method + " failed: " + e.getCause(), e.getCause());
	}
}

private static void recordQuery(ACS_Actor actor, Method method, Object[] inputs, String answer) {
	StringBuilder query = new StringBuilder(method.getName()).append('(');
	for(int i = 0; i < inputs.length; i++) { query.append(i > 0 ? ", " : "").append(describe(inputs[i])); }
	actor.getJournal().recordQueryRequest(query.append(')').toString());
	actor.getJournal().recordQueryResponse(answer);
}

/** An array by its type and length, as {@code int[3]}, since a query records the request, not its data. */
private static String describe(Object value) {
	return value != null && value.getClass().isArray() ? value.getClass().getComponentType().getSimpleName() + "[" + Array.getLength(value) + "]" : String.valueOf(value);
}

private static Object read(DataInputStream dis, Class<?> type) throws IOException {
	if(type.isArray()) {
		int length = dis.readInt();
		if(length < 0) { throw new IOException(type.getSimpleName() + " length " + length); }
		Object array = Array.newInstance(type.getComponentType(), length);
		for(int i = 0; i < length; i++) { Array.set(array, i, read(dis, type.getComponentType())); }
		return array;
	}
	if(type == boolean.class) { return dis.readBoolean(); }
	if(type == byte.class) { return dis.readByte(); }
	if(type == short.class) { return dis.readShort(); }
	if(type == int.class) { return dis.readInt(); }
	if(type == long.class) { return dis.readLong(); }
	if(type == float.class) { return dis.readFloat(); }
	if(type == double.class) { return dis.readDouble(); }
	if(type == String.class) { return readString(dis); }
	throw new IllegalStateException(type.getName() + " is not in the request grammar");
}

private static void write(DataOutputStream dos, Class<?> type, Object value) throws IOException {
	if(type == void.class) { return; }
	if(type.isArray()) {
		int length = Array.getLength(value);
		dos.writeInt(length);
		for(int i = 0; i < length; i++) { write(dos, type.getComponentType(), Array.get(value, i)); }
	}
	else if(type == boolean.class) { dos.writeBoolean((Boolean) value); }
	else if(type == byte.class) { dos.writeByte((Byte) value); }
	else if(type == short.class) { dos.writeShort((Short) value); }
	else if(type == int.class) { dos.writeInt((Integer) value); }
	else if(type == long.class) { dos.writeLong((Long) value); }
	else if(type == float.class) { dos.writeFloat((Float) value); }
	else if(type == double.class) { dos.writeDouble((Double) value); }
	else if(type == String.class) { writeString(dos, (String) value); }
	else { throw new IllegalStateException(type.getName() + " is not in the request grammar"); }
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
	StringBuilder lines = new StringBuilder();
	for(Method method : requestsOf(actor)) {
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

/** The marked public methods of {@code actor} by code; a code repeated or skipped is refused. */
static List<Method> requestsOf(Class<?> actor) {
	List<Method> marked = Arrays.stream(actor.getMethods())
		.filter(method -> !method.isBridge() && !method.isSynthetic() && markOf(actor, method) != null)
		.sorted(Comparator.comparingInt(method -> markOf(actor, method).value()))
		.toList();
	for(int code = 0; code < marked.size(); code++) {
		int given = markOf(actor, marked.get(code)).value();
		if(given != code) { throw new IllegalStateException(actor.getName() + "." + marked.get(code).getName() + " has code " + given + " where " + code + " is due; codes run 0 to " + (marked.size() - 1) + " once each"); }
	}
	return marked;
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

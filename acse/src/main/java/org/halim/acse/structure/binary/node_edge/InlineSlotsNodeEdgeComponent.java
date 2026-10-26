package org.halim.acse.structure.binary.node_edge;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import static org.halim.acs.structure.UnaryRestrictions.*;
import static org.halim.acs.structure.BinaryRestrictions.*;


/**
 * The node side as {@code k} slots a node in one flat int array: node {@code n}'s edges sit at
 * {@code n*k} onwards, a byte a node says how many of its slots it uses, and only a node with more than
 * {@code k} edges gets an entry for the rest. Optimized for the case where most nodes have about as many
 * edges as there are slots: a node costs {@code 4k + 1} bytes and no object, adding an edge is a write into
 * its slots, and removing one searches at most {@code k} of them and its overflow.
 * <p>
 * {@code k} is how the graph is stored, not the graph, so {@link #setSlots} changes it at any time,
 * laying every node out again in one pass, outside the journal; pick it near the nodes' usual edge count, so
 * few of them overflow. A node's slots are reserved once the graph first reaches it, and a node's place is
 * its identity times {@code k}, so nodes stay below {@code 2^31 / k}.
 * */
public class InlineSlotsNodeEdgeComponent extends NodeEdgeGraphBase {

/** A node's used count is a byte. */
public static final int MAX_SLOTS = 127;

/** How many edges a node keeps in place. */
int slots;
/** {@code slots} ints a node; only the first {@link #counts} of a node's are its edges. */
final IntArrayList inline = new IntArrayList();
/** How many of each node's slots it uses. */
final ByteArrayList counts = new ByteArrayList();
/** The edges past a node's slots, for the nodes that have more. */
final Int2ObjectOpenHashMap<IntArrayList> overflow = new Int2ObjectOpenHashMap<>();


public InlineSlotsNodeEdgeComponent(ACS_Core core, ACS_Journal journal, int nodeArity, int edgeArity, boolean directed, boolean acyclic, boolean allowsLoops, boolean allowsParallelLinks, int slots) {
	super(core, journal, nodeArity, edgeArity, directed, acyclic, allowsLoops, allowsParallelLinks);
	this.slots = checkSlots(slots);
}

/** Built by name through the core's addStructure: nodeArity, edgeArity, directed, acyclic, allowsLoops, allowsParallelLinks, slots read from the request, in that order; nothing is written back. */
public InlineSlotsNodeEdgeComponent(ACS_Core core, ACS_Journal journal, DataInputStream parameters, DataOutputStream returns) throws IOException {
	this(core, journal, parameters.readInt(), parameters.readInt(), parameters.readBoolean(), parameters.readBoolean(), parameters.readBoolean(), parameters.readBoolean(), parameters.readInt());
}

InlineSlotsNodeEdgeComponent(ACS_Core core, ACS_Journal journal, Rules rules, int slots) {
	super(core, journal, rules);
	this.slots = checkSlots(slots);
}

static int checkSlots(int slots) {
	if(slots < 1 || slots > MAX_SLOTS) { throw new IllegalArgumentException("slots " + slots + ", from 1 to " + MAX_SLOTS); }
	return slots;
}

public int getSlots() { return slots; }

/** Lays every node's edges out again over {@code slots} slots: one pass over the nodes, not journaled. */
public void setSlots(int slots) {
	checkSlots(slots);
	ArrayList<IntArrayList> held = new ArrayList<>(counts.size());
	for(int node = 0; node < counts.size(); node++) { held.add(edgeListOf(node)); }
	this.slots = slots;
	inline.clear();
	counts.clear();
	overflow.clear();
	for(int node = 0; node < held.size(); node++) {
		IntArrayList edges = held.get(node);
		for(int i = 0; i < edges.size(); i++) { add(node, edges.getInt(i)); }
	}
}


@Override
void index(int edge, int first, int second) {
	add(first, edge);
	if(second != first) { add(second, edge); }
}

@Override
void unindex(int edge, int first, int second) {
	remove(first, edge);
	if(second != first) { remove(second, edge); }
}

@Override
IntArrayList edgeListOf(int node) {
	IntArrayList held = new IntArrayList();
	if(node < 0 || node >= counts.size()) { return held; }
	held.addElements(0, inline.elements(), node * slots, counts.getByte(node));
	IntArrayList extra = overflow.get(node);
	if(extra != null) { held.addAll(extra); }
	return held;
}

/** Into the node's next free slot, or its overflow once the slots are full. */
void add(int node, int edge) {
	while(counts.size() <= node) {
		counts.add((byte) 0);
		inline.size(Math.multiplyExact(counts.size(), slots));
	}
	int used = counts.getByte(node);
	if(used < slots) {
		inline.set(node * slots + used, edge);
		counts.set(node, (byte) (used + 1));
		return;
	}
	overflow.computeIfAbsent(node, n -> new IntArrayList()).add(edge);
}

/** A freed slot takes an edge from the overflow, or else the node's last slot, so the used slots stay a run. */
void remove(int node, int edge) {
	int start = node * slots, used = counts.getByte(node);
	IntArrayList extra = overflow.get(node);
	for(int i = 0; i < used; i++) {
		if(inline.getInt(start + i) != edge) { continue; }
		if(extra != null) {
			inline.set(start + i, extra.popInt());
			if(extra.isEmpty()) { overflow.remove(node); }
		} else {
			inline.set(start + i, inline.getInt(start + used - 1));
			counts.set(node, (byte) (used - 1));
		}
		return;
	}
	extra.removeInt(extra.lastIndexOf(edge));
	if(extra.isEmpty()) { overflow.remove(node); }
}


/** The slot count. */
@Override
void writeSettings(DataOutputStream dos) throws IOException { dos.writeInt(slots); }

/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal. */
public static InlineSlotsNodeEdgeComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	Rules rules = Rules.read(dis);
	InlineSlotsNodeEdgeComponent graph = new InlineSlotsNodeEdgeComponent(core, journal, rules, dis.readInt());
	graph.readEdges(dis);
	return graph;
}


}

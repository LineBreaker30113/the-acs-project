package org.halim.acse.structure.binary.node_edge;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;


/**
 * The node side as a list of edges a node, each its own int list, made the first time the node has an
 * edge. No node is limited in how many edges it holds and none overflows, so a graph whose edge counts
 * vary widely costs no rearranging; what it pays is the list itself - on a 64-bit JVM about 80 bytes a node
 * once it has an edge, a 24-byte object and a first array of ten ints - which on a graph of nodes with a few
 * edges each is more than the edges. Adding an edge appends to two lists, removing one searches both from
 * the end.
 * Not multi thread safe.
 * */
public class ArrayListsNodeEdgeComponent extends NodeEdgeGraphBase {

/** Per node, its edges in the order made; null until it has had one. */
final ArrayList<IntArrayList> incident = new ArrayList<>();


public ArrayListsNodeEdgeComponent(ACS_Core core, ACS_Journal journal, int nodeArity, int edgeArity, boolean directed, boolean acyclic, boolean allowsLoops, boolean allowsParallelEdges) {
	super(core, journal, nodeArity, edgeArity, directed, acyclic, allowsLoops, allowsParallelEdges);
}

/** Built by name through the core's addStructure: nodeArity, edgeArity, directed, acyclic, allowsLoops, allowsParallelEdges read from the request, in that order; nothing is written back. */
public ArrayListsNodeEdgeComponent(ACS_Core core, ACS_Journal journal, DataInputStream parameters, DataOutputStream returns) throws IOException {
	this(core, journal, parameters.readInt(), parameters.readInt(), parameters.readBoolean(), parameters.readBoolean(), parameters.readBoolean(), parameters.readBoolean());
}

ArrayListsNodeEdgeComponent(ACS_Core core, ACS_Journal journal, Rules rules) {
	super(core, journal, rules);
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
IntArrayList edgesOf(int node) {
	IntArrayList held = node >= 0 && node < incident.size() ? incident.get(node) : null;
	return held == null ? new IntArrayList() : new IntArrayList(held);
}

void add(int node, int edge) {
	while(incident.size() <= node) { incident.add(null); }
	if(incident.get(node) == null) { incident.set(node, new IntArrayList()); }
	incident.get(node).add(edge);
}

void remove(int node, int edge) {
	IntArrayList held = incident.get(node);
	held.removeInt(held.lastIndexOf(edge));
}


/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal. */
public static ArrayListsNodeEdgeComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	ArrayListsNodeEdgeComponent graph = new ArrayListsNodeEdgeComponent(core, journal, Rules.read(dis));
	graph.readEdges(dis);
	return graph;
}


}

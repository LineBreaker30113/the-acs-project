package org.halim.acs.structure;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.database.ACS_Requestable;

/** Formal binary structure: connector between primary arity (0) and complement arity (1). */
public interface ACS_BinaryStructure extends ACS_StructureBase {

	byte STEP_RELATE_TO = 1;
	byte STEP_UNRELATE_TO = -1;

	@Override
	default int getArityCount() { return 2; }

	@Override
	default boolean isRelatedArity(int identity) { return identity == getArityIdentity(0) || identity == getArityIdentity(1); }

	default int getPrimaryArity() { return getArityIdentity(0); }
	default int getComplementArity() { return getArityIdentity(1); }

	@ACS_Requestable(5)
	boolean relateToBinary(int primary, int complement, String comment);

	@ACS_Requestable(6)
	boolean unrelateFromBinary(int primary, int complement, String comment);

	@ACS_Requestable(7)
	boolean isRelatedToBinary(int primary, int complement);

	@ACS_Requestable(8)
	boolean setComplement(int primary, int complement, String comment);

	@ACS_Requestable(9)
	boolean setPrimary(int complement, int primary, String comment);

	@ACS_Requestable(10)
	boolean setComplementsOf(int primary, int[] complements, String comment);

	@ACS_Requestable(11)
	boolean setPrimariesOf(int complement, int[] primaries, String comment);

	@ACS_Requestable(12)
	default boolean moveBinaryRelation(int source, int target, int arityIndex, String comment) {
		boolean changed = mergeBinary(source, target, arityIndex, comment);
		return (arityIndex == 0 ? setComplementsOf(source, new int[0], comment) : setPrimariesOf(source, new int[0], comment)) || changed;
	}

	@ACS_Requestable(13)
	default boolean mergeBinary(int source, int target, int arityIndex, String comment) {
		if(arityIndex == 0) {
			IntOpenHashSet union = new IntOpenHashSet(getComplementsOf(target));
			union.addAll(IntArrayList.wrap(getComplementsOf(source)));
			return setComplementsOf(target, union.toIntArray(), comment);
		}
		if(arityIndex != 1) { throw new IllegalArgumentException("a binary structure has arity indices 0 and 1, not " + arityIndex); }
		IntOpenHashSet union = new IntOpenHashSet(getPrimariesOf(target));
		union.addAll(IntArrayList.wrap(getPrimariesOf(source)));
		return setPrimariesOf(target, union.toIntArray(), comment);
	}

	@ACS_Requestable(14)
	int complementCount(int primary);

	@ACS_Requestable(15)
	int primaryCount(int complement);

	void copyComplementsInto(int primary, int[] dest, int offset);

	void copyPrimariesInto(int complement, int[] dest, int offset);

	@ACS_Requestable(16)
	default int[] getComplementsOf(int primary) {
		int[] complements = new int[complementCount(primary)];
		copyComplementsInto(primary, complements, 0);
		return complements;
	}

	@ACS_Requestable(17)
	default int[] getPrimariesOf(int complement) {
		int[] primaries = new int[primaryCount(complement)];
		copyPrimariesInto(complement, primaries, 0);
		return primaries;
	}

}

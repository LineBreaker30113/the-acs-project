package org.halim.acse.arity.catalog.memory;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.ACS_ArityComponentBase;
import org.halim.acs.arity.catalog.IntegerValueCatalog.IntSizedIntegerValueCatalog;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class IntegerValueCatalogListerShort implements IntSizedIntegerValueCatalog {

/** One short a slot, packed - the list is the storage. */
@Override
public double getEstimatedBytesPerIdentity() { return 2; }
public ShortArrayList data = new ShortArrayList();

@Override
public int getIntData(long identity) {
	return data.getShort((int) identity);
}

@Override
public void setLongFor(long identity, long value) {
	data.set((int) identity, ACS_ArityComponentBase.exactShort(value));
}


/** The flat list is the storage, so reaching an identity is just making the list that long. */
@Override
public boolean ensureCapacity(long identity) {
	int want = Math.toIntExact(identity + 1);
	if(data.size() >= want) { return false; }
	data.size(want);
	return true;
}

@Override
public void truncate(long capacity) {
	data.size(capacity < 0 ? 0 : Math.toIntExact(capacity + 1));
}

/** The length, then every value. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	dos.writeInt(data.size());
	for(int i = 0; i < data.size(); i++) { dos.writeShort(data.getShort(i)); }
	return true;
}

/** Rebuilds what {@link #saveTo} wrote. */
public static IntegerValueCatalogListerShort loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	IntegerValueCatalogListerShort catalog = new IntegerValueCatalogListerShort();
	int size = dis.readInt();
	catalog.data = new ShortArrayList(size);
	for(int i = 0; i < size; i++) { catalog.data.add(dis.readShort()); }
	return catalog;
}


/**
 * The flat list is the storage, so the whole range is one run and there is no chunk split to pay
 * for. This is the case the per-element default is worst against - the loop it replaces was all
 * dispatch and no work.
 * */
@Override
public void copyIntsInto(long from, long to, int[] dest, int offset) {
	short[] elements = data.elements();
	int at = Math.toIntExact(from);
	int end = Math.toIntExact(to);
	if(end > data.size()) { throw new IndexOutOfBoundsException("identity " + (end - 1) + " is past this catalog"); }
	while(at < end) { dest[offset++] = elements[at++]; }
}

}

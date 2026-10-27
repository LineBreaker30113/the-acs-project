package org.halim.acse.arity.catalog.memory;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.ACS_ArityComponentBase;
import org.halim.acs.arity.catalog.IntegerValueCatalog.IntSizedIntegerValueCatalog;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class IntegerValueCatalogListerInt implements IntSizedIntegerValueCatalog {

/** One int a slot, packed - the list is the storage. */
@Override
public double getEstimatedBytesPerIdentity() { return 4; }
public IntArrayList data = new IntArrayList();

@Override
public int getIntData(long identity) {
	return data.getInt((int) identity);
}

@Override
public void setLongFor(long identity, long value) {
	data.set((int) identity, ACS_ArityComponentBase.exactInt(value));
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
	for(int i = 0; i < data.size(); i++) { dos.writeInt(data.getInt(i)); }
	return true;
}

/** Rebuilds what {@link #saveTo} wrote. */
public static IntegerValueCatalogListerInt loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	IntegerValueCatalogListerInt catalog = new IntegerValueCatalogListerInt();
	int size = dis.readInt();
	catalog.data = new IntArrayList(size);
	for(int i = 0; i < size; i++) { catalog.data.add(dis.readInt()); }
	return catalog;
}


/**
 * The flat list is the storage, so the whole range is one run and there is no chunk split to pay
 * for. This is the case the per-element default is worst against - the loop it replaces was all
 * dispatch and no work.
 * */
@Override
public void copyIntsInto(long from, long to, int[] dest, int offset) {
	data.getElements(Math.toIntExact(from), dest, offset, Math.toIntExact(to - from));
}

}

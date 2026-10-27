package org.halim.acse.arity.catalog.memory;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.catalog.FloatingValueCatalog;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class FloatingValueCatalogListerFloat implements FloatingValueCatalog {

/** One float a slot, packed - half the double form, which is the point. */
@Override
public double getEstimatedBytesPerIdentity() { return 4; }
public FloatArrayList data = new FloatArrayList();

@Override
public double getData(long identity) {
	return data.getFloat((int) identity);
}

/** Narrows by rounding, as this variant's writes do. */
@Override
public void setDoubleFor(long identity, double value) {
	data.set((int) identity, (float) value);
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
	for(int i = 0; i < data.size(); i++) { dos.writeInt(Float.floatToRawIntBits(data.getFloat(i))); }
	return true;
}

/** Rebuilds what {@link #saveTo} wrote. */
public static FloatingValueCatalogListerFloat loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	FloatingValueCatalogListerFloat catalog = new FloatingValueCatalogListerFloat();
	int size = dis.readInt();
	catalog.data = new FloatArrayList(size);
	for(int i = 0; i < size; i++) { catalog.data.add(Float.intBitsToFloat(dis.readInt())); }
	return catalog;
}


/**
 * The flat list is the storage, so the whole range is one run and there is no chunk split to pay
 * for. This is the case the per-element default is worst against - the loop it replaces was all
 * dispatch and no work.
 * */
@Override
public void copyDoublesInto(long from, long to, double[] dest, int offset) {
	float[] elements = data.elements();
	int at = Math.toIntExact(from);
	int end = Math.toIntExact(to);
	if(end > data.size()) { throw new IndexOutOfBoundsException("identity " + (end - 1) + " is past this catalog"); }
	while(at < end) { dest[offset++] = elements[at++]; }
}

}

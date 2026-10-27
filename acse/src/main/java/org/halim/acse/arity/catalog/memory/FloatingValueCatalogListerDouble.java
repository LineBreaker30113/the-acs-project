package org.halim.acse.arity.catalog.memory;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.catalog.FloatingValueCatalog;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class FloatingValueCatalogListerDouble implements FloatingValueCatalog {

/** One double a slot, packed - the list is the storage. */
@Override
public double getEstimatedBytesPerIdentity() { return 8; }
public DoubleArrayList data = new DoubleArrayList();

@Override
public double getData(long identity) {
	return data.getDouble((int) identity);
}

@Override
public void setDoubleFor(long identity, double value) {
	data.set((int) identity, value);
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
	for(int i = 0; i < data.size(); i++) { dos.writeLong(Double.doubleToRawLongBits(data.getDouble(i))); }
	return true;
}

/** Rebuilds what {@link #saveTo} wrote. */
public static FloatingValueCatalogListerDouble loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	FloatingValueCatalogListerDouble catalog = new FloatingValueCatalogListerDouble();
	int size = dis.readInt();
	catalog.data = new DoubleArrayList(size);
	for(int i = 0; i < size; i++) { catalog.data.add(Double.longBitsToDouble(dis.readLong())); }
	return catalog;
}


/**
 * The flat list is the storage, so the whole range is one run and there is no chunk split to pay
 * for. This is the case the per-element default is worst against - the loop it replaces was all
 * dispatch and no work.
 * */
@Override
public void copyDoublesInto(long from, long to, double[] dest, int offset) {
	data.getElements(Math.toIntExact(from), dest, offset, Math.toIntExact(to - from));
}

}

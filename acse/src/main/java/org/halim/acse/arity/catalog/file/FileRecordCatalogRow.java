package org.halim.acse.arity.catalog.file;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.ACS_ArityComponentBase;
import org.halim.acs.arity.catalog.ACS_ArityCatalogRowBase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.Objects;


/**
 * One row of a {@link FileRecordCatalog} as a catalog row of its own: the adapter that lets the record
 * file sit in an arity's slots, one field a slot. It holds only the file and the field, so its lifecycle
 * is the file's, shared with the record's other rows.
 * <p>
 * One class serves every field type, so the views are the families' rules written once here: an integer
 * field answers as {@link org.halim.acs.arity.catalog.IntegerValueCatalog} does - a byte,
 * short or int field as the int-sized form, whose int and double views cannot refuse - and a floating
 * field as {@link org.halim.acs.arity.catalog.FloatingValueCatalog} does, a float field
 * rounding what it is given. Conversion is exact or it throws, through the same helpers.
 * <p>
 * A store copies the record file into the store's folder, once however many of its rows the arity holds,
 * and writes the layout, the field and the reach. A load puts the copy back at the file's place when the
 * first of the record's rows loads, and the rest of them share what it opened.
 * */
public class FileRecordCatalogRow implements ACS_ArityCatalogRowBase {

public final MappedRecordFile storage;
public final int field;
final byte type;
final int offset;

FileRecordCatalogRow(MappedRecordFile storage, int field) {
	Objects.checkIndex(field, storage.fieldCount());
	this.storage = storage;
	this.field = field;
	this.type = storage.typeOf(field);
	this.offset = storage.offsetOf(field);
}

/** The record catalog this row is a field of. */
public FileRecordCatalog catalog() { return new FileRecordCatalog(storage); }


/** The field's own width; the record's other fields are theirs. */
@Override
public double getEstimatedBytesPerIdentity() { return Math.abs(type); }

@Override
public int getPreferredType() {
	if(type < 0) { return PREFERRED_FLOATING; }
	return type == MappedRecordFile.LONG ? PREFERRED_LONGI : PREFERRED_INTEGER;
}

@Override
public boolean ensureCapacity(long identity) { return storage.ensureCapacity(identity); }

@Override
public void truncate(long capacity) { storage.truncate(capacity); }


/** The layout, with the file copied into the store's folder, then the field and the reach. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	storage.writeLayout(dos);
	dos.writeInt(field);
	dos.writeLong(storage.lastIndex);
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, over the file put back from the loaded folder's copy. */
public static FileRecordCatalogRow loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	MappedRecordFile storage = MappedRecordFile.openLayout(dis);
	int field = dis.readInt();
	if(field < 0 || field >= storage.fieldCount()) { throw new IOException("field " + field + " of a record of " + storage.fieldCount()); }
	storage.reach(dis.readLong());
	return new FileRecordCatalogRow(storage, field);
}


/** The field's place in the identity's record. */
int at(long identity) { return storage.positionOf(identity) + offset; }

/** The stored integer, widened; for an integer field. */
long integer(long identity) {
	MappedByteBuffer mapping = storage.chunkOf(identity);
	int at = at(identity);
	return switch(type) {
		case MappedRecordFile.BYTE -> mapping.get(at);
		case MappedRecordFile.SHORT -> mapping.getShort(at);
		case MappedRecordFile.INT -> mapping.getInt(at);
		default -> mapping.getLong(at);
	};
}

/** The stored floating value, widened; for a floating field. */
double floating(long identity) {
	MappedByteBuffer mapping = storage.chunkOf(identity);
	return type == MappedRecordFile.FLOAT ? mapping.getFloat(at(identity)) : mapping.getDouble(at(identity));
}


@Override
public long getLongFor(long identity) {
	return type < 0 ? ACS_ArityComponentBase.exactLong(floating(identity)) : integer(identity);
}

@Override
public int getIntFor(long identity) {
	return type < 0 ? ACS_ArityComponentBase.exactInt(floating(identity)) : ACS_ArityComponentBase.exactInt(integer(identity));
}

@Override
public double getDoubleFor(long identity) {
	return type < 0 ? floating(identity) : ACS_ArityComponentBase.exactDouble(integer(identity));
}

/** Exact at the field's width; on a floating field, only a long with an exact double. Growth is the arity's. */
@Override
public void setLongFor(long identity, long value) {
	if(type < 0) {
		setDoubleFor(identity, ACS_ArityComponentBase.exactDouble(value));
		return;
	}
	MappedByteBuffer mapping = storage.chunkOf(identity);
	int at = at(identity);
	switch(type) {
		case MappedRecordFile.BYTE -> mapping.put(at, ACS_ArityComponentBase.exactByte(value));
		case MappedRecordFile.SHORT -> mapping.putShort(at, ACS_ArityComponentBase.exactShort(value));
		case MappedRecordFile.INT -> mapping.putInt(at, ACS_ArityComponentBase.exactInt(value));
		default -> mapping.putLong(at, value);
	}
}

/** A float field rounds; an integer field takes only an exact long. Growth is the arity's. */
@Override
public void setDoubleFor(long identity, double value) {
	if(type > 0) {
		setLongFor(identity, ACS_ArityComponentBase.exactLong(value));
		return;
	}
	MappedByteBuffer mapping = storage.chunkOf(identity);
	if(type == MappedRecordFile.FLOAT) { mapping.putFloat(at(identity), (float) value); }
	else { mapping.putDouble(at(identity), value); }
}


}

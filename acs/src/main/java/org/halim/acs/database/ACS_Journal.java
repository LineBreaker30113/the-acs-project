package org.halim.acs.database;

import java.io.DataOutputStream;
import java.io.IOException;

public interface ACS_Journal extends ACS_Actor {

/** Begins recording a mutation on the structure. The paramsMemory is for communicating parameters using a single allocation, and itself will not be recorded, defined as an object so client choose to use an int[] or IntArrayList. */
int beginRecordingActivity(ACS_Actor actor, String methodName, Object paramsMemory, String comment);
/** Completes the last recording of mutation on the structure. */
int completeRecordingActivity();
/** Undoes what the innermost open request recorded, nested requests included, and closes it. */
int abortRecordingActivity();
/** Whether a request is open, so what must run between requests - storing, loading - can refuse. */
boolean isRecordingActivity();
/** paramCount states how many first elements of the paramsMemory must be copied. */
void recordActivityStep(byte stepPair, int paramCount);
/** Returns the index to pass among a step's params. */
int recordBoolean(boolean value);
/** Returns the index to pass among a step's params. */
int recordObject(Object value);
boolean getBoolean(int index);
Object getObject(int index);
/** Begins a request that does not mutate, it is not bound to be a single method and provider gives the full description */
int recordQueryRequest(String description);
/** Completes a request that does not mutate, it is not bound to be a single method and provider gives the full description */
int recordQueryResponse(String description);

@ACS_Requestable(0)
void undoLastActivity();
@ACS_Requestable(1)
void redoLastActivity();
@ACS_Requestable(2)
void undoUntilActivityIndex(int activityIndex);
@ACS_Requestable(3)
void redoUntilActivityIndex(int activityIndex);
/** Undoes or redoes as needed. */
@ACS_Requestable(4)
void inspectAt(int activityIndex);
/** Returns to the last point prior to made inspection. */
@ACS_Requestable(5)
void endInspection();
/** Whether an inspection holds a return point; while it does, beginRecordingActivity refuses, since recording would drop the way back. */
@ACS_Requestable(6)
boolean isInspecting();
/** The last request of the last applied transaction, -1 when none is applied. */
@ACS_Requestable(7)
int getCurrentActivityIndex();
/** How many requests the transactions after the current point hold, which redo can apply again. */
@ACS_Requestable(8)
int getPendingActivityCount();

void storeAll(DataOutputStream dos) throws IOException;

}

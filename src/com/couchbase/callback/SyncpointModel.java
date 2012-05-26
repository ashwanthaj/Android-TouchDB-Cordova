package com.couchbase.callback;

import java.util.Map;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDRevision;

public class SyncpointModel extends TDRevision {

	public SyncpointModel(TDBody body) {
		super(body);
		// TODO Auto-generated constructor stub
	}

	public SyncpointModel(Map<String, Object> properties) {
		super(properties);
		// TODO Auto-generated constructor stub
	}

	public SyncpointModel(String docId, String revId, boolean deleted) {
		super(docId, revId, deleted);
		// TODO Auto-generated constructor stub
	}

}

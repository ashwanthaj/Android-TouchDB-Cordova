package com.couchbase.callback;

import java.util.HashMap;
import java.util.Map;

import android.util.Log;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDMisc;
import com.couchbase.touchdb.TDStatus;

public class SyncpointSubscription extends SyncpointModel {
	
	private String ownerId;
    public static final String TAG = "SyncpointSubscription";

	public SyncpointSubscription(TDBody body) {
		super(body);
		// TODO Auto-generated constructor stub
	}

	public SyncpointSubscription(Map<String, Object> properties) {
		super(properties);
		// TODO Auto-generated constructor stub
	}

	public SyncpointSubscription(String docId, String revId, boolean deleted) {
		super(docId, revId, deleted);
		// TODO Auto-generated constructor stub
	}

	public SyncpointInstallation makeInstallationWithLocalDatabase(TDDatabase localDB) {
		String name;
		if (localDB != null) {
			// TODO: is this the same as IOS relativePath?
			name = localDB.getName();
		} else {
			//TODO - compare with IOS randomString(void)
			name = "channel-" + TDMisc.TDCreateUUID();
			localDB = server.getDatabaseNamed(name);
			localDB.open();
		}
		
        Log.v(TAG, String.format("Installing SyncpointSubscription to %s", name));
        // TODO ensureCreated(localDB)
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("type", "installation");
        documentProperties.put("local_db_name", name);
        TDBody body = new TDBody(documentProperties);
        SyncpointInstallation inst = new SyncpointInstallation(body);
		inst.setState("created");
		SyncpointSession session = SyncpointSession.sessionInDatabase(this.database, this.context);
		inst.setSession(session);
		inst.setOwnerId(this.ownerId);
		inst.setSubscription(this);
		TDStatus status = new TDStatus();
		// TODO: confirm putRevision is equivalent to IOS: return [[inst save] wait: outError] ? inst : nil;
		localDB.putRevision(inst, null, false, status);
		return inst;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	

}

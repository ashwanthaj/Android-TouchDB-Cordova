package com.couchbase.callback;

import java.util.Map;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;

public class SyncpointSession extends SyncpointModel {

	private String owner_id, oauth_creds, pairing_creds, control_database, control_db_synced;
	public SyncpointSession(TDBody body) {
		super(body);
		// TODO Auto-generated constructor stub
	}

	public SyncpointSession(Map<String, Object> properties) {
		super(properties);
		// TODO Auto-generated constructor stub
	}

	public SyncpointSession(String docId, String revId, boolean deleted) {
		super(docId, revId, deleted);
		// TODO Auto-generated constructor stub
	}

	public static SyncpointSession sessionInDatabase(TDDatabase database, SharedPreferences preferences) {
		SyncpointSession session = null;
		//SharedPreferences userPreference = getSharedPreferences("preference name", Activity.MODE_PRIVATE);
	
		String sessID = preferences.getString("Syncpoint_SessionDocID", null);
		if (sessID == null) {
			return null;
		}
		TDRevision doc = SyncpointClient.getDocumentWithId(database, sessID);
		if (doc == null) {
			return null;
		}
		if (doc.getProperties() == null) {
			// Oops -- the session ID in user-defaults is out of date, so clear it
			SharedPreferences.Editor editor = preferences.edit();
			editor.remove("Syncpoint_SessionDocID");
			editor.commit();
			return null;
		}
		session = (SyncpointSession) doc;
		return session;
	}
}

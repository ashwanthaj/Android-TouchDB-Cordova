package com.couchbase.callback;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Log;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDMisc;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;

public class SyncpointSession extends SyncpointModel {

	private String ownerId;
	private HashMap<String,String> oauthCreds;
	private HashMap<String,String> pairingCreds;
	private boolean paired;
	private boolean readyToPair;
	
	private String controlDatabase;	/** The name of the remote database that the local control database syncs with. */
	private boolean controlDbSynced;
	private TDStatus error;
	
    public static final String TAG = "SyncpointSession";
    
    public SyncpointSession(TDRevision doc) {
    	super(doc.getProperties());
    }
	
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

	/**
	 * 
	 * @param database
	 * @param context - SyncpointClient should have already have this, when it was init'd.
	 * @return
	 */
	public static SyncpointSession sessionInDatabase(TDDatabase database, Context context) {
		SyncpointSession session = null;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String sessID = prefs.getString("Syncpoint_SessionDocID", null);
		if (sessID == null) {
			return null;
		}
		TDRevision doc = SyncpointModel.getDocumentWithId(database, sessID);
		if (doc == null) {
			return null;
		}
		if (doc.getProperties() == null) {
			// Oops -- the session ID in user-defaults is out of date, so clear it
			SharedPreferences.Editor editor = prefs.edit();
			editor.remove("Syncpoint_SessionDocID");
			editor.commit();
			return null;
		}
		session = new SyncpointSession(doc);
		return session;
	}
	
	/** Creates a new session document in the local control database.
    @param database  The local server's control database.
    @param appId  The ID of this app on the Syncpoint cluster
    @return  The new SyncpointSession instance. */
	public static SyncpointSession makeSessionInDatabase(TDDatabase database, String appId, URL remote, Context context) {
	    // TODO: Register the other model classes with the database's model factory:
    	Log.d(TAG, String.format("Creating session for %s in %s:",appId, database));	    	
    	TDRevision doc = initWithNewDocumentInDatabase(database, null);
    	SyncpointSession session = new SyncpointSession(doc);
    	session.getProperties().put("app_id", appId);
    	session.getProperties().put("syncpoint_url", remote);
    	session.setState("new");
    	HashMap<String,String> oauthCreds = new HashMap<String,String>();
    	oauthCreds.put("consumer_key", TDMisc.TDCreateUUID());
    	oauthCreds.put("consumer_secret", TDMisc.TDCreateUUID());
    	oauthCreds.put("token_secret", TDMisc.TDCreateUUID());
    	oauthCreds.put("token", TDMisc.TDCreateUUID());
    	session.setOauthCreds(oauthCreds);
    	session.getProperties().putAll(oauthCreds);
    	HashMap<String,String> pairingCreds = new HashMap<String,String>();
    	pairingCreds.put("username", "pairing-" + TDMisc.TDCreateUUID());
    	pairingCreds.put("password", TDMisc.TDCreateUUID());
    	session.setPairingCreds(pairingCreds);
    	session.getProperties().putAll(pairingCreds);
    	TDStatus status = new TDStatus();
    	TDRevision rev = database.putRevision(session, doc.getRevId(), false, status);
    	// TODO: convert TDRevision to SyncpointSession instead.
    	session.setRevId(rev.getRevId());
		Log.e(TAG, String.format("SyncpointSession: session _id: %s, _rev: %s ", session.getDocId(), session.getRevId()));
    	if (status.getCode() >= 300) {
    		Log.e(TAG, String.format("SyncpointSession: Couldn't save new session at database: %s ", database.getName()));
    		return null;
    	}
    	String sessionID = session.getDocId();
    	Log.d(TAG, String.format("...session ID = %s:",sessionID)); 
    	Log.d(TAG, String.format("...session _rev = %s; doc _rev = %s.",session.getRevId(), doc.getRevId())); 
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		//String sessID = prefs.getString("Syncpoint_SessionDocID", null);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString("Syncpoint_SessionDocID", sessionID);
		editor.commit();
		return session;
	}
	
	public HashMap<String,Object> pairingUserProperties() {
		HashMap<String,Object> userProps = new HashMap<String,Object>();
		String username = this.pairingCreds.get("username");
		String password = this.pairingCreds.get("password");
		userProps.put("_id", "org.couchdb.user:" + username);
        userProps.put("name", username);
        userProps.put("type", "user");
        userProps.put("sp_oauth",this.oauthCreds);
        userProps.put("pairing_state", "new");
        userProps.put("pairing_type", this.getValueOfProperty("pairing_type"));
        userProps.put("pairing_token", this.getValueOfProperty("pairing_token"));
        userProps.put("pairing_app_id", this.getValueOfProperty("app_id"));
        userProps.put("roles", new HashMap());
        userProps.put("password", password);
		return userProps;
	}
	
	public TDStatus getError() {
		if (!state.equals("error")) {
			return null;
		}
		return error;
	}

	public void setError(TDStatus error) {
		this.error = error;
	}
	
	public void clearState(TDStatus status) {
		state = "new";
		this.setError(null);
		this.getProperties().remove("error");
		
		database.putRevision(this, null, false, status);
	}

	public boolean isPaired() {
		if (this.getState().equals("paired")) {
			paired = true;
			return true;
		} else {
			paired = false;
			return false;
		}
	}

	public void setPaired(boolean paired) {
		this.paired = paired;
	}
	
	// TODO: Confirm this does the same thing as IOS version: 
	// return !![self getValueOfProperty:@"pairing_token"];
	public boolean isReadyToPair() {
		if (this.getValueOfProperty("pairing_token") != null) {
			return true;
		} else {
			return false;
		}
	}
	
	public void setReadyToPair(boolean readyToPair) {
		this.readyToPair = readyToPair;
	}
	
	public void didFirstSyncOfControlDB() {
		if (!this.controlDbSynced) {
			this.controlDbSynced = true;
			Log.v(TAG, "didFirstSyncOfControlDB done. Need to save (not implemented yet.) ");
			// TODO: [[self save] wait: nil];
		}
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}
	
	public boolean isControlDbSynced() {
		return controlDbSynced;
	}

	public void setControlDbSynced(boolean controlDbSynced) {
		this.controlDbSynced = controlDbSynced;
	}

	public String getControlDatabase() {
		return controlDatabase;
	}

	public void setControlDatabase(String controlDatabase) {
		this.controlDatabase = controlDatabase;
	}

	public HashMap<String, String> getOauthCreds() {
		return oauthCreds;
	}

	public void setOauthCreds(HashMap<String, String> oauthCreds) {
		this.oauthCreds = oauthCreds;
	}

	public HashMap<String, String> getPairingCreds() {
		return pairingCreds;
	}

	public void setPairingCreds(HashMap<String, String> pairingCreds) {
		this.pairingCreds = pairingCreds;
	}





}

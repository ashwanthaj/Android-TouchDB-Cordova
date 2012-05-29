package com.couchbase.callback;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import android.util.Log;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.replicator.TDReplicator;

/**
 * An installation represents a subscription to a channel on a specific device. 
 */
public class SyncpointInstallation extends SyncpointModel {

	private String ownerId;
	private boolean local;		/** Is this installation specific to this device? */
	private TDDatabase localDatabase;	/** The local database to sync. */
	private SyncpointSubscription subscription;
	private SyncpointChannel channel;	/** The channel this is associated with. */
	private SyncpointSession session;	/** The session this is associated with. */

    public static final String TAG = "SyncpointInstallation";

	public SyncpointInstallation(TDBody body) {
		super(body);
		// TODO Auto-generated constructor stub
	}

	public SyncpointInstallation(Map<String, Object> properties) {
		super(properties);
		// TODO Auto-generated constructor stub
	}

	public SyncpointInstallation(String docId, String revId, boolean deleted) {
		super(docId, revId, deleted);
		// TODO Auto-generated constructor stub
	}

	public SyncpointSession getSession() {
		return session;
	}

	public void setSession(SyncpointSession session) {
		this.session = session;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	public SyncpointChannel getChannel() {
		return channel;
	}

	public void setChannel(SyncpointChannel channel) {
		this.channel = channel;
	}

	public SyncpointSubscription getSubscription() {
		return subscription;
	}

	public void setSubscription(SyncpointSubscription subscription) {
		this.subscription = subscription;
	}

	// Starts bidirectional sync of an application database with its server counterpart.
	public void sync() {
		TDDatabase localChannelDb = getLocalDatabase();
		String syncpointUrl = (String) this.session.getValueOfProperty("syncpoint_url");
		URL cloudChannelURL = null;
		try {
			cloudChannelURL = new URL(syncpointUrl + this.channel.getCloudDatabase());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Log.v(TAG, String.format("Sync local db %s with remote %s", localChannelDb, cloudChannelURL));
		// TODO: compare with IOS replicateWithURL - need to use _replicator db.
		// TODO: Check if replication works after restarting the app.
		TDReplicator replPull = localChannelDb.getReplicator(cloudChannelURL, false, true);
		replPull.start();
		TDReplicator replPush = localChannelDb.getReplicator(cloudChannelURL, true, true);
		replPush.start();
	}

	public TDDatabase getLocalDatabase() {
		TDDatabase localDatabase = null;
		if (!this.isLocal()) {
			return null;
		}
		String name = (String) this.getValueOfProperty("local_db_name");
		// TODO: confirm this is the same as IOS return name ? [self.database.server databaseNamed: name] : nil;
		if (name != null) {
			localDatabase = this.server.getDatabaseNamed(name);
		}
		return localDatabase;
	}

	public void setLocalDatabase(TDDatabase localDatabase) {
		this.localDatabase = localDatabase;
	}

	public boolean isLocal() {
        session = SyncpointSession.sessionInDatabase(this.database, context);
        String sessionDocID = session.getDocId();
        String sessionId = (String) this.getValueOfProperty("session_id");
        if (sessionDocID.equals(sessionId)) {
        	return true;
        } else {
        	return false;
        }
	}

	public void setLocal(boolean local) {
		this.local = local;
	}

}

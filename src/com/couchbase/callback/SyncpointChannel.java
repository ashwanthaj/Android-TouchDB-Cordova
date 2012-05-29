package com.couchbase.callback;

import java.util.Map;

import com.couchbase.touchdb.TDBody;

public class SyncpointChannel extends SyncpointModel {

	private boolean ready;	/** Is the channel set up on the server and ready for use? */
	private boolean unpaired;	
	private SyncpointSubscription subscription;	/** The local user's subscription to the channel, if any. */
	private SyncpointInstallation installation;	/** The local device's installation of the channel, if any. */
	private String cloudDatabase; 	/** The name of the server-side database to sync subscriptions with. */

	
	public SyncpointChannel(TDBody body) {
		super(body);
		// TODO Auto-generated constructor stub
	}

	public SyncpointChannel(Map<String, Object> properties) {
		super(properties);
		// TODO Auto-generated constructor stub
	}

	public SyncpointChannel(String docId, String revId, boolean deleted) {
		super(docId, revId, deleted);
		// TODO Auto-generated constructor stub
	}

	public boolean isReady() {
		ready = this.state.equals("ready");
		return ready;
	}

	public void setReady(boolean ready) {
		this.ready = ready;
	}

	public boolean isUnpaired() {
		unpaired = this.state.equals("unpaired");
		return unpaired;
	}

	public void setUnpaired(boolean unpaired) {
		this.unpaired = unpaired;
	}

	public SyncpointSubscription getSubscription() {
		return subscription;
	}

	public void setSubscription(SyncpointSubscription subscription) {
		this.subscription = subscription;
	}

	public SyncpointInstallation getInstallation() {
		return installation;
	}

	public void setInstallation(SyncpointInstallation installation) {
		this.installation = installation;
	}

	public String getCloudDatabase() {
		return cloudDatabase;
	}

	public void setCloudDatabase(String cloudDatabase) {
		this.cloudDatabase = cloudDatabase;
	}
}

package com.couchbase.callback;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import android.content.Context;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDDatabase.TDContentOptions;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.TDStatus;

public class SyncpointModel extends TDRevision {
	
	protected String state;
	private HashSet<String> changedNames;
	private HashSet<SyncpointSubscription> installedSubscriptions;	/** All subscriptions in this session that have installations associated with them. */
	private HashSet<SyncpointInstallation> allInstallations;	/** Enumerates all installations of subscriptions in this session. */
	protected TDServer server;
	protected Context context;
	protected TDDatabase database;	/** The database the item's document belongs to.*/
	
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
	
	public Object getValueOfProperty(String property) {
		Object value = this.getProperties().get(property);
		if ((value == null) && (changedNames != null) && (changedNames.contains(property))) {
			value = this.getValueOfProperty(property);
		}
		return value;
	}


	public HashSet<String> getChangedNames() {
		return changedNames;
	}

	public void setChangedNames(HashSet<String> changedNames) {
		this.changedNames = changedNames;
	}

	public HashSet<SyncpointSubscription> getInstalledSubscriptions() {
		return installedSubscriptions;
	}

	public void setInstalledSubscriptions(
			HashSet<SyncpointSubscription> installedSubscriptions) {
		this.installedSubscriptions = installedSubscriptions;
	}

	public TDServer getServer() {
		return server;
	}

	public void setServer(TDServer server) {
		this.server = server;
	}

	/**
	 * Consider moving this to TDDatabase.
	 * @param database
	 * @param docId
	 * @return TDRevision
	 */
	public static TDRevision getDocumentWithId(TDDatabase database, String docID) {
		TDRevision doc = database.getDocumentWithIDAndRev(docID, null, EnumSet.noneOf(TDDatabase.TDContentOptions.class));
	    if (doc == null) {
	    	if (docID.length() == 0) {
	    		return null;
	    		// TODO: add conditions for _design and regular documents.
	    	} else {
	    	 doc = initWithNewDocumentInDatabase(database, docID);
	    	}
	    }
	    return doc;
	}

	/**
	 * Puts an empty document into the database.
	 * TODO: confirm this is doing what IOS initWithNewDocumentInDatabase does.
	 * @param database
	 * @param docID may be null
	 * @return
	 */
	public static TDRevision initWithNewDocumentInDatabase(TDDatabase database, String docID) {
		TDRevision doc = null;
		TDRevision rev = initWithDocument(docID);
		TDStatus status = new TDStatus();
		doc = database.putRevision(rev, null, false, status);
		if (doc == null) {
			return null;
		}
		// create documentProperties once rev. has been saved.
		if (doc.getProperties() == null) {
			Map<String, Object> documentProperties = new HashMap<String, Object>();
			documentProperties.put("_id", doc.getDocId());
			documentProperties.put("_rev", doc.getRevId());
			documentProperties.put("sequence", doc.getSequence());
			documentProperties.put("deleted", doc.isDeleted());
			doc.setProperties(documentProperties);
		}
		// TODO: docCache?
		return doc;
	}
	
	public static TDRevision initWithURL(String url) {		
		//create a document
		Map<String, Object> documentProperties = new HashMap<String, Object>();
		documentProperties.put("_url", url);
		TDBody body = new TDBody(documentProperties);
		TDRevision rev = new TDRevision(body);
		return rev;
	}

	/**
	 * Creates an empty document with optional _id
	 * TODO: confirm this is doing what IOS initWithDocument does.
	 * @param docID may be null
	 * @return TDRevision with _id if supplied.
	 */
	public static TDRevision initWithDocument(String docID) {
		//create a document
		Map<String, Object> documentProperties = new HashMap<String, Object>();
		if (docID != null) {
			documentProperties.put("_id", docID);
		}
		TDBody body = new TDBody(documentProperties);
		TDRevision rev = new TDRevision(body);
		return rev;
	}

	public HashSet<SyncpointInstallation> getAllInstallations() {
		return allInstallations;
	}

	public void setAllInstallations(HashSet<SyncpointInstallation> allInstallations) {
		this.allInstallations = allInstallations;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public TDDatabase getDatabase() {
		return database;
	}

	public void setDatabase(TDDatabase database) {
		this.database = database;
	}

}

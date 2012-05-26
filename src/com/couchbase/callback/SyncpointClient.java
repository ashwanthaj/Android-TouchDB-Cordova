package com.couchbase.callback;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.ektorp.http.HttpResponse;

import android.content.SharedPreferences;
import android.util.Log;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.TDView;
import com.couchbase.touchdb.TDViewMapBlock;
import com.couchbase.touchdb.TDViewMapEmitBlock;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.replicator.TDPuller;
import com.couchbase.touchdb.replicator.TDPusher;
import com.couchbase.touchdb.replicator.changetracker.TDChangeTracker;
import com.couchbase.touchdb.replicator.changetracker.TDChangeTracker.TDChangeTrackerMode;
import com.couchbase.touchdb.replicator.changetracker.TDChangeTrackerClient;

public class SyncpointClient extends SyncpointModel {

	private URL remote;
	private String appId;
	private TDServer server;
	private TDDatabase localControlDatabase;
	private SyncpointSession session;
	private TDPuller controlPull;
	private TDPusher controlPush;
    private Boolean observingControlPull;
    
    public static final String TAG = "SyncpointClient";
    
    private String kLocalControlDatabaseName = "sp_control";
    
	public SyncpointClient(TDBody body) {
		super(body);
		// TODO Auto-generated constructor stub
	}

	public SyncpointClient(Map<String, Object> properties) {
		super(properties);
		// TODO Auto-generated constructor stub
	}

	public SyncpointClient(String docId, String revId, boolean deleted) {
		super(docId, revId, deleted);
		// TODO Auto-generated constructor stub
	}

	/**
	 * initWithLocalServer
	 * @param localServer
	 * @param remoteServerURL
	 * @param syncpointAppId
	 * @param preferences
	 */
	public SyncpointClient init(TDServer localServer, URL remoteServerURL, String syncpointAppId, SharedPreferences preferences) {
    	server = localServer;
        remote = remoteServerURL;
        appId = syncpointAppId;
        
        localControlDatabase = setupControlDatabaseNamed(kLocalControlDatabaseName);
        session = SyncpointSession.sessionInDatabase(localControlDatabase, preferences);
        return this;
    }
    
    TDDatabase setupControlDatabaseNamed(String name) {
    	TDDatabase database = server.getDatabaseNamed(name);
    	database.open();
//    	if (!ensureCreated(server, database)) {
//    		return null;
//    	}
    	// Create a 'view' of known channels by owner:
		TDRevision doc = getDocumentWithId(database, "_design/syncpoint");
		TDView view = database.getViewNamed("syncpoint/channels");
        view.setMapReduceBlocks(new TDViewMapBlock() {
            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                if(document.get("type") == "channel") {
                    emitter.emit(document.get("owner_id"), document);
                }
            }
        }, null, "1.1");
        setTracksChanges(true, server, database);
        //Log.v(TAG, "...Created CouchDatabase at " + database.getPath());

    	return database;
    }
    
    /**
     * 
     * @param server
     * @param database
     * @return
     */
    public static boolean ensureCreated(TDServer server, TDDatabase database) {
    	TouchDBHttpClient client = new TouchDBHttpClient(server);
    	String name = database.getName();
    	HttpResponse response = client.put("/"+name, database.toString());
    	int status = response.getCode();
    	if ((response.isSuccessful()) && (status != 412)) {
    		return false;
    	}
    	return true;
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
        	 //create a document
            Map<String, Object> documentProperties = new HashMap<String, Object>();
            documentProperties.put("_id", docID);
            TDBody body = new TDBody(documentProperties);
            TDRevision rev1 = new TDRevision(body);
            TDStatus status = new TDStatus();
            doc = database.putRevision(rev1, null, false, status);
            if (doc == null) {
            	return null;
            }
            // TODO: docCache?
        	}
        }
        return doc;
	}
	
	public void setTracksChanges(boolean tracksChanges, TDServer server, TDDatabase database) {
		//this.tracksChanges = tracksChanges;
        TDChangeTrackerClient client = new TDChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(TDChangeTracker tracker) {
                Log.v(TAG, "See change tracker stopped");
            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
                Log.v(TAG, "See change " + seq.toString());
            }

            @Override
            public HttpClient getHttpClient() {
            	//return (HttpClient) new TouchDBHttpClient(server);
            	return new DefaultHttpClient();
            }
        };
        
        TDChangeTracker tracker = database.getTracker();
        
		if (tracksChanges && tracker == null) {
			long lastSequence = database.getLastSequence();	
			URL url = null;
			try {
				url = new URL("touchdb:///" + database.getName());
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			tracker = new TDChangeTracker(url, TDChangeTrackerMode.LongPoll, lastSequence, client);
			tracker.setServer(server);
			tracker.start();
		} else if (!tracksChanges && tracker != null) {
			tracker.stop();
			tracker = null;
		}
	}
	
    
    
	public URL getRemote() {
		return remote;
	}
	public void setRemote(URL remote) {
		this.remote = remote;
	}
	public String getAppId() {
		return appId;
	}
	public void setAppId(String appId) {
		this.appId = appId;
	}
	public TDServer getServer() {
		return server;
	}
	public void setServer(TDServer server) {
		this.server = server;
	}
	public TDDatabase getLocalControlDatabase() {
		return localControlDatabase;
	}
	public void setLocalControlDatabase(TDDatabase localControlDatabase) {
		this.localControlDatabase = localControlDatabase;
	}
	public SyncpointSession getSession() {
		return session;
	}
	public void setSession(SyncpointSession session) {
		this.session = session;
	}
	public TDPuller getControlPull() {
		return controlPull;
	}
	public void setControlPull(TDPuller controlPull) {
		this.controlPull = controlPull;
	}
	public TDPusher getControlPush() {
		return controlPush;
	}
	public void setControlPush(TDPusher controlPush) {
		this.controlPush = controlPush;
	}
	public Boolean getObservingControlPull() {
		return observingControlPull;
	}
	public void setObservingControlPull(Boolean observingControlPull) {
		this.observingControlPull = observingControlPull;
	}

}

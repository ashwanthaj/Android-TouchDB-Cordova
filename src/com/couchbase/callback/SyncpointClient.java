package com.couchbase.callback;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.ektorp.http.HttpResponse;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
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
import com.couchbase.touchdb.replicator.TDReplicator;
import com.couchbase.touchdb.replicator.changetracker.TDChangeTracker;
import com.couchbase.touchdb.replicator.changetracker.TDChangeTracker.TDChangeTrackerMode;
import com.couchbase.touchdb.replicator.changetracker.TDChangeTrackerClient;

public class SyncpointClient extends SyncpointModel {

	private URL remote;
	private String appId;
	private SyncpointSession session;
	private TDReplicator controlPull;
	private TDReplicator controlPush;
    private Boolean observingControlPull;
    protected TDDatabase localControlDatabase;
    
    public static final String TAG = "SyncpointClient";
    
    private String kLocalControlDatabaseName = "sp_control";
    private ObjectMapper mapper = new ObjectMapper();
    
	public SyncpointClient(TDBody body) {
		super(body);
	}

	public SyncpointClient(Map<String, Object> properties) {
		super(properties);
	}

	public SyncpointClient(String docId, String revId, boolean deleted) {
		super(docId, revId, deleted);
	}

	public SyncpointClient(TDBody body, Context context) {
		super(body);
		this.context = context;
	}

	/**
	 * initWithLocalServer
	 * @param localServer
	 * @param remoteServerURL
	 * @param syncpointAppId
	 */
	public SyncpointClient init(TDServer localServer, URL remoteServerURL, String syncpointAppId) {
    	server = localServer;
        remote = remoteServerURL;
        appId = syncpointAppId;
        
        localControlDatabase = setupControlDatabaseNamed(kLocalControlDatabaseName);
        if (localControlDatabase == null) {
        	return null;
        }
        session = SyncpointSession.sessionInDatabase(localControlDatabase, context);
        if (session == null) {	// if no session make one
        	session = SyncpointSession.makeSessionInDatabase(localControlDatabase, appId, remote, context);
        }
        //TODO: error logging
        if (session.isPaired()) {
        	Log.d(TAG, "Session is active");
        	connectToControlDB();
        } else if (session.isReadyToPair()) {
        	Log.d(TAG, String.format("Begin pairing with cloud: %s:",remoteServerURL));
        	beginPairing();
        }
        return this;
    }
    
    TDDatabase setupControlDatabaseNamed(String name) {
    	TDDatabase database = server.getDatabaseNamed(name);
    	database.open();
//    	if (!ensureCreated(server, database)) {
//    		return null;
//    	}
    	// Create a 'view' of known channels by owner:
		TDRevision doc = SyncpointModel.getDocumentWithId(database, "_design/syncpoint");
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
    	return database;
    }
    
    public void beginPairing() {
    	Log.d(TAG, "Pairing session...");
    	if (session.isReadyToPair()) {
    		assert !session.isPaired();
    		TDStatus status = new TDStatus();
    		session.clearState(status);
    	}
    }
    
    public void savePairingUserToRemote() {
    	TDRevision rev = SyncpointModel.initWithURL(remote.toString());
    	TouchDBHttpClient client = new TouchDBHttpClient(server);
    	HttpResponse response = client.get(remote.toString() + "_session");
    	InputStream input = response.getContent();
    	String userDbName = null;	// name of the _users db
        try {
			Map<String,Object> fullBody = mapper.readValue(input, Map.class);
			// when not logged in, server returns:
	    	// {"ok":true,"userCtx":{"name":null,"roles":[]},
	    	//"info":{"authentication_db":"_users","authentication_handlers":["oauth","cookie","default"]}}
			Map<String,Object> info = (Map<String, Object>) fullBody.get("info");
			userDbName = (String) info.get("authentication_db");
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        HashMap<String,Object> userProps = session.pairingUserProperties();
        String id = (String) userProps.get("_id");
        TDRevision newUserDoc = SyncpointModel.initWithDocument(id);
        newUserDoc.getProperties().putAll(userProps);
        String docJson = null;
		try {
			docJson = mapper.writeValueAsString(newUserDoc);
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        // /_users/org.couchdb.user:pairing-59e71fe9a7ca91f6d18eb683eba99f59
    	response = client.put(remote.toString() + userDbName + "/", docJson);    	
    	 //http://pairing-59e71fe9a7ca91f6d18eb683eba99f59:f3c0ac1e4bcf436f2e84f43b2cd4be98@localhost:5984
    	String credentials = (String) session.pairingUserProperties().get("username") + ":" 
    	+ (String) session.pairingUserProperties().get("password") + "@";
    	// Add relativePath to user: org.couchdb.user:pairing-59e71fe9a7ca91f6d18eb683eba99f59
    	String remoteUserDbUrl = remote.toString() + userDbName + "/" + id;
    	String remoteURLString = remoteUserDbUrl.replace("://", "://" + credentials);
    	//response = client.put(remoteUserDbName, docJson);
    	waitForPairingToComplete(remoteURLString, newUserDoc);
    }
    
    public void waitForPairingToComplete(final String remoteURLString, final TDRevision userDoc) {
    	final TouchDBHttpClient client = new TouchDBHttpClient(server);
    	Handler mHandler = new Handler(); 
		mHandler.postDelayed(new Runnable() { 
	        public void run() { 
	        	Log.v(TAG, "delaying launch of put to remoteUserDbName by 3 seconds.");
	        	HttpResponse response = client.get(remoteURLString);
	        	InputStream input = response.getContent();
	        	String state = null;	// name of the _users db
	            try {
	    			Map<String,Object> fullBody = mapper.readValue(input, Map.class);
	    			state = (String) fullBody.get("pairing_state");
	    		} catch (JsonParseException e) {
	    			// TODO Auto-generated catch block
	    			e.printStackTrace();
	    		} catch (JsonMappingException e) {
	    			// TODO Auto-generated catch block
	    			e.printStackTrace();
	    		} catch (IOException e) {
	    			// TODO Auto-generated catch block
	    			e.printStackTrace();
	    		}
	            if (state.equals("paired")) {
	            	pairingDidComplete(remoteURLString, userDoc);
	            } else {
	            	waitForPairingToComplete(remoteURLString, userDoc);
	            }
	        } 
	    },3000);
    }
    
    private void pairingDidComplete(final String remoteURLString, TDRevision userDoc) {
    	// get the value for the props HashMap from the document.
    	Map<String, Object> props = userDoc.getProperties();
    	session.setState("state");
    	session.setOwnerId((String) props.get("owner_id"));
    	session.setControlDatabase((String) props.get("control_database"));
    	// save session to the control db - sp_control/E9763BE2-98DC-4416-92FC-1C275597EB4C
    	TDStatus status = new TDStatus();
    	database.putRevision(session, null, false, status);
    	Log.v(TAG, "Device is now paired");
    	// TODO: Confirm that numberWithBool:YES returns 1.
    	props.put("_deleted", 1);
    	userDoc.setProperties(props);
    	
    }
    
    /* Buggy...
     * TODO: compare w/ IOS version.
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
    
	private void observeControlDatabase() {
        Observer changeListener = new Observer() {
            @Override
            public void update(Observable observable, Object data) {
            	controlDatabaseChanged();
            }
        };
		localControlDatabase.addObserver(changeListener);		
	}
	
	private void controlDatabaseChanged() {
		// if we are done with first ever sync
		if (session.isControlDbSynced()) {
			Log.v(TAG, "Control DB changed");
			 // collect 1 second of changes before acting
//	        todo can we make these calls all collapse into one?
			// TODO: Does this duplicate the intent of MYAfterDelay?
			Handler mHandler = new Handler(); 
			mHandler.postDelayed(new Runnable() { 
		        public void run() { 
		        	Log.v(TAG, "delaying launch of getUpToDateWithSubscriptions by 1 second.");
		        	getUpToDateWithSubscriptions();
		        } 
		    },1000);
		}
	}

	// Called when the control database changes or is initial pulled from the server.
	private void getUpToDateWithSubscriptions() {
		Log.v(TAG, "getUpToDateWithSubscriptions");
		// Make installations for any subscriptions that don't have one:
	    HashSet<SyncpointSubscription> installedSubscriptions = session.getInstalledSubscriptions();
	    for (SyncpointSubscription sub : installedSubscriptions) {
	    	Log.v(TAG, "Making installation db for " + sub);
	    	sub.context = this.context;
	    	sub.makeInstallationWithLocalDatabase(localControlDatabase);	// TODO: Report error
		}
	 // Sync all installations whose channels are ready:
	    HashSet<SyncpointInstallation> allInstallations = session.getAllInstallations();
	    for (SyncpointInstallation inst : allInstallations) {
			if (inst.getChannel().isReady()) {
				inst.sync();
			}
		}
	}
	
	private TDReplicator pullControlDataFromDatabaseNamed(String controlDatabase, boolean continuous) {
		TDReplicator replicator = null;
		try {
			URL url = new URL(remote.toString() + controlDatabase);
			//pullRemote = new TDPuller(localControlDatabase, url, false);
			replicator = localControlDatabase.getReplicator(url, false, continuous);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return replicator;
	}
	
	private TDReplicator pushControlDataToDatabaseNamed(String controlDatabase, boolean continuous) {
		TDReplicator replicator = null;
		try {
			URL url = new URL(remote.toString() + controlDatabase);
			replicator = localControlDatabase.getReplicator(url, true, continuous);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return replicator;
	}
	
	protected void connectToControlDB() {
		if (session != null) {
			Log.v(TAG, "connectToControlDB " + session.getControlDatabase());
			if (!session.isControlDbSynced()) {
				doInitialSyncOfControlDB();	// sync once before we write
			} else {
				didInitialSyncOfControlDB();	// go continuous
			}
		}
	}
	
	private void doInitialSyncOfControlDB() {
		if (!observingControlPull) {
			// During the initial sync, make the pull non-continuous, and observe when it stops.
	        // That way we know when the control DB has been fully updated from the server.
	        // Once it has stopped, we can fire the didSyncControlDB event on the session,
	        // and restart the sync in continuous mode.
	        Log.v(TAG, "doInitialSyncOfControlDB ");
	        controlPull = pullControlDataFromDatabaseNamed(session.getControlDatabase(), false);
	        Observer observer = new Observer() {
				
				@Override
				public void update(Observable observable, Object data) {
					Log.v(TAG, "observingControlPull says update. ");
					if (observable == controlPull) {
			            if (!controlPull.isRunning()) {
			            	didInitialSyncOfControlDB();	// go continuous
			            }
					}
				}
			};
			controlPull.addObserver(observer);
	       // [_controlPull addObserver: self forKeyPath: @"running" options: 0 context: NULL];
	        observingControlPull = true;
		}
	}
	
	/**
	 * restart the sync in continuous mode.
	 */
	private void didInitialSyncOfControlDB() {
		Log.v(TAG, "didInitialSyncOfControlDB");
		// Now we can sync continuously & push
		controlPull= pullControlDataFromDatabaseNamed(session.getControlDatabase(), true);
		controlPush = pushControlDataToDatabaseNamed(session.getControlDatabase(), true);
		session.didFirstSyncOfControlDB();
		//MYAfterDelay(1.0, ^{
		getUpToDateWithSubscriptions();
		observeControlDatabase();
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
	public SyncpointSession getSession() {
		return session;
	}
	public void setSession(SyncpointSession session) {
		this.session = session;
	}
	public Boolean getObservingControlPull() {
		return observingControlPull;
	}
	public void setObservingControlPull(Boolean observingControlPull) {
		this.observingControlPull = observingControlPull;
	}

	public TDReplicator getControlPull() {
		return controlPull;
	}

	public void setControlPull(TDReplicator controlPull) {
		this.controlPull = controlPull;
	}

	public TDReplicator getControlPush() {
		return controlPush;
	}

	public void setControlPush(TDReplicator controlPush) {
		this.controlPush = controlPush;
	}

	public TDDatabase getLocalControlDatabase() {
		return localControlDatabase;
	}

	public void setLocalControlDatabase(TDDatabase localControlDatabase) {
		this.localControlDatabase = localControlDatabase;
	}

}

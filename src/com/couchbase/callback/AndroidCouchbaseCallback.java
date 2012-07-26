/**
 *     Copyright 2011 Couchbase, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.couchbase.callback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import junit.framework.Assert;

import org.apache.cordova.DroidGap;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.DbAccessException;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.couchbase.syncpoint.SyncpointClient;
import com.couchbase.syncpoint.impl.SyncpointClientImpl;
import com.couchbase.syncpoint.model.SyncpointChannel;
import com.couchbase.syncpoint.model.SyncpointInstallation;
import com.couchbase.syncpoint.model.SyncpointSession;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.TDView;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.javascript.TDJavaScriptViewCompiler;
import com.couchbase.touchdb.listener.TDListener;
import com.couchbase.touchdb.replicator.TDReplicator;

public class AndroidCouchbaseCallback extends DroidGap
{
    public static final String TAG = AndroidCouchbaseCallback.class.getName();
    private static final int ACTIVITY_ACCOUNTS = 1;
    private static final int MENU_ACCOUNTS = 2;    
    private static final int COPY_TEXT = 3; 
    private static final int SIGNUP_PROCESS = 4;
    public static final String COUCHBASE_DATABASE_SUFFIX = ".couch";
    public static final String TOUCHDB_DATABASE_SUFFIX = ".touchdb";
    public static final String WELCOME_DATABASE = "welcome";
    public static final String DEFAULT_ATTACHMENT = "/index.html";
    private TDListener listener;
    private ProgressDialog progressDialog;
    private Handler uiHandler;
    static Handler myHandler;
    private Account selectedAccount;
    private boolean registered;
    private SyncpointClient syncpoint;
    private TDServer server = null;
    private TDDatabase newDb;
	private String localSyncpointDbName;	// null if local syncpoint DB has not been created and replicated.
	private String url;	//baseURL for local couchapp.
	private String appDb;	// db name from properties file used in installation.
	private String couchAppInstanceUrl;	// from properties file used in installation.
	private int replicationChangesProcessed;


    protected boolean installWelcomeDatabase() {
        return true;
    }

    protected boolean showSplashScreen() {
        return true;
    }

    protected int getSplashScreenDrawable() {
        return R.drawable.splash;
    }

    protected String getAttachmentPath() {
        return DEFAULT_ATTACHMENT;
    }

    protected void couchbaseStarted(String host, int port) {

    }


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if(showSplashScreen()) {
            // show the splash screen
            // NOTE: Callback won't show the splash until we try to load a URL
            //       so we start a load, with a wait time we should never exceed
            setIntegerProperty("splashscreen", getSplashScreenDrawable());
            //loadUrl("file:///android_asset/www/error.html", 60000);
        }
        //loadUrl("file:///android_asset/www/index.html");

        // increase the default timeout
        super.setIntegerProperty("loadUrlTimeoutValue", 90000);
        
        String filesDir = getFilesDir().getAbsolutePath();

        Properties properties = new Properties();

        try {
        	InputStream rawResource = getResources().openRawResource(R.raw.coconut);
        	properties.load(rawResource);
        } catch (Resources.NotFoundException e) {
        	System.err.println("Did not find raw resource: " + e);
        } catch (IOException e) {
        	System.err.println("Failed to open microlog property file");
        }
        
        try {
            server = new TDServer(filesDir);           
            listener = new TDListener(server, 8888);
            listener.start();
            
            TDView.setCompiler(new TDJavaScriptViewCompiler());

        } catch (IOException e) {
            Log.e(TAG, "Unable to create TDServer", e);
        }

        // start couchbase
        //couchbaseService = couchbaseMobile.startCouchbase();
        String ipAddress = "0.0.0.0";
        Log.d(TAG, ipAddress);
		String host = ipAddress;
		int port = 8888;
		url = "http://" + host + ":" + Integer.toString(port) + "/";
		
        uiHandler = new Handler();
        appDb = properties.getProperty("app_db");
        couchAppInstanceUrl = properties.getProperty("couchAppInstanceUrl");
	    File destination = new File(filesDir + File.separator + appDb + TOUCHDB_DATABASE_SUFFIX);
	    String masterServer = properties.getProperty("master_server");
	    if (masterServer != null) {
	    	Constants.serverURLString = masterServer;
	    	//Constants.replicationURL = masterServer + "/" + appDb;
	    	Log.d(TAG, "Disabled Constants.replicationURL: no replication.");
		    Log.d(TAG, "replicationURL: " + Constants.replicationURL);
	    }
	    String syncpointAppId = properties.getProperty("syncpoint_app_id");
	    if (syncpointAppId != null) {
	    	Constants.syncpointAppId = syncpointAppId;
	    }
	    String syncpointDefaultChannelName = properties.getProperty("syncpoint_default_channel");
	    if (syncpointDefaultChannelName != null) {
	    	Constants.syncpointDefaultChannelName = syncpointDefaultChannelName;
	    }
	    //setContentView(R.layout.receive_result);
	    //TDDatabase db = server.getDatabaseNamed(appDb);
	    Log.d(TAG, "Checking for touchdb at " + filesDir + File.separator + appDb + TOUCHDB_DATABASE_SUFFIX);
	    if (!destination.exists()) {
	    	Log.d(TAG, "Touchdb does not exist. Installing.");
	    	// must be in the assets directory
	    	try {
	    		//db.replaceWithDatabase(appDb + TOUCHDB_DATABASE_SUFFIX, appDb);
	    		// This is the appDb touchdb
	        	this.copyFileOrDir(appDb + TOUCHDB_DATABASE_SUFFIX, filesDir);
	    		// These are the appDb attachments
	        	this.copyFileOrDir(appDb, filesDir);
	        	// This is the mobilefuton touchdb
	        	this.copyFileOrDir("mobilefuton" + TOUCHDB_DATABASE_SUFFIX, filesDir);
	        	// These are the mobilefuton attachments
	        	this.copyFileOrDir("mobilefuton", filesDir);
			} catch (Exception e) {
				e.printStackTrace();
				String errorMessage = "There was an error extracting the database.";
				displayLargeMessage(errorMessage, "big");
				Log.d(TAG, errorMessage);
				progressDialog.setMessage(errorMessage);
				//this.setCouchAppUrl("/");
				AndroidCouchbaseCallback.this.loadUrl(url);
			}
	    } else {
	    	Log.d(TAG, "Touchdb exists. Checking Syncpoint status.");	    	
	    }
	    
    	/** Syncpoint	**/
		// create the syncpoint client
		try {
	    	URL masterServerUrl = null;
			try {
				masterServerUrl = new URL(masterServer);
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			syncpoint = new SyncpointClientImpl(getApplicationContext(), masterServerUrl, Constants.syncpointAppId);
			SyncpointChannel channel = syncpoint.getMyChannel(syncpointDefaultChannelName);
			SyncpointInstallation inst = channel.getInstallation(getApplicationContext());
			if(inst != null) {
				CouchDbConnector localDatabase = inst.getLocalDatabase(getApplicationContext());
				String localDatabaseName = localDatabase.getDatabaseName();
				Log.v(TAG, "localDatabaseName: " + localDatabaseName);
				//TDDatabase origDb = server.getDatabaseNamed(appDb);
				//origDb.open();
				newDb = server.getDatabaseNamed(localDatabaseName);
				newDb.open();

				long designDocId = newDb.getDocNumericID("_design/couchabb");

				if (designDocId < 1) {
					/*URL localCouchappUrl = null;
					try {
						localCouchappUrl = new URL(url + appDb);
					} catch (MalformedURLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					TDReplicator replPull = newDb.getReplicator(localCouchappUrl, false, false);
					replPull.start();*/
				    // show signup progress indicator
					startSignupProcessActivity();
				} else {
					localSyncpointDbName = localDatabaseName;
					String couchAppUrl = url + appDb + "/" + properties.getProperty("couchAppInstanceUrl");
				    if (newDb != null) {
				    	couchAppUrl = url + localSyncpointDbName + "/" + properties.getProperty("couchAppInstanceUrl");
				    }
				    Log.d( TAG, "Loading couchAppUrl: " + couchAppUrl );
				    AndroidCouchbaseCallback.this.loadUrl(couchAppUrl);
				}
			} else {
				startAccountSelector();
			}
		} catch (DbAccessException e1) {
			Log.e( TAG, "Error: " , e1);
			e1.printStackTrace();
			Toast.makeText(this, "Error: Unable to connect to Syncpoint Server: " + e1.getMessage(), Toast.LENGTH_LONG).show();
		}
		//setContentView(R.layout.receive_result);
    }

	public void startAccountSelector() {
		Log.i(TAG, "startAccountSelector");
		Intent i = new Intent();
		i.setClassName( 
		    "com.couchbase.callback",
		    "com.couchbase.callback.AccountSelector" );
		startActivityForResult(i, ACTIVITY_ACCOUNTS );
	}
	
	public void startSignupProcessActivity() {
		Log.i(TAG, "startSignupProcessActivity");
		Intent i = new Intent();
		i.setClassName( 
				"com.couchbase.callback",
				"com.couchbase.callback.SignupProcessActivity" );
		startActivityForResult(i, SIGNUP_PROCESS );
	}


    @Override
    protected void onActivityResult( int requestCode,
                                        int resultCode, 
                                        Intent extras ) {
        super.onActivityResult( requestCode, resultCode, extras);
        switch(requestCode) {
        case ACTIVITY_ACCOUNTS: {
        	if (resultCode == RESULT_OK) {
        		//startSignupProcessActivity();
        		String accountName = extras.getStringExtra( "account" );
        		selectedAccount = getAccountFromAccountName( accountName );
        		Toast.makeText(this, "Account selected: "+accountName, Toast.LENGTH_SHORT).show();
        		if( selectedAccount != null ) {
        			register();
            		//startSignupProcessActivity();
        		}	
        	} 
        }
        break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menu.add( Menu.NONE, COPY_TEXT, Menu.NONE, R.string.copy_text );
        menu.add( Menu.NONE, MENU_ACCOUNTS, Menu.NONE, R.string.menu_accounts );
        return result;
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {
        switch ( item.getItemId() ) {
            case MENU_ACCOUNTS: {
                    startAccountSelector();
                    return true;
                }
//            case COPY_TEXT: {
//                Toast.makeText(getApplicationContext(), "Select Text", Toast.LENGTH_SHORT).show();
//                //selectAndCopyText();
//                emulateShiftHeld(webView);
//                return true;
//            }
        }
        return false;
    }
    
	public Account getSelectedAccount() {
		return selectedAccount;
	}
	
	private Account getAccountFromAccountName( String accountName ) {
		AccountManager accountManager = AccountManager.get( this );
		Account accounts[] = accountManager.getAccounts();
		for( int i = 0 ; i < accounts.length ; ++i )
			if( accountName.equals( accounts[i].name ) )
				return accounts[i];
		return null;
	}
	
	private void register() {
		if( registered )
			unregister();
		else {
			Log.d( TAG, "register()" );
			//C2DMessaging.register( this, C2DM_SENDER );
			//syncpoint.pairSessionWithType("console", selectedAccount.name);
			if (selectedAccount != null) {
				try {
					syncpoint.pairSession("console", selectedAccount.name);
			    	HttpClient httpClient = new TouchDBHttpClient(server);
			    	CouchDbInstance localServer = new StdCouchDbInstance(httpClient);  	
			    	//CouchDbConnector userDb = localServer.createConnector("_users", false);
			    	CouchDbConnector localControlDatabase = localServer.createConnector(SyncpointClientImpl.LOCAL_CONTROL_DATABASE_NAME, false);			    	
			    	//PairingUser pairingUser = session.getPairingUser();
					//PairingUser result = userDb.get(PairingUser.class, pairingUser.getId());
					//waitForPairingToComplete(localServer, localControlDatabase);
			    	setupLocalSyncpointDatabase(localServer);
				} catch (DbAccessException e) {
					Log.e( TAG, "Error: " , e);
					Toast.makeText(this, "Error: Unable to connect to Syncpoint Server: " + e.getMessage(), Toast.LENGTH_LONG).show();
				}
				Log.d( TAG, "register() done" );
			}
		}
	}
	private void unregister() {
		if( registered ) {
			Log.d( TAG, "unregister()" );
			//C2DMessaging.unregister( this );
			Log.d( TAG, "unregister() done" );
		}
	}
	
	 /*void waitForPairingToComplete(final CouchDbInstance localServer, final CouchDbConnector localControlDatabase) {
	        Log.v(TAG, "Waiting for pairing to complete...");
	        Looper l = Looper.getMainLooper();
	        Handler h = new Handler(l);
	        h.postDelayed(new Runnable() {

	            @Override
	            public void run() {
	            	String message = "Checking to see if account has been authorized.";
	            	Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
	                Log.v(TAG, message);
			    	SyncpointSession session = SyncpointSession.sessionInDatabase(getApplicationContext(), localServer, localControlDatabase);
	                if (session.isPaired()) {
	                	pairingDidComplete(localServer);
	                } else {
	                	message = "Authorization is in progress. ";
		            	Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
	                    Log.v(TAG, message);
	                    waitForPairingToComplete(localServer, localControlDatabase);
	                }
	                PairingUser user = remote.get(PairingUser.class, userDoc.getId());
	                if("paired".equals(user.getPairingState())) {
	                    pairingDidComplete(localServer);
	                } else {
	                	message = "Pairing state is stuck at " + user.getPairingState();
		            	Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
	                    Log.v(TAG, message);
	                    waitForPairingToComplete(localServer, session, remote, user);
	                }
	            }
	        }, 3000);
	    }*/
	    
	 void setupLocalSyncpointDatabase(final CouchDbInstance localServer) {
		 //CouchDbConnector localControlDatabase = localServer.createConnector(SyncpointClientImpl.LOCAL_CONTROL_DATABASE_NAME, false);
		 //SyncpointSession session = SyncpointSession.sessionInDatabase(getApplicationContext(), localServer, localControlDatabase);
		 //if(session != null) {
		 //if(session.isPaired()) {
		 
		 String message = "Installing local profile.";
		 Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
		 Log.v(TAG, message);
		 SyncpointChannel channel = syncpoint.getMyChannel(Constants.syncpointDefaultChannelName);
		 CouchDbConnector localDatabase = channel.ensureLocalDatabase(getApplicationContext());
		 
		 final String localDatabaseName = localDatabase.getDatabaseName();
		 Log.v(TAG, "localDatabaseName: " + localDatabaseName);
		 //TDDatabase origDb = server.getDatabaseNamed(appDb);
		 //origDb.open();
		 newDb = server.getDatabaseNamed(localDatabaseName);
		 newDb.open();

		 URL localCouchappUrl = null;
		 try {
			 localCouchappUrl = new URL(url + appDb);
		 } catch (MalformedURLException e) {
			 // TODO Auto-generated catch block
			 e.printStackTrace();
		 }
		 TDReplicator replPull = newDb.getReplicator(localCouchappUrl, false, false);
		 replPull.start();
		 
		 boolean activeReplication = true;
		 while (activeReplication == true) {
			 List<TDReplicator> activeReplicators = newDb.getActiveReplicators();
			 int i = 0;
			 if(activeReplicators != null) {
				 for (TDReplicator replicator : activeReplicators) {
					 String source = replicator.getRemote().toExternalForm();
					 Log.v(TAG, "remote " + source);
					 if (source.equals(localCouchappUrl.toExternalForm())) {
						 if (replicator.isRunning() == true) {
							 try {
								 i++;
								 Thread.sleep(1000);
							 } catch (InterruptedException e) {
								 // TODO Auto-generated catch block
								 e.printStackTrace();
							 }
						 }
					 }
				 }
			 }
			 if (i == 0) {
				 activeReplication = false;
			 }
		 }
		 /*Observer observer = new Observer() {

			 @Override
			 public void update(Observable observable, Object data) {
				 Log.v(TAG, "Waiting for replicator to finish ");
				 replicationChangesProcessed = replPull.getChangesProcessed();
				 //if (observable == replPull) {
					 if (!replPull.isRunning()) {
						 launchCouchAppView(localDatabaseName, replPull);
					 }
				 //}
			 }
		 };
		 replPull.addObserver(observer);*/
		 
		 /*int replicationChangesTotal = replPull.getChangesTotal();
		 
		 while(replicationChangesProcessed > replicationChangesTotal) {
			 Log.i(TAG, "Waiting for replicator to finish");
			 try {
				 Thread.sleep(1000);
			 } catch (InterruptedException e) {
				 // TODO Auto-generated catch block
				 e.printStackTrace();
			 }
		 }*/
		 
		 /*Thread t1 = new Thread() {
			 public void run() {
				 replPull.start();
			 }
		 };

		 t1.start();*/
		 
		 

		 /*while(replPull.isRunning()) {
			 Log.i(TAG, "Waiting for replicator to finish");
			 try {
				 Thread.sleep(1000);
			 } catch (InterruptedException e) {
				 // TODO Auto-generated catch block
				 e.printStackTrace();
			 }
		 }*/

		 launchCouchAppView(localDatabaseName);
		
		/* 
		 // create a sample document to verify that replication in the channel is working
		 Map<String,Object> testObject = new HashMap<String,Object>();
		 testObject.put("key", "value");
		 // store the document
		 localDatabase.create(testObject);*/
		 //}
		 //}
	 }

	public void launchCouchAppView(String localDatabaseName) {
		Log.i(TAG, "launchCouchAppView");
		try {
			Thread.sleep(2*1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    localSyncpointDbName = localDatabaseName;
		String couchAppUrl = url + appDb + "/" + couchAppInstanceUrl;
	    if (newDb != null) {
	    	couchAppUrl = url + localSyncpointDbName + "/" + couchAppInstanceUrl;
	    }
	    Log.d( TAG, "Loading couchAppUrl: " + couchAppUrl );
	    AndroidCouchbaseCallback.this.loadUrl(couchAppUrl);
	}
	
    // kudos: http://stackoverflow.com/questions/6058843/android-how-to-select-texts-from-webview    
    private void emulateShiftHeld(WebView view)
    {
        try
        {
            KeyEvent shiftPressEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                                                    KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0);
            shiftPressEvent.dispatch(view);
            Toast.makeText(this, "Now click the text you highlighted.", Toast.LENGTH_SHORT).show();
        }
        catch (Exception e)
        {
            Log.e("dd", "Exception in emulateShiftHeld()", e);
        }
    }
    
    
    
    public void displayLargeMessage( String message, String size ) {
		LayoutInflater inflater = getLayoutInflater();
		View layout = null;
		if (size.equals("big")) {
			layout = inflater.inflate(R.layout.toast_layout_large,(ViewGroup) findViewById(R.id.toast_layout_large));
		} else {
			layout = inflater.inflate(R.layout.toast_layout_medium,(ViewGroup) findViewById(R.id.toast_layout_large));
		}
		
		ImageView image = (ImageView) layout.findViewById(R.id.image);
		image.setImageResource(R.drawable.android);
		TextView text = (TextView) layout.findViewById(R.id.text);
		text.setText(message);
		//uiHandler.post( new ToastMessage( this, message ) );
		/*Toast toast = new Toast(getApplicationContext());
		toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
		toast.setDuration(Toast.LENGTH_LONG);
		toast.setView(layout);*/
		uiHandler.post( new ToastMessageBig( this, message, layout ) );
		//toast.show();
	}
    
    /**
     * kudos: http://stackoverflow.com/a/4530294
     * @param path
     * @param destination
     */
    private void copyFileOrDir(String path, String destination) {
        AssetManager assetManager = this.getAssets();
        String assets[] = null;
        try {
            assets = assetManager.list(path);
            if (assets.length == 0) {
                copyFile(path, destination);
            } else {
                String fullPath = destination + "/" + path;
                File dir = new File(fullPath);
                if (!dir.exists())
                    dir.mkdir();
                for (int i = 0; i < assets.length; ++i) {
                    copyFileOrDir(path + File.separator + assets[i], destination);
                }
            }
        } catch (IOException ex) {
            Log.e("tag", "I/O Exception", ex);
        }
    }

    private void copyFile(String filename, String destination) {
        AssetManager assetManager = this.getAssets();

        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(filename);
            String newFileName = destination + File.separator + filename;
            out = new FileOutputStream(newFileName);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (Exception e) {
            Log.e("tag", e.getMessage());
        }

    }
    
    @Override
    public void onRestart() {
    	super.onRestart();
    	Log.d(TAG, "restarting...");
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	try {
    		Log.d(TAG, "stoppping app...");
    		if (newDb != null) {
        		newDb.close();
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error stopping app.");
    		e.printStackTrace();
    	}
    }
}

class ToastMessageBig implements Runnable {
	View layout;
	Context ctx;
	String msg;
	
	public ToastMessageBig( Context ctx, String msg, View layout ) {
		this.ctx = ctx;
		this.msg = msg;
		this.layout = layout;
	}
	
	public void run() {
		//Toast.makeText( ctx, msg, Toast.LENGTH_SHORT).show();
		Toast toast = new Toast(ctx);
		toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
		toast.setDuration(Toast.LENGTH_LONG);
		toast.setView(layout);
		toast.show();
	}
}


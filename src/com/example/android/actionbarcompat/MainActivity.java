/*
 * Copyright 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.actionbarcompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.api.CordovaInterface;
import org.apache.cordova.api.IPlugin;
import org.apache.cordova.api.LOG;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.DbAccessException;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.couchbase.callback.Constants;
import com.couchbase.syncpoint.SyncpointClient;
import com.couchbase.syncpoint.impl.SyncpointClientImpl;
import com.couchbase.syncpoint.model.SyncpointChannel;
import com.couchbase.syncpoint.model.SyncpointInstallation;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.TDView;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.javascript.TDJavaScriptViewCompiler;
import com.couchbase.touchdb.listener.TDListener;
import com.couchbase.touchdb.replicator.TDReplicator;

public class MainActivity extends ActionBarActivity implements CordovaInterface{
    private boolean mAlternateTitle = false;
    private boolean bound;
    private boolean volumeupBound;
    private boolean volumedownBound;
    
    String TAG = "MainActivity-TouchDB";
    private IPlugin activityResultCallback;
    private Object activityResultKeepRunning;
    private Object keepRunning;

    CordovaWebView mainView;
    
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
    
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mainView =  (CordovaWebView) findViewById(R.id.mainView);
        //mainView.loadUrl("file:///android_asset/www/index.html");
        
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
				mainView.loadUrl("file:///android_asset/www/error.html");
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
				    //AndroidCouchbaseCallback.this.loadUrl(couchAppUrl);
				    mainView.loadUrl(couchAppUrl);
				}
			} else {
				startAccountSelector();
			}
		} catch (DbAccessException e1) {
			Log.e( TAG, "Error: " , e1);
			e1.printStackTrace();
			Toast.makeText(this, "Error: Unable to connect to Syncpoint Server: " + e1.getMessage(), Toast.LENGTH_LONG).show();
		}

/*
        findViewById(R.id.toggle_title).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAlternateTitle) {
                    setTitle(R.string.app_name);
                } else {
                    setTitle(R.string.alternate_title);
                }
                mAlternateTitle = !mAlternateTitle;
            }
        });
        */
    }
    
    public void startAccountSelector() {
		Log.i(TAG, "startAccountSelector");
		/*Intent i = new Intent();
		i.setClassName( 
		    "com.example.android.actionbarcompat",
		    "com.example.android.actionbarcompat.AccountSelector" );
		startActivityForResult(i, ACTIVITY_ACCOUNTS );*/
		mainView.loadUrl("file:///android_asset/www/accounts.html");
	}
	
	public void startSignupProcessActivity() {
		Log.i(TAG, "startSignupProcessActivity");
		/*Intent i = new Intent();
		i.setClassName( 
				"com.example.android.actionbarcompat",
				"com.example.android.actionbarcompat.SignupProcessActivity" );
		startActivityForResult(i, SIGNUP_PROCESS );*/
		mainView.loadUrl("file:///android_asset/www/processing.html");
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main, menu);

        // Calling super after populating the menu is necessary here to ensure that the
        // action bar helpers have a chance to handle this event.
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Toast.makeText(this, "Tapped home", Toast.LENGTH_SHORT).show();
                break;

            case R.id.menu_refresh:
                Toast.makeText(this, "Fake refreshing...", Toast.LENGTH_SHORT).show();
                getActionBarHelper().setRefreshActionItemState(true);
                getWindow().getDecorView().postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                getActionBarHelper().setRefreshActionItemState(false);
                            }
                        }, 1000);
                break;

            case R.id.menu_search:
                Toast.makeText(this, "Tapped search", Toast.LENGTH_SHORT).show();
                break;

            case R.id.menu_share:
                Toast.makeText(this, "Tapped share", Toast.LENGTH_SHORT).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Override the backbutton.
     *
     * @param override
     */
    public void bindBackButton(boolean override) {
        this.bound = override;
    }

    /**
     * Determine of backbutton is overridden.
     *
     * @return
     */
    public boolean isBackButtonBound() {
        return this.bound;
    }

    public void bindButton(String button, boolean override) {
      // TODO Auto-generated method stub
      if (button.compareTo("volumeup")==0) {
        this.volumeupBound = override;
      }
      else if (button.compareTo("volumedown")==0) {
        this.volumedownBound = override;
      }
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    /**
     * Called when a message is sent to plugin.
     *
     * @param id            The message id
     * @param data          The message data
     * @return              Object or null
     */
    public Object onMessage(String id, Object data) {
    	LOG.d(TAG, "onMessage(" + id + "," + data + ")");
    	if ("exit".equals(id)) {
    		super.finish();
    	} else if ("account".equals(id)) {
    		final String accountName = data.toString();
    		selectedAccount = getAccountFromAccountName(accountName);
    		this.getActivity().runOnUiThread(new Runnable() {
    			public void run() {
    				Context context = getApplicationContext();
    				//Toast.makeText(activity, "Hello", Toast.LENGTH_SHORT).show();
    				Toast.makeText(context, "Account selected: "+accountName, Toast.LENGTH_SHORT).show();
    				if( selectedAccount != null ) {
    	    			register();
    	    			//startSignupProcessActivity();
    	    		}	
    			}
    		});	
    	} 
    	return null;
    }
    

    @Override
    public void setActivityResultCallback(IPlugin plugin) {
        this.activityResultCallback = plugin;        
    }
    /**
     * Launch an activity for which you would like a result when it finished. When this activity exits, 
     * your onActivityResult() method will be called.
     *
     * @param command           The command object
     * @param intent            The intent to start
     * @param requestCode       The request code that is passed to callback to identify the activity
     */
    public void startActivityForResult(IPlugin command, Intent intent, int requestCode) {
        this.activityResultCallback = command;
        this.activityResultKeepRunning = this.keepRunning;

        // If multitasking turned on, then disable it for activities that return results
        if (command != null) {
            this.keepRunning = false;
        }

        // Start activity
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void cancelLoadUrl() {
        // This is a no-op.
    }
    

    //@Override
    /**
     * Called when an activity you launched exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode       The request code originally supplied to startActivityForResult(),
     *                          allowing you to identify who this result came from.
     * @param resultCode        The integer result code returned by the child activity through its setResult().
     * @param data              An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
/*    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        IPlugin callback = this.activityResultCallback;
        if (callback != null) {
            callback.onActivityResult(requestCode, resultCode, intent);
        }
    }*/
    
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
    /**
     * Called when the system is about to start resuming a previous activity.
     */
    protected void onPause() {
        super.onPause();

         // Send pause event to JavaScript
        this.mainView.loadUrl("javascript:try{cordova.fireDocumentEvent('pause');}catch(e){console.log('exception firing pause event from native');};");

        // Forward to plugins
        if (this.mainView.pluginManager != null) {
            this.mainView.pluginManager.onPause(true);
        }
    }

    @Override
    /**
     * Called when the activity receives a new intent
     **/
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        //Forward to plugins
        if ((this.mainView != null) && (this.mainView.pluginManager != null)) {
            this.mainView.pluginManager.onNewIntent(intent);
        }
    }

    @Override
    /**
     * Called when the activity will start interacting with the user.
     */
    protected void onResume() {
        super.onResume();

       
        if (this.mainView == null) {
            return;
        }

        // Send resume event to JavaScript
        this.mainView.loadUrl("javascript:try{cordova.fireDocumentEvent('resume');}catch(e){console.log('exception firing resume event from native');};");

        // Forward to plugins
        if (this.mainView.pluginManager != null) {
            this.mainView.pluginManager.onResume(true);
        }

    }

    @Override
    /**
     * The final call you receive before your activity is destroyed.
     */
    public void onDestroy() {
        LOG.d(TAG, "onDestroy()");
        super.onDestroy();
        if (mainView.pluginManager != null) {
            mainView.pluginManager.onDestroy();
        }
        
        if (this.mainView != null) {

            // Send destroy event to JavaScript
            this.mainView.loadUrl("javascript:try{cordova.require('cordova/channel').onDestroy.fire();}catch(e){console.log('exception firing destroy event from native');};");

            // Load blank page so that JavaScript onunload is called
            this.mainView.loadUrl("about:blank");

            // Forward to plugins
            if (this.mainView.pluginManager != null) {
                try {
					this.mainView.pluginManager.onDestroy();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        }
        else {
            //this.endActivity();
        }
    }

	@Override
	public Context getContext() {
		// TODO Auto-generated method stub
		return getApplicationContext();
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
    
    public void register() {
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
	    //this.loadUrl(couchAppUrl);
	    mainView.loadUrl(couchAppUrl);
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

	public boolean isRegistered() {
		return registered;
	}

	public void setRegistered(boolean registered) {
		this.registered = registered;
	}

	public SyncpointClient getSyncpoint() {
		return syncpoint;
	}

	public void setSyncpoint(SyncpointClient syncpoint) {
		this.syncpoint = syncpoint;
	}

}

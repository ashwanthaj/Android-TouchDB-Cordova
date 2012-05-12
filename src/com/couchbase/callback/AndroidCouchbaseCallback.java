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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.TDView;
import com.couchbase.touchdb.javascript.TDJavaScriptViewCompiler;
import com.couchbase.touchdb.listener.TDListener;

import org.apache.cordova.*;

/**
 * Avoid making changes to this class.  If you find the need, please
 * make suggestions here:  https://groups.google.com/forum/#!forum/mobile-couchbase
 */

public class AndroidCouchbaseCallback extends DroidGap
{
    public static final String TAG = AndroidCouchbaseCallback.class.getName();
    public static final String COUCHBASE_DATABASE_SUFFIX = ".couch";
    public static final String TOUCHDB_DATABASE_SUFFIX = ".touchdb";
    public static final String WELCOME_DATABASE = "welcome";
    public static final String DEFAULT_ATTACHMENT = "/index.html";
    private ServiceConnection couchbaseService;
    private String couchappDatabase;
    private TDListener listener;
    private ProgressDialog progressDialog;
    private Handler uiHandler;
    static Handler myHandler;

    protected boolean installWelcomeDatabase() {
        return true;
    }

    protected boolean showSplashScreen() {
        return true;
    }

    protected int getSplashScreenDrawable() {
        return R.drawable.splash;
    }

    protected String getDatabaseName() {
        return findCouchApp();
    }

    protected String getDesignDocName() {
        return findCouchApp();
    }

    protected String getAttachmentPath() {
        return DEFAULT_ATTACHMENT;
    }

    protected String getCouchAppURL(String host, int port) {
        return "http://" + host + ":" + port + "/" + getDatabaseName() + "/_design/" + getDesignDocName() + getAttachmentPath();
    }

    protected String getWelcomeAppURL(String host, int port) {
        return "http://" + host + ":" + port + "/" + WELCOME_DATABASE + "/_design/" + WELCOME_DATABASE + DEFAULT_ATTACHMENT;
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
        
        TDServer server = null;
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
		String url = "http://" + host + ":" + Integer.toString(port) + "/";
		
        uiHandler = new Handler();
        String appDb = properties.getProperty("app_db");
	    File destination = new File(filesDir + File.separator + appDb + TOUCHDB_DATABASE_SUFFIX);
	    String couchAppUrl = url + properties.getProperty("couchAppInstanceUrl");
	    //TDDatabase db = server.getDatabaseNamed(appDb);
	    Log.d(TAG, "Checking for touchdb at " + filesDir + File.separator + appDb + TOUCHDB_DATABASE_SUFFIX);
	    if (!destination.exists()) {
	    	Log.d(TAG, "Touchdb does not exist. Installing.");
	    	// must be in the assets directory
	    	try {
	    		//db.replaceWithDatabase(appDb + TOUCHDB_DATABASE_SUFFIX, appDb);
	    		// This is the touchdb
	        	this.copyFileOrDir(appDb + TOUCHDB_DATABASE_SUFFIX, filesDir);
	    		// These are the attachments
	        	this.copyFileOrDir(appDb, filesDir);
	    		AndroidCouchbaseCallback.this.loadUrl(couchAppUrl);
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
	    	Log.d(TAG, "Touchdb exists. Loading WebView.");
	    	//loadWebview();
	    	AndroidCouchbaseCallback.this.loadUrl(couchAppUrl);
	    }
    }

    /**
     * Look for the first .couch file that is not named "welcome.couch"
     * that can be found in the assets folder
     *
     * @return the name of the database (without the .couch extension)
     * @throws IOException
     */
    public String findCouchApp() {
        String result = null;
        AssetManager assetManager = getAssets();
        String[] assets = null;
        try {
            assets = assetManager.list("");
        } catch (IOException e) {
            Log.e(TAG, "Error listing assets", e);
        }
        if(assets != null) {
            for (String asset : assets) {
                if(!asset.startsWith(WELCOME_DATABASE) && asset.endsWith(COUCHBASE_DATABASE_SUFFIX)) {
                    result = asset.substring(0, asset.length() - COUCHBASE_DATABASE_SUFFIX.length());
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Clean up the Couchbase service
     */
    //@Override
   /* public void onDestroy() {
        if(couchbaseService != null) {
            unbindService(couchbaseService);
        }
        super.onDestroy();
    }*/
    
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


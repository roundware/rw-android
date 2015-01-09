/*
    STORIES FROM MAIN STREET
    Android client application
       Copyright (C) 2008-2013 Halsey Solutions, LLC
    with contributions by Rob Knapen
    ALL RIGHTS RESERVED
*/
package org.famsf.roundware.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.ViewFlipper;

import org.roundware.service.RW;
import org.roundware.service.RWService;
import org.roundware.service.RWTags;
import org.roundware.service.util.RWList;
import org.roundware.service.util.RWListItem;
import org.roundware.service.util.RWUriHelper;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.famsf.roundware.R;
import org.famsf.roundware.Settings;
import org.famsf.roundware.utils.AssetData;
import org.famsf.roundware.utils.AssetImageManager;
import org.famsf.roundware.utils.LocationBg;
import org.famsf.roundware.utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ListenActivity extends Activity {
    private final static String LOGTAG = "Listen";

    // Roundware tag type used in this activity
    private final static String ROUNDWARE_TAGS_TYPE = "listen";
    private final static boolean D = true;

    // Roundware voting types used in this activity
    private final static String AS_VOTE_TYPE_FLAG = "flag";
    private final static String AS_VOTE_TYPE_LIKE = "like";

    private final static int ASSET_IMAGE_LINGER_MS = 2500;

    // fields
    private ProgressDialog mProgressDialog;
    private ViewFlipper mViewFlipper;
    private ImageView mBackgroundImageView;
    private WebView mWebView;
    private Button mHomeButton;
    private Button mExploreButton;

    private Button mRefineButton;
    private ToggleButton mPlayButton;
    private Button mRecordButton;

    private View mAssetImageLayout;
    private ImageView mAssetImageView;
    private TextView mAssetTextView;
    private AssetData mPendingAsset = new AssetData(null,null);
    private AssetData mCurrentAsset = mPendingAsset;


    private PausableScheduledThreadPoolExecutor mEventPool;

    private final Object mAssetImageLock = new Object();

//    private ToggleButton mLikeButton;
//    private ToggleButton mFlagButton;
    private int mVolumeLevel = 80;
    private RWService mRwBinder;
    private RWTags mProjectTags;
    private RWList mTagsList;
    private String mContentFileDir;
    private int mCurrentAssetId;
    private int mPreviousAssetId;
    private AssetImageManager mAssetImageManager = null;

    LocationListener mLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            if (LocationBg.getSite(location) == LocationBg.DE_YOUNG) {
                mBackgroundImageView.setImageResource(R.drawable.bg_listen_dy);
            } else {
                mBackgroundImageView.setImageResource(R.drawable.bg_listen_lh);
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {}
    };

    /**
     * Handles connection state to an RWService Android Service. In this
     * activity it is assumed that the service has already been started
     * by another activity and we only need to connect to it.
     */
    private ServiceConnection rwConnection = new ServiceConnection() {
        @SuppressLint("SetJavaScriptEnabled")
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (D) { Log.d(LOGTAG, "+++ onServiceConnected +++"); }
            mRwBinder = ((RWService.RWServiceBinder) service).getService();
            startPlayback();
            // mRwBinder.playbackFadeIn(mVolumeLevel);
            mRwBinder.setVolumeLevel(mVolumeLevel, false);

            // create a tags list for display and selection
            mProjectTags = mRwBinder.getTags().filterByType(ROUNDWARE_TAGS_TYPE);
            mTagsList = new RWList(mProjectTags);
            mTagsList.restoreSelectionState(Settings.getSharedPreferences());
            synchronized (this){
                if(mAssetImageManager == null){
                    mAssetImageManager = new AssetImageManager(getString(R.string.rw_spec_host_url));
                }
            }
            mAssetImageManager.addTags(mTagsList);
            if(RW.DEBUG_W_FAUX_TAGS){
                Random random = new Random();
                //huge, causes fault
                mAssetImageManager.addTag(1, "http://upload.wikimedia.org/wikipedia/commons/9/92/Artwork_by_North_American_Rockwell.jpg");
                //very small
                mAssetImageManager.addTag(2, "http://upload.wikimedia.org/wikipedia/en/a/ac/MilesSmile.png");
                //medium
                mAssetImageManager.addTag(3, "http://upload.wikimedia.org/wikipedia/commons/7/7e/Tony_Robbin_artwork.JPG");
                //narrow
                mAssetImageManager.addTag(4, "http://upload.wikimedia.org/wikipedia/commons/a/ab/Nachi_Falls.jpg");
                //wide
                mAssetImageManager.addTag(0, "http://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/Kano_Eitoku_003.jpg/1280px-Kano_Eitoku_003.jpg");
            }
            updateUIState();

            // auto start playback when connected (and no already playing)
            // startPlayback();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (D) { Log.d(LOGTAG, "+++ onServiceDisconnected +++"); }
            mRwBinder = null;
        }
    };



    /**
     * Handles events received from the RWService Android Service that we
     * connect to. Since most operations of the service involve making calls
     * to the Roundware server, the response is handle asynchronously with
     * results passed back as broadcast intents. An IntentFilter is set up
     * in the onResume method of this activity and controls which intents
     * from the RWService will be received and processed here.
     */
    private BroadcastReceiver rwReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (D) { Log.d(LOGTAG, "+++ BroadcastReceiver.onReceive +++"); }
            updateUIState();
            if (RW.READY_TO_PLAY.equals(intent.getAction())) {
                if (D) { Log.d(LOGTAG, "RW_READY_TO_PLAY"); }
                // remove progress dialog when needed
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
            } else if (RW.STREAM_METADATA_UPDATED.equals(intent.getAction())) {
                if (D) { Log.d(LOGTAG, "RW_STREAM_METADATA_UPDATED"); }
                handleAssetChange( (Uri)intent.getParcelableExtra(RW.EXTRA_STREAM_METADATA_URI) );

            } else if (RW.USER_MESSAGE.equals(intent.getAction())) {
                if (D) { Log.d(LOGTAG, "RW_USER_MESSAGE"); }
                showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), false, false);
            } else if (RW.ERROR_MESSAGE.equals(intent.getAction())) {
                if (D) { Log.d(LOGTAG, "RW_ERROR_MESSAGE"); }
                showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), true, false);
            } else if (RW.SESSION_OFF_LINE.equals(intent.getAction())) {
                if (D) { Log.d(LOGTAG, "RW_SESSION_OFF_LINE"); }
                showMessage(getString(R.string.connection_to_server_lost_play), true, false);
            } else if (RW.STREAM_BUFFERING_START.equals(intent.getAction())) {
                mEventPool.pause();
            } else if (RW.STREAM_BUFFERING_END.equals(intent.getAction())) {
                mEventPool.resume();
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (D) { Log.d(LOGTAG, "+++ onCreate +++"); }
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setContentView(R.layout.activity_listen);
        initUIWidgets();
        mEventPool = new PausableScheduledThreadPoolExecutor(2);
        mEventPool.setRejectedExecutionHandler( new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                Log.w(LOGTAG, "Event was rejected!");
            }
        });
        mEventPool.setKeepAliveTime(ASSET_IMAGE_LINGER_MS, TimeUnit.MILLISECONDS);

        // connect to service started by other activity
        try {
            Intent bindIntent = new Intent(this, RWService.class);
            bindService(bindIntent, rwConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception ex) {
            showMessage(getString(R.string.connection_to_server_failed) + " " + ex.getMessage(), true, true);
        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        if (D) { Log.d(LOGTAG, "+++ onNewIntent +++"); }
        super.onNewIntent(intent);
        // TODO: activity is reused for a new task
        // TODO: check if this can be used?
        // TODO: or select other launchMode in manifest
        // TODO: reset the filter webview?
        // TODO: test (back) navigation, define tasks and activity stacks

        if (mViewFlipper != null) {
            mViewFlipper.setDisplayedChild(0);
        }
    }


    @Override
    protected void onPause() {
        if (D) { Log.d(LOGTAG, "+++ onPause +++"); }
        super.onPause();
        unregisterReceiver(rwReceiver);
        if (mTagsList != null) {
            mTagsList.saveSelectionState(Settings.getSharedPreferences());
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(mLocationListener);
    }


    @Override
    protected void onResume() {
        if (D) { Log.d(LOGTAG, "+++ onResume +++"); }
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(RW.READY_TO_PLAY);
        filter.addAction(RW.SESSION_ON_LINE);
        filter.addAction(RW.CONTENT_LOADED);
        filter.addAction(RW.SESSION_OFF_LINE);
        filter.addAction(RW.UNABLE_TO_PLAY);
        filter.addAction(RW.ERROR_MESSAGE);
        filter.addAction(RW.USER_MESSAGE);
        filter.addAction(RW.STREAM_METADATA_UPDATED);
        filter.addAction(RW.STREAM_BUFFERING_START);
        filter.addAction(RW.STREAM_BUFFERING_END);
        registerReceiver(rwReceiver, filter);

        startPlayback();

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 10, mLocationListener);
    }


    @Override
    protected void onDestroy() {
        if(mEventPool != null){
            mEventPool.purge();
            mEventPool.shutdownNow();
        }
        if (rwConnection != null) {
            unbindService(rwConnection);
        }
        super.onDestroy();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO: do we want the options menu on this activity?
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.sfmslisten, menu);
        return true;
    }

    /**
     * Sets up the primary UI widgets (spinner and buttons), and how to
     * handle interactions.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void initUIWidgets() {
        mWebView = (WebView) findViewById(R.id.listenFilterWebView);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);

        webSettings.setAppCachePath(this.getFilesDir().getAbsolutePath());
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSupportMultipleWindows(false);
        webSettings.setSupportZoom(false);
        webSettings.setSavePassword(false);
        webSettings.setGeolocationDatabasePath(this.getFilesDir().getAbsolutePath());
        webSettings.setGeolocationEnabled(false);
        webSettings.setDatabaseEnabled(false);
        webSettings.setDomStorageEnabled(false);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(LOGTAG, "shouldOverrideUrlLoading");
                Uri uri = Uri.parse(url);
                if (uri.getScheme().equals("roundware")) {
                    Log.d(LOGTAG, "Processing roundware uri: " + url);
                    String schemeSpecificPart = uri.getSchemeSpecificPart(); // everything from : to #
                    if ("//listen_done".equalsIgnoreCase(schemeSpecificPart)) {
                        // request update of audio stream directly when needed
                        if ((mRwBinder != null) && (mTagsList.hasValidSelectionsForTags())) {
                            if (mRwBinder.isPlaying()) {
                                new ModifyStreamTask(mTagsList, getString(R.string.modify_stream_problem)).execute();
                            }
                        }
                        mViewFlipper.showPrevious();
                        updateUIState();
                    } else if ("//webview_done".equalsIgnoreCase((schemeSpecificPart))) {
                        mViewFlipper.showPrevious();
                    } else {
                        if (mTagsList != null) {
                            mTagsList.setSelectionFromWebViewMessageUri(uri);
                        }
                    }
                    return true;
                }
                // open link in external browser
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (mViewFlipper.getCurrentView().getId() != R.id.web_layout) {
                    mViewFlipper.showNext();
                }
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
              Log.d(LOGTAG, "Error: " + description);
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        });

        mViewFlipper = (ViewFlipper) findViewById(R.id.viewFlipper);
        mBackgroundImageView = (ImageView) findViewById(R.id.background);

        mHomeButton = (Button) findViewById(R.id.left_title_button);
        mHomeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mRwBinder.playbackStop();
                Intent homeIntent = new Intent(ListenActivity.this, MainActivity.class);
                ListenActivity.this.startActivity(homeIntent);
            }
        });

        mExploreButton = (Button) findViewById(R.id.right_title_button);
        mExploreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), ExploreActivity.class));
            }
        });

        mRefineButton = (Button) findViewById(R.id.refine);
        mRefineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // get the folder where the web content files are stored
                mContentFileDir = mRwBinder.getContentFilesDir();
                if ((mWebView != null) && (mContentFileDir != null)) {
                    String contentFileName = mContentFileDir + "listen.html";
                    try {
                        String data = mRwBinder.readContentFile(contentFileName);
                        data = data.replace("/*%roundware_tags%*/", mTagsList.toJsonForWebView(ROUNDWARE_TAGS_TYPE));
                        mWebView.loadDataWithBaseURL("file://" + contentFileName, data, null, null, null);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(LOGTAG, "Problem loading content file: listen.html");
                        // TODO: dialog?? error?? Don't let it get this far?
                    }
                }
            }
        });

        mPlayButton = (ToggleButton) findViewById(R.id.play);
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!mRwBinder.isPlaying()) {
                    startPlayback();
                } else {
                    stopPlayback();
                }
                updateUIState();
            }
        });

        /*
        mFlagButton = (ToggleButton) findViewById(R.id.listenFlagToggleButton);
        mFlagButton.setEnabled(false);

        mLikeButton = (ToggleButton) findViewById(R.id.listenLikeToggleButton);
        mLikeButton.setEnabled(false);
        */

        mRecordButton = (Button) findViewById(R.id.record);
        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPlayback();
                SpeakActivity.showLegalDialogIfNeeded(ListenActivity.this, mRwBinder);
            }
        });

        mAssetImageLayout = findViewById(R.id.asssetImageLayout);
        mAssetImageView = (ImageView)mAssetImageLayout.findViewById(R.id.assetImage);
        mAssetTextView = (TextView)mAssetImageLayout.findViewById(R.id.assetImageDescription);
    }


    private void startPlayback() {
        if (D) { Log.d(LOGTAG, "+++ startPlayback +++"); }
        if (mRwBinder != null) {
            if (!mRwBinder.isPlaying()) {
                if (!mRwBinder.isPlayingMuted()) {
                    showProgress(getString(R.string.starting_playback_title), getString(R.string.starting_playback_message), true, true);
                    mCurrentAssetId = -1;
                    mPreviousAssetId = -1;
                    if(mCurrentAsset == null) {
                        AssetData assetData = new AssetData(null, null);
                        setPendingAsset(assetData);
                        setCurrentAsset(assetData);
                    }
                    mRwBinder.playbackStart(mTagsList);
                }
                mRwBinder.playbackFadeIn(mVolumeLevel);
            }
        }
        updateUIState();
    }


    private void stopPlayback() {
        sendVotingState(mCurrentAssetId);
        mVolumeLevel = mRwBinder.getVolumeLevel();
        mRwBinder.playbackFadeOut();
        mCurrentAssetId = -1;
        mPreviousAssetId = -1;
        updateUIState();
    }


    private void handleAssetChange(Uri uri) {

        if(uri == null){
            //panic
            Log.d(LOGTAG, "handleAssetChange param null!");
            return;
        }

        String assetValue = uri.getQueryParameter(RW.METADATA_URI_NAME_ASSET_ID);
        int assetId = -1;
        if(!TextUtils.isEmpty(assetValue)){
            try {
                assetId = Integer.parseInt(assetValue);
            }catch (NumberFormatException e) {}
        }

        mPreviousAssetId = mCurrentAssetId;
        mCurrentAssetId = assetId;

        // send asset voting if needed
        sendVotingState(mPreviousAssetId);

        List<String> tags = RWUriHelper.getQueryParameterValues(uri, RW.METADATA_URI_NAME_TAGS);
        if(tags.isEmpty()){
            String remaining = RWUriHelper.getQueryParameter(uri, RW.METADATA_URI_NAME_REMAINING);
            if(remaining != null && !remaining.equals("0") ){
                // this metadata message is verbose, remaining assets probably still use same image
                return;
            }
        }


        // update display
        String url = null;
        String description = null;
        for (String tag : tags) {
            if (!TextUtils.isEmpty(tag)) {
                int tagId = -1;
                try {
                    tagId = Integer.parseInt(tag);
                } catch (NumberFormatException e) {
                }
                if (-1 != tagId) {
                    url = mAssetImageManager.getImageUrl(tagId);
                    description = mAssetImageManager.getImageDescription(tagId);
                    if (!TextUtils.isEmpty(url)) {
                        // to support multiple images per asset, collect all tag urls
                        // for each url inflate a new layout and load url into each layout's image
                        Log.d(LOGTAG, "asset image " + tag + " hit " + url);
                        break;
                    }
                }
            }
        }

        AssetData assetData = new AssetData(url, description);
        setPendingAsset(assetData);
        long latency = mRwBinder.getPrepareTime();
        Log.v(LOGTAG, "Scheduling " + url + " in " + latency);
        mEventPool.schedule(new AssetEvent(assetData), latency, TimeUnit.MILLISECONDS);

        if (!TextUtils.isEmpty(url)) {
            //pre-load into cache
            Picasso.with(this).load(url);
        }
    }


    private void sendVotingState(int assetId) {
      /*
        if (assetId != -1) {
            if (mFlagButton.isChecked()) {
                new VoteAssetTask(assetId, AS_VOTE_TYPE_FLAG, null, getString(R.string.vote_asset_problem)).execute();
            }
            if (mLikeButton.isChecked()) {
                new VoteAssetTask(assetId, AS_VOTE_TYPE_LIKE, null, getString(R.string.vote_asset_problem)).execute();
            }
        }
        */
    }


    private void setPendingAsset(AssetData asset) {
        synchronized (mAssetImageLock) {
            mPendingAsset = asset;
        }
    }
    private void setCurrentAsset(AssetData asset){
        synchronized (mAssetImageLock){
            mCurrentAsset = asset;
        }
    }

    private void updateAssetImageUi(final AssetData assetData){
        if(mAssetImageView == null || mAssetTextView == null || assetData == null){
            //panic
            Log.w(LOGTAG, "An Asset Image View is null!");
            return;
        }
        if(!mCurrentAsset.equals(mPendingAsset) && mCurrentAsset.url == null){
            //pending appears valid, do not hide yet
            return;
        }
        synchronized (mAssetImageLock) {
            boolean hasUrl = !TextUtils.isEmpty(assetData.url);
            // Only show images if volume level is audible
            boolean showUrl = hasUrl && (mRwBinder.getVolumeLevel() > 0);
            if (showUrl) {
                //load
                Picasso picasso = Picasso.with(this);
                // set below true, to view image source debugging
                picasso.setIndicatorsEnabled(false);
                picasso.load(mCurrentAsset.url)
                        .noFade()
                        .into(mAssetImageView, new Callback() {
                            @Override
                            public void onSuccess() {
                                if(mCurrentAsset.equals(assetData)) {
                                    // Stale assetdata ignored
                                    mAssetTextView.setText(assetData.description);
                                    mAssetImageLayout.setVisibility(View.VISIBLE);
                                }
                            }

                            @Override
                            public void onError() {
                                Log.w(LOGTAG, "Image load failure!");
                                mAssetImageLayout.setVisibility(View.INVISIBLE);
                            }
                        });
            } else {
                mAssetImageLayout.setVisibility(View.INVISIBLE);
            }
        }

    }

    /**
     * Updates the state of the primary UI widgets based on current
     * connection state and other state variables.
     */
    private void updateUIState() {
        // TODO: Only allow this activity to be current when the RwService is running.
        if (mRwBinder == null) {
            // not connected to RWService
            mPlayButton.setChecked(false);
            mPlayButton.setEnabled(false);
//            mLikeButton.setChecked(false);
//            mLikeButton.setEnabled(false);
//            mFlagButton.setChecked(false);
//            mFlagButton.setEnabled(false);
        } else {
            // connected to RWService
            boolean isPlaying = mRwBinder.isPlaying();
            mPlayButton.setEnabled(true);
            mPlayButton.setChecked(isPlaying);
//          if (!isPlaying) {
//                mLikeButton.setChecked(false);
//                mLikeButton.setEnabled(false);
//                mFlagButton.setChecked(false);
//                mFlagButton.setEnabled(false);
//            }
        }
        updateScreenForSelectedTags();
        updateAssetImageUi(mCurrentAsset);
    }


    private void updateScreenForSelectedTags() {
        if ((mProjectTags == null) || (mTagsList == null)) {
            return;
        }

        RWList exhibits = mTagsList.filter(mProjectTags.findTagByCodeAndType("exhibit", ROUNDWARE_TAGS_TYPE));
        TextView tv = (TextView) findViewById(R.id.listenTitleTextView);
        tv.setText("");
        if (exhibits.getSelectedCount() == 1) {
            RWListItem selectedExhibit = exhibits.getSelectedItems().get(0);
            // update title
            tv.setText(selectedExhibit.getText());
            // update background
            int exhibitId = selectedExhibit.getTagId();
            if (mViewFlipper != null) {
                String imageName = "bg_" + exhibitId;
                int resId = getResources().getIdentifier(imageName, "drawable", "com.earprint.rw");
                if (resId != 0) {
                    mBackgroundImageView.setImageResource(resId);
                } else {
                    mBackgroundImageView.setImageResource(R.drawable.bg_speak_dy);
                }
            }
        }
    }


    /**
     * Shows a standardized message dialog for the specified conditions.
     *
     * @param message to be displayed
     * @param isError type of notification
     * @param isFatal dialog exits activity
     */
    private void showMessage(String message, boolean isError, boolean isFatal) {
        Utils.showMessageDialog(this, message, isError, isFatal);
    }


    /**
     * Shows a standardized progress dialog for the specified conditions.
     *
     * @param title           to be displayed
     * @param message         to be displayed
     * @param isIndeterminate setting for the progress dialog
     * @param isCancelable    setting for the progress dialog
     */
    private void showProgress(String title, String message, boolean isIndeterminate, boolean isCancelable) {
        if (mProgressDialog == null) {
            mProgressDialog = Utils.showProgressDialog(this, title, message, isIndeterminate, isCancelable);
        }
    }


    /**
     * Async task that calls rwModifyStream for direct processing, but in
     * the background for Android to keep the UI responsive.
     *
     * @author Rob Knapen
     */
    private class ModifyStreamTask extends AsyncTask<Void, Void, String> {

        private RWList selections;
        private String errorMessage;

        public ModifyStreamTask(RWList selections, String errorMessage) {
            this.selections = selections;
            this.errorMessage = errorMessage;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                mRwBinder.rwModifyStream(selections, true);
                return null;
            } catch (Exception e) {
                return errorMessage;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result != null) {
                showMessage(result, true, false);
            }
        }
    }


    /**
     * Async task that calls rwSendVoteAsset for direct processing, but in
     * the background for Android to keep the UI responsive.
     *
     * @author Rob Knapen
     */
    private class VoteAssetTask extends AsyncTask<Void, Void, String> {

        private int mAssetId;
        private String mVoteType;
        private String mVoteValue;
        private String mErrorMessage;

        public VoteAssetTask(int assetId, String voteType, String voteValue, String errorMessage) {
            mAssetId = assetId;
            mVoteType = voteType;
            mVoteValue = voteValue;
            mErrorMessage = errorMessage;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                mRwBinder.rwSendVoteAsset(mAssetId, mVoteType, mVoteValue, true);
                return null;
            } catch (Exception e) {
                return mErrorMessage;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result != null) {
                showMessage(result, true, false);
            }
        }
    }

    private class AssetEvent implements Runnable{

        private final AssetData assetData;
        public AssetEvent(AssetData assetData){
            this.assetData = assetData;
        }

        @Override
        public void run() {
            synchronized (mAssetImageLock){
                if(mPendingAsset.equals(assetData)){
                    AssetData previousAsset = mCurrentAsset;
                    setCurrentAsset(assetData);
                    if(TextUtils.isEmpty(assetData.url) && !TextUtils.isEmpty(previousAsset.url)){
                        mEventPool.schedule(new AssetEvent(assetData), ASSET_IMAGE_LINGER_MS,
                                TimeUnit.MILLISECONDS);
                    }else{
                        Log.v(LOGTAG, "Delayed AssetEvent ("+this.assetData.url+") starting now");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateAssetImageUi(assetData);
                            }
                        });
                    }
                }
            }
        }
    }




    /**
     * Adds pausing, adapted from Android Developers Documentation
     * http://developer.android.com/reference/java/util/concurrent/ThreadPoolExecutor.html
     */
    private class PausableScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor{

        public PausableScheduledThreadPoolExecutor(int corePoolSize) {
            super(corePoolSize);
            pauseQueue = new ArrayList<PausedRunnable>();
        }
        private boolean isPaused;
        private ReentrantLock pauseLock = new ReentrantLock();

        private class PausedRunnable{
            public final Runnable runnable;
            public final long delay;
            public PausedRunnable(Runnable runnable, long delay){
                this.runnable = runnable;
                this.delay = delay;
            }
        }

        private ArrayList<PausedRunnable> pauseQueue;

        private void add(Runnable r){
            ScheduledFuture sf = (ScheduledFuture)r;
            pauseQueue.add( new PausedRunnable(r, sf.getDelay(TimeUnit.MILLISECONDS)) );
            sf.cancel(false);
        }

        protected void beforeExecute(Thread t, Runnable r) {
            super.beforeExecute(t, r);
            pauseLock.lock();

            if (isPaused) {
                add(r);
            }

            pauseLock.unlock();

        }

        public void pause() {
            pauseLock.lock();
            try {
                isPaused = true;
                BlockingQueue<Runnable> queue = getQueue();
                for(Runnable r : queue){
                    add(r);
                }
            } finally {
                pauseLock.unlock();
            }
        }

        public void resume() {
            pauseLock.lock();
            try {
                isPaused = false;
                for(PausedRunnable pausedRunnable : pauseQueue){
                    this.schedule(pausedRunnable.runnable, pausedRunnable.delay, TimeUnit.MILLISECONDS);
                }
            } finally {
                pauseLock.unlock();
            }
        }
    }

}


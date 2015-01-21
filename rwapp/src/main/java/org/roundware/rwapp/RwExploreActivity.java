/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.rwapp;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 *
 */
public class RwExploreActivity extends Activity {
    public static final String LOGTAG = RwExploreActivity.class.getSimpleName();
    private final static boolean D = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (D) { Log.d(LOGTAG, "+++ onCreate +++"); }
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.activity_explore);
        WebView webView = (WebView) findViewById(R.id.exploreWebView);

        webView.loadUrl(getString(R.string.explore_url));

        WebSettings webSettings = webView.getSettings();

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

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(LOGTAG, "shouldOverrideUrlLoading");
                Uri uri = Uri.parse(url);
                if (uri.getScheme().equals("roundware")) {
                    Log.d(LOGTAG, "Processing roundware uri: " + url);
                    String schemeSpecificPart = uri.getSchemeSpecificPart(); // everything from : to #
                    if ("//webview_done".equalsIgnoreCase((schemeSpecificPart))) {
                        finish();
                    }
                    return true;
                }
                // open link in external browser
                return super.shouldOverrideUrlLoading(view, url);
            }

        });
    }

}


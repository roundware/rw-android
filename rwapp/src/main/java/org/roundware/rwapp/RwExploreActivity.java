package org.roundware.rwapp;

/**
 * Explore activity
 */
public class RwExploreActivity extends RwServiceWebActivity {
    public static final String LOGTAG = RwExploreActivity.class.getSimpleName();
    private final static boolean D = true;

    @Override
    protected String getUrl() {
        return getString(R.string.explore_url);
    }
}


/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
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


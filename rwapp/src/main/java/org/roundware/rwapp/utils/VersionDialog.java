/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.rwapp.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

/**
 * A dialog that is displayed only once for each new version of an application.
 * Can be used to show information about new features. Retrieves information
 * about the current version name from the package info, and uses the shared
 * preferences to keep track of changes. To make debugging easier it is not
 * based on the version tagId (since Google also uses this to track application
 * updates).
 * 
 * @author Rob Knapen
 */
public class VersionDialog {

    private final static String NAME_NOT_FOUND = "NameNotFoundError";

    public static final String VERSION_DIALOG_PREFERENCE = "VERSION_DIALOG_PREFERENCE";
    private final static String PREF_VERSION_NAME_CHECK = "PREF_VERSION_NAME_CHECK";


    private VersionDialog() {
        // void -- can not create instance
    }


    public static void show(Context context, String packageName, int dialogResId, int text1ResId, boolean forced) {
        String text = context.getResources().getString(text1ResId);
        show(context, packageName, dialogResId, text, forced);
    }


    public static void show(Context context, String packageName, int dialogResId, String text, boolean forced) {
        String currentVersionName = appVersionInfo(context, packageName);
        String storedVersionName = prefVersionInfo(context);
        if ((forced) || (!currentVersionName.equals(storedVersionName))) {
            Dialog d = createDialog(context, dialogResId, text);
            if (d != null) {
                d.show();
                updatePrefVersionInfo(context, currentVersionName);
            }
        }
    }


    private static String appVersionInfo(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_META_DATA);
            return info.versionName;
        } catch (Exception e) {
            return "CouldNotRetrieveVersionInfoError";
        }
    }


    private static String prefVersionInfo(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(VERSION_DIALOG_PREFERENCE, Activity.MODE_PRIVATE);
        return prefs.getString(PREF_VERSION_NAME_CHECK, NAME_NOT_FOUND);
    }


    private static void updatePrefVersionInfo(Context context, String versionName) {
        SharedPreferences prefs = context.getSharedPreferences(VERSION_DIALOG_PREFERENCE, Activity.MODE_PRIVATE);
        Editor editor = prefs.edit();
        editor.putString(PREF_VERSION_NAME_CHECK, versionName);
        editor.apply();
    }


    private static Dialog createDialog(Context context, int dialogResId, String text) {
        AlertDialog.Builder ad = new AlertDialog.Builder(context);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(dialogResId, null);
        TextView tv = (TextView)layout.findViewById(android.R.id.text1);
        tv.setText(text);

        ad.setView(layout);
        ad.setCancelable(true);

        ad.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // void
            }
        });

        return ad.create();
    }

}

package cc.fovea.openwith;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.URLUtil;

import java.io.IOException;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handle serialization of Android objects ready to be sent to javascript.
 */
class Serializer {

    /** Convert an intent to JSON.
     *
     * This actually only exports stuff necessary to see file content
     * (streams or clip data) sent with the intent.
     * If none are specified, null is return.
     */
    public static JSONObject toJSONObject(
            final ContentResolver contentResolver,
            final Intent intent)
            throws JSONException {
        JSONArray items = new JSONArray();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            items = itemsFromClipData(contentResolver, intent);
        }
        if (items.length() == 0) {
            JSONObject item = itemFromExtras(contentResolver, intent.getExtras());
            if(item != null){
                items.put(item);
            }
        }
        if (items.length() == 0) {
            JSONObject item = itemFromData(contentResolver, intent.getData());
            if(item != null){
                items.put(item);
            }
        }

        final JSONObject action = new JSONObject();
        action.put("action", translateAction(intent.getAction()));
        action.put("exit", readExitOnSent(intent.getExtras()));
        action.put("items", items);
        return action;
    }

    public static String translateAction(final String action) {
        if ("android.intent.action.SEND".equals(action) ||
                "android.intent.action.SEND_MULTIPLE".equals(action)) {
            return "SEND";
        } else if ("android.intent.action.VIEW".equals(action)) {
            return "VIEW";
        }
        return action;
    }

    /** Read the value of "exit_on_sent" in the intent's extra.
     *
     * Defaults to false. */
    public static boolean readExitOnSent(final Bundle extras) {
        if (extras == null) {
            return false;
        }
        return extras.getBoolean("exit_on_sent", false);
    }

    /** Extract the list of items from clip data (if available). */
    public static JSONArray itemsFromClipData(
            final ContentResolver contentResolver,
            final Intent intent)
            throws JSONException {
        ClipData clipData = intent.getClipData();
        if (clipData != null) {
            final int clipItemCount = clipData.getItemCount();
            JSONObject[] items = new JSONObject[clipItemCount];
            for (int i = 0; i < clipItemCount; i++) {
                ClipDescription clipDescription = clipData.getDescription();
                if(clipDescription.hasMimeType("text/uri-list")){
                    JSONObject item = urlFromExtra(intent.getExtras());
                    if(item != null){
                        items[i] = item;
                    }
                } else {
                    ClipData.Item clipItem = clipData.getItemAt(i);
                    if(clipDescription.hasMimeType("text/plain")){
                        if(URLUtil.isValidUrl(clipItem.getText().toString())){
                            JSONObject item = urlFromExtra(intent.getExtras());
                            if(item != null){
                                items[i] = item;
                                continue;
                            }
                        }
                    }
                    JSONObject item = toJSONObject(contentResolver, clipItem.getUri());
                    if(item != null){
                        items[i] = item;
                    } else {
                        items[i] = itemFromData(contentResolver, intent.getData());
                    }
                }
            }
            return new JSONArray(items);
        }
        return new JSONArray();
    }

    /** Extract the list of items from the intent's extra stream.
     *
     * See Intent.EXTRA_STREAM for details. */
    public static JSONObject itemFromExtras(
            final ContentResolver contentResolver,
            final Bundle extras)
            throws JSONException {
        if (extras == null) {
            return null;
        }

        return toJSONObject(
                contentResolver,
                (Uri) extras.get(Intent.EXTRA_STREAM));
    }

    /** Extract the url from the intent's extra.
     *
     * See Intent.TEXT for details. */
    public static JSONObject urlFromExtra(
            final Bundle extras)
            throws JSONException {
        if (extras == null) {
            return null;
        }

        final JSONObject json = new JSONObject();
        json.put("url", extras.getString("android.intent.extra.TEXT"));
        json.put("title", extras.getString("android.intent.extra.SUBJECT"));
        return json;
    }

    /** Extract the list of items from the intent's getData
     *
     * See Intent.ACTION_VIEW for details. */
    public static JSONObject itemFromData(
            final ContentResolver contentResolver,
            final Uri uri)
            throws JSONException {
        if (uri == null) {
            return null;
        }
        return toJSONObject(
                contentResolver,
                uri);
    }

    /** Convert an Uri to JSON object.
     *
     * Object will include:
     *    "type" of data;
     *    "uri" itself;
     *    "path" to the file, if applicable.
     *    "data" for the file.
     */
    public static JSONObject toJSONObject(
            final ContentResolver contentResolver,
            final Uri uri)
            throws JSONException {
        if (uri == null) {
            return null;
        }
        final JSONObject json = new JSONObject();
        final String type = contentResolver.getType(uri);
        json.put("type", type);
        json.put("uri", uri);
        json.put("path", getRealPathFromURI(contentResolver, uri));
        return json;
    }

    /** Return data contained at a given Uri as Base64. Defaults to null. */
    public static String getDataFromURI(
            final ContentResolver contentResolver,
            final Uri uri) {
        try {
            final InputStream inputStream = contentResolver.openInputStream(uri);
            final byte[] bytes = ByteStreams.toByteArray(inputStream);
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        }
        catch (IOException e) {
            return "";
        }
    }

    /** Convert the Uri to the direct file system path of the image file.
     *
     * source: https://stackoverflow.com/questions/20067508/get-real-path-from-uri-android-kitkat-new-storage-access-framework/20402190?noredirect=1#comment30507493_20402190 */
    public static String getRealPathFromURI(
            final ContentResolver contentResolver,
            final Uri uri) {
        final String[] proj = { MediaStore.Images.Media.DATA };
        final Cursor cursor = contentResolver.query(uri, proj, null, null, null);
        if (cursor == null) {
            return "";
        }
        final int column_index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
        if (column_index < 0) {
            cursor.close();
            return "";
        }
        cursor.moveToFirst();
        final String result = cursor.getString(column_index);
        cursor.close();
        return result;
    }
}
// vim: ts=4:sw=4:et

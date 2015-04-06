package devsung.dashcam;

/**
 * Created by Peter on 15-04-04.
 */

import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.util.Log;

public class MediaScanner implements MediaScannerConnectionClient{
    public void addFile(String filename)
    {
        String [] paths = new String[1];
        paths[0] = filename;
        MediaScannerConnection.scanFile(context, paths, null, this);
    }

    public void onMediaScannerConnected() {
    }

    public void onScanCompleted(String path, Uri uri) {
        Log.i("ScannerHelper", "Scan done - path:" + path + " uri:" + uri);
    }
}

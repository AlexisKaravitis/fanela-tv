package app.fanela.tv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Serves the match cards (PNG files this app draws) to the home screen, read-only. */
public class ArtProvider extends ContentProvider {
    static String authority(android.content.Context c) { return c.getPackageName() + ".art"; }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        // only our card files (letters, digits, underscore); never crest/image cache files or anything else
        if (name == null || !name.matches("c[a-z0-9]+_[a-z0-9_]+\\.png")) throw new FileNotFoundException(String.valueOf(uri));
        return ParcelFileDescriptor.open(new File(getContext().getFilesDir(), name), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}

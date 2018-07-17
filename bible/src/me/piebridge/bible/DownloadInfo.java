package me.piebridge.bible;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;

public class DownloadInfo {
    public final long id;
    public final int status;
    public final String title;

    public DownloadInfo(long id, int status, String title) {
        this.id = id;
        this.status = status;
        this.title = title;
    }

    public static DownloadInfo getDownloadInfo(Context context, long id) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            return null;
        }
        Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(id));
        if (!cursor.moveToFirst())
            return null;

        int columnStatus = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS);
        int columnTitle = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE);

        int status = cursor.getInt(columnStatus);
        String title = cursor.getString(columnTitle);
        return new DownloadInfo(id, status, title);
    }
}
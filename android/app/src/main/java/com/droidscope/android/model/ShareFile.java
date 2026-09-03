package com.droidscope.android.model;

import android.net.Uri;

public final class ShareFile {
    private final Uri uri;
    private final String displayName;
    private final String mimeType;
    private final long size;

    public ShareFile(Uri uri, String displayName, String mimeType, long size) {
        this.uri = uri;
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.size = size;
    }

    public Uri getUri() { return uri; }
    public String getDisplayName() { return displayName; }
    public String getMimeType() { return mimeType; }
    public long getSize() { return size; }
}

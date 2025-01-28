package de.monocles.chat;

import android.graphics.drawable.Drawable;

import io.ipfs.cid.Cid;

public interface GetThumbnailForCid {
	public Drawable getThumbnail(Cid cid);
}

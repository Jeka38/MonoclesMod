package de.monocles.mod;

import android.graphics.drawable.Drawable;

import io.ipfs.cid.Cid;

public interface GetThumbnailForCid {
	public Drawable getThumbnail(Cid cid);
}

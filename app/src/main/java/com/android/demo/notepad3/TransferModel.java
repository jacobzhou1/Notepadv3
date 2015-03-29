/*
 * Copyright 2010-2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.demo.notepad3;

import android.content.Context;
import android.net.Uri;

import com.amazonaws.mobileconnectors.s3.transfermanager.Transfer;
import com.amazonaws.mobileconnectors.s3.transfermanager.TransferManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/*
 * The TransferModel is a class that encapsulates a transfer. It handles the 
 * interaction with the underlying TransferManager and Upload/Download classes
 */
public abstract class TransferModel {
	private static final String TAG = "TransferModel";

	public static enum Status {
		IN_PROGRESS, PAUSED, CANCELED, COMPLETED
	};

	// all TransferModels have associated id which is their key to sModels
	private static LinkedHashMap<Integer, TransferModel> sModels = new LinkedHashMap<Integer, TransferModel>();
	private static int sNextId = 1;

	private String mFileName;
	private Context mContext;
	private Uri mUri;
	private int mId;
	private TransferManager mManager;

	public static TransferModel getTransferModel(int id) {
		return sModels.get(id);
	}

	public static List<TransferModel> getAllTransfers() {

		List<TransferModel> transferModels = new ArrayList<TransferModel>();
		TransferModel[] tmpModels = new TransferModel[sModels.size()];
		TransferModel[] modelss = sModels.values().toArray(tmpModels);
		for (int i = 0; i < modelss.length; i++) {
			transferModels.add(modelss[i]);
		}
		return transferModels;
	}

	public static void removeTransferModel(int id) {

		sModels.remove(id);
	}

	public TransferModel(Context context, Uri uri, TransferManager manager) {
		mContext = context;
		mUri = uri;
		mManager = manager;
		String uriString = uri.toString();
		mFileName = Util.getFileName(uriString);
		mId = sNextId++;
		sModels.put(mId, this);
	}

	public String getFileName() {
		return mFileName;
	}

	public int getId() {
		return mId;
	}

	public int getProgress() {
		Transfer transfer = getTransfer();
		if (transfer != null) {
			int ret = (int) transfer.getProgress().getPercentTransferred();
			return ret;
		}
		return 0;
	}

	public Uri getUri() {
		return mUri;
	}

	public abstract void abort();

	public abstract Status getStatus();

	public abstract Transfer getTransfer();

	public abstract void pause();

	public abstract void resume();

	protected Context getContext() {
		return mContext;
	}

	protected TransferManager getTransferManager() {
		return mManager;
	}
}

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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.android.demo.notepad3.TransferModel.Status;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/*
 * Activity where user can see the items in the S3 bucket and download stuff
 * from there
 */
public class NoteList extends Activity {

	private static final int REFRESH_DELAY = 500;

	private ListView mList;
	private AmazonS3Client mClient;
	private ObjectAdapter mAdapter;
	// keeps track of the objects the user has selected
	private HashSet<S3ObjectSummary> mSelectedObjects = new HashSet<S3ObjectSummary>();
	private Button mRefreshButton;

	private Timer mTimer;
	private List<TransferModel> mModels = new ArrayList<TransferModel>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_list);
		setTitle("Note list");
		// initialize the client
		mClient = Util.getS3Client(NoteList.this);

		mList = (ListView) findViewById(R.id.list);

		mAdapter = new ObjectAdapter(this);
		mList.setOnItemClickListener(new ItemClickListener());
		mList.setAdapter(mAdapter);

		mRefreshButton = (Button) findViewById(R.id.refresh);

		findViewById(R.id.download).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// download all the objects that were selected
				String[] keys = new String[mSelectedObjects.size()];
				int i = 0;
				for (S3ObjectSummary obj : mSelectedObjects) {
					keys[i] = obj.getKey();
					i++;
				}
				TransferController.download(NoteList.this, keys);
			}
		});

		findViewById(R.id.refresh).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new RefreshTask().execute();
			}
		});

		new RefreshTask().execute();

		// make timer that will refresh all the transfer views
		mTimer = new Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				NoteList.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						syncModels();
						refresh();
					}
				});
			}
		};
//		mTimer.schedule(task, 0, REFRESH_DELAY);
	}

	private class RefreshTask extends
			AsyncTask<Void, Void, List<S3ObjectSummary>> {
		@Override
		protected void onPreExecute() {
			mRefreshButton.setEnabled(false);
			mRefreshButton.setText(R.string.refreshing);
		}

		@Override
		protected List<S3ObjectSummary> doInBackground(Void... params) {
			// get all the objects in bucket
			return mClient.listObjects(
					Constants.BUCKET_NAME.toLowerCase(Locale.US),
					Util.getPrefix(NoteList.this)).getObjectSummaries();
		}

		@Override
		protected void onPostExecute(List<S3ObjectSummary> objects) {
			// now that we have all the keys, add them all to the adapter
			mAdapter.clear();
			mAdapter.addAll(objects);
			mSelectedObjects.clear();
			mRefreshButton.setEnabled(true);
			mRefreshButton.setText(R.string.refresh);
		}
	}

	/*
	 * This lets the user click on anywhere in the row instead of just the
	 * checkbox to select the files to download
	 */
	private class ItemClickListener implements OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int pos,
				long id) {
			S3ObjectSummary item = mAdapter.getItem(pos);
			boolean checked = false;
			// try removing, if it wasn't there add
			if (!mSelectedObjects.remove(item)) {
				mSelectedObjects.add(item);
				checked = true;
			}
			((ObjectAdapter.ViewHolder) view.getTag()).checkbox
					.setChecked(checked);
		}
	}

	/* Adapter for all the S3 objects */
	private class ObjectAdapter extends ArrayAdapter<S3ObjectSummary> {
		public ObjectAdapter(Context context) {
			super(context, R.layout.bucket_row);
		}

		@Override
		public View getView(int pos, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = LayoutInflater.from(getContext()).inflate(
						R.layout.bucket_row, null);
				holder = new ViewHolder(convertView);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}
			S3ObjectSummary summary = getItem(pos);
			holder.checkbox.setChecked(mSelectedObjects.contains(summary));
			holder.key.setText(Util.getFileName(summary.getKey()));
			holder.size.setText(String.valueOf(summary.getSize()));
			return convertView;
		}

		public void addAll(Collection<? extends S3ObjectSummary> collection) {
			for (S3ObjectSummary obj : collection) {
				// if statement removes the "folder" from showing up
				if (!obj.getKey().equals(Util.getPrefix(NoteList.this))) {
					add(obj);
				}
			}
		}

		private class ViewHolder {
			private CheckBox checkbox;
			private TextView key;
			private TextView size;

			private ViewHolder(View view) {
				checkbox = (CheckBox) view.findViewById(R.id.checkbox);
				key = (TextView) view.findViewById(R.id.key);
				size = (TextView) view.findViewById(R.id.size);
			}
		}
	}

	/* makes sure that we are up to date on the transfers */
	private void syncModels() {
		List<TransferModel> models = TransferModel.getAllTransfers();
		if (mModels.size() != models.size()) {
			// add the transfers we haven't seen yet
			mModels = models;
		}
	}

	/* refresh method for public use */
	public void refresh() {

		for (int i = 0; i < mModels.size(); i++)
			refresh(mModels.get(0));
	}

	/*
	 * We use this method within the class so that we can have the UI update
	 * quickly when the user selects something
	 */
	private void refresh(TransferModel model) {

		Status status = model.getStatus();

		int progress = 0;
		switch (status) {
		case IN_PROGRESS:

			progress = model.getProgress();

			break;
		case PAUSED:
			progress = 0;
			break;
		case CANCELED:
			progress = -1;
			break;
		case COMPLETED:

			progress = 100;

			if (model instanceof DownloadModel) {

				TransferModel.removeTransferModel(model.getId());

				// if download completed, show option to open file
				// get the file extension
				MimeTypeMap m = MimeTypeMap.getSingleton();
				String mimeType = m.getMimeTypeFromExtension(MimeTypeMap
						.getFileExtensionFromUrl(model.getUri().toString()));

				try {
					// try opening activity to open file
					Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
					intent.setDataAndType(model.getUri(), mimeType);
					startActivity(intent);
				} catch (ActivityNotFoundException e) {
					// if file fails to be opened, show error
					// message
					Toast.makeText(this, R.string.nothing_found_to_open_file,
							Toast.LENGTH_SHORT).show();
				}
			}
			break;
		}

	}

	/* What to do when user presses pause button */
	private void pause(TransferModel model) {
		if (model.getStatus() == Status.IN_PROGRESS) {
			TransferController.pause(this, model);
			model.pause();
			refresh(model);
		} else {
			TransferController.resume(this, model);
			model.resume();
			refresh(model);
		}
	}

	/* What to do when user presses abort button */
	private void abort(TransferModel model) {
		TransferController.abort(this, model);
		model.abort();
		refresh(model);
	}

}

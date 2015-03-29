/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.demo.notepad3;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.android.demo.notepad3.TransferModel.Status;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class NoteEdit extends Activity {

	private static final int REFRESH_DELAY = 500;

	private EditText mTitleText;
	private EditText mBodyText;
	private Timer mTimer;
	private ProgressDialog pd;
	private List<TransferModel> mModels = new ArrayList<TransferModel>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.note_edit);
		setTitle(R.string.edit_note);

		mTitleText = (EditText) findViewById(R.id.title);
		mBodyText = (EditText) findViewById(R.id.body);

		pd = new ProgressDialog(this);
		pd.setCancelable(false);
		pd.setMessage("Uploading...");

		Button confirmButton = (Button) findViewById(R.id.confirm);

		confirmButton.setOnClickListener(new View.OnClickListener() {

			public void onClick(View view) {
				saveData();
			}

		});

		// make timer that will refresh all the transfer views
		mTimer = new Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				NoteEdit.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						syncModels();
						refresh();
					}
				});
			}
		};
		mTimer.schedule(task, 0, REFRESH_DELAY);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mTimer.cancel();
		pd.dismiss();
	}

	@Override
	protected void onStop() {
		super.onStop();
		mTimer.purge();
	}

	private void saveData() {

		if (!pd.isShowing())
			pd.show();

		try {

			String title = mTitleText.getText().toString().trim();

			if (title.equals("")) {

				Toast.makeText(getApplicationContext(),
						"You must input the title", Toast.LENGTH_SHORT).show();

				return;
			}

			File saveFile = new File(getFilesDir().getPath() + File.separator
					+ title + ".txt");

			FileOutputStream outStream = new FileOutputStream(saveFile, false);
			outStream.write(mBodyText.getText().toString().getBytes());
			outStream.close();

			Uri uri = Uri.fromFile(saveFile);

			if (uri != null) {
				TransferController.upload(this, uri);
			}

		} catch (Exception e) {
			e.printStackTrace();
			pd.hide();
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

		if (mModels.size() > 0)
			refresh(mModels.get(0));
	}

	/*
	 * We use this method within the class so that we can have the UI update
	 * quickly when the user selects something
	 */
	private void refresh(TransferModel model) {

		Status status = model.getStatus();
		boolean isUploadModel = model instanceof UploadModel;

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

			break;
		}

		if (isUploadModel) {

			pd.setMessage("Uploading " + model.getFileName() + "..." + progress
					+ "");

			if (progress == 0 || progress == -1 || progress == 100) {
				pd.hide();

				File file = new File(model.getUri().getPath());

				if (file.exists()) {
					file.delete();
				}

				TransferModel.removeTransferModel(model.getId());

				Toast.makeText(getApplicationContext(),
						"Save to AWS S3 succeed.", Toast.LENGTH_SHORT).show();
				finish();

			}

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

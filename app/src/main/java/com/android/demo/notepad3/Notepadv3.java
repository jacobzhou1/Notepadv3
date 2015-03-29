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

import com.amazonaws.services.s3.AmazonS3Client;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class Notepadv3 extends Activity implements OnClickListener {

	private Button btn_add_note, btn_get_note;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		btn_add_note = (Button) findViewById(R.id.btn_add_note);
		btn_get_note = (Button) findViewById(R.id.btn_get_note);

		btn_add_note.setOnClickListener(this);
		btn_get_note.setOnClickListener(this);

		new CheckBucketExists().execute();
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch (v.getId()) {
		case R.id.btn_add_note:

			startActivity(new Intent(this, NoteEdit.class));

			break;

		case R.id.btn_get_note:

			startActivity(new Intent(this, NoteList.class));

			break;

		default:
			break;
		}
	}

	private class CheckBucketExists extends AsyncTask<Object, Void, Boolean> {

		@Override
		protected Boolean doInBackground(Object... params) {
			AmazonS3Client sS3Client = Util
					.getS3Client(getApplicationContext());
			return Util.doesBucketExist();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (!result.booleanValue()) {
				new CreateBucket().execute();
			} else {
				System.out.println("exist");
			}
		}
	}

	private class CreateBucket extends AsyncTask<Object, Void, Boolean> {

		@Override
		protected Boolean doInBackground(Object... params) {
			AmazonS3Client sS3Client = Util
					.getS3Client(getApplicationContext());
			if (!Util.doesBucketExist()) {
				Util.createBucket();
				return true;
			}
			return false;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (!result) {
				Toast.makeText(getApplicationContext(),
						"Bucket already exists", Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(getApplicationContext(),
						"Bucket successfully created!", Toast.LENGTH_SHORT)
						.show();
			}
		}
	}
}

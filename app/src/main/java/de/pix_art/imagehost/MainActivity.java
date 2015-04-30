package de.pix_art.imagehost;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.graphics.Matrix;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

@SuppressLint("NewApi")

public class MainActivity extends ActionBarActivity {
    private static final String TAG = "EXIF:";
    static int w = 1920;
    static int h = 1920;
    private static int RESULT_LOAD_IMG = 1;
    ProgressDialog prgDialog;
    String encodedString;
    RequestParams params = new RequestParams();
    String imgPath, fileName;
    Bitmap bitmap;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prgDialog = new ProgressDialog(this);
        // Set Cancelable as False
        prgDialog.setCancelable(false);

	try {
	    Intent intent = getIntent();
	    String action = intent.getAction();
	    String type = intent.getType();
	    if (Intent.ACTION_SEND.equals(action) && type != null && type.startsWith("image/")) {
		displayImageFromIntent(intent);
	    }
        } catch (Exception e) {
            Toast.makeText(this, R.string.fail, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager connMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = connMan.getActiveNetworkInfo();
        if (ni == null) {
            // There are no active networks.
            return false;
        } else
            return true;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent i = new Intent(this, PreferencesActivity.class);
                startActivity(i);
                return true;
            case R.id.info:
                break;
        }
        return true;
    }

    //exif rotation
    private int getExifOrientation(){
        ExifInterface exif;
        int orientation = 0;
        try {
            exif = new ExifInterface( imgPath );
            orientation = exif.getAttributeInt( ExifInterface.TAG_ORIENTATION, 1 );
        } catch ( IOException e ) {
            e.printStackTrace();
        }
        Log.d(TAG, "got orientation " + orientation);
        return orientation;
    }

    // get rotation
    private int getBitmapRotation() {
        int rotation = 0;
        switch ( getExifOrientation() ) {
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotation = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotation = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotation = 270;
                break;
        }

        return rotation;
    }

    public void displayImageFromIntent(Intent data) {
	// Get the Image from data
	Uri selectedImage = data.getData();
	String[] filePathColumn = { MediaStore.Images.Media.DATA };

	if (selectedImage == null)
	    selectedImage = data.getParcelableExtra(Intent.EXTRA_STREAM);

	// Get the cursor
	Cursor cursor = getContentResolver().query(selectedImage,
		filePathColumn, null, null, null);
	// Move to first row
	cursor.moveToFirst();

	int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
	imgPath = cursor.getString(columnIndex);
	cursor.close();
        
	ImageView imgView = (ImageView) findViewById(R.id.imgView);
	// Set the Image in ImageView
	BitmapFactory.Options imageview = null;
	imageview = new BitmapFactory.Options();
    // create a matrix for the manipulation
    Matrix matrix = new Matrix();
    // rotate the Bitmap
    matrix.postRotate(getBitmapRotation());
    // resize image
	imageview.inSampleSize = 2;
	Bitmap newimageview = BitmapFactory.decodeFile(imgPath, imageview);
    // recreate the new Bitmap
    Bitmap rotatedBitmap = Bitmap.createBitmap(newimageview, 0, 0, newimageview.getWidth(), newimageview.getHeight(), matrix, true);
	imgView.setImageBitmap(rotatedBitmap);

	// Get the Image's file name
	String fileNameSegments[] = imgPath.split("/");
	fileName = fileNameSegments[fileNameSegments.length - 1];
	// Put file name in Async Http Post Param which will used in Php web app
	//params.put("filename", fileName);

	if (isNetworkConnected()){
	// immediately upload the image
	uploadImage(null);
    } else {
        Toast.makeText(this, R.string.no_connection,
                Toast.LENGTH_LONG).show();
    }
    }

    public void loadImagefromGallery(View view) {
            // Create intent to Open Image applications like Gallery, Google Photos
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            // Start the Intent
            startActivityForResult(galleryIntent, RESULT_LOAD_IMG);
    }
    
    // When Image is selected from Gallery
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            // When an Image is picked
            if (requestCode == RESULT_LOAD_IMG && resultCode == RESULT_OK
                    && null != data) {
		displayImageFromIntent(data);
            } else {
                Toast.makeText(this, R.string.no_image,
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.fail, Toast.LENGTH_LONG).show();
        }

    }

    // When Upload button is clicked
    public void uploadImage(View v) {
        // When Image is selected from Gallery
        if (imgPath != null && !imgPath.isEmpty()) {
            prgDialog.setMessage(getString(R.string.preparing_image));
            prgDialog.show();
            // Convert image to String using Base64
            encodeImagetoString();
            // When Image is not selected from Gallery
        } else {
            Toast.makeText(
                    getApplicationContext(),
                    R.string.choose_image,
                    Toast.LENGTH_LONG).show();
        }
    }

    // AsyncTask - To convert Image to String
    public void encodeImagetoString() {
        new AsyncTask<Void, Void, String>() {

            protected void onPreExecute() {

            };

            @Override
            protected String doInBackground(Void... params) {
                BitmapFactory.Options options = null;
                options = new BitmapFactory.Options();
                int imageHeight = options.outHeight; // get the height
                int imageWidth = options.outWidth; // get the width

                options.inJustDecodeBounds = true;
                Bitmap bm = BitmapFactory.decodeFile(imgPath, options);

                int heightRatio = (int)Math.ceil(options.outHeight/(float)h);
                int widthRatio = (int)Math.ceil(options.outWidth/(float)w);

                if (heightRatio > 1 || widthRatio > 1)
                {
                    if (heightRatio > widthRatio){
                        options.inSampleSize = heightRatio;
                        } else {
                        options.inSampleSize = widthRatio;
                        }
                    }

                options.inJustDecodeBounds = false;

                // create a matrix for the manipulation
                Matrix matrix = new Matrix();
                // rotate the Bitmap
                matrix.postRotate(getBitmapRotation());
                Bitmap newbm = BitmapFactory.decodeFile(imgPath, options);
                // recreate the new Bitmap
                Bitmap rotatedBm = Bitmap.createBitmap(newbm , 0, 0, newbm .getWidth(), newbm .getHeight(), matrix, true);

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                // Must compress the Image to reduce image size to make upload easy
                rotatedBm.compress(Bitmap.CompressFormat.JPEG, 75, stream);
                byte[] byte_arr = stream.toByteArray();
                // Encode Image to String
                encodedString = Base64.encodeToString(byte_arr, 0);
                return "";
            }

            @Override
            protected void onPostExecute(String msg) {
                prgDialog.setMessage(getString(R.string.uploading));
                // Put converted Image string into Async Http Post param
                params.put("image", encodedString);
                // Trigger Image upload
                triggerImageUpload();
            }
        }.execute(null, null, null);
    }

    public void triggerImageUpload() {
        makeHTTPCall();
    }

    // Make Http call to upload Image to Php server
    public void makeHTTPCall() {
        prgDialog.setMessage(getString(R.string.convert_image));
        AsyncHttpClient client = new AsyncHttpClient();
        // Don't forget to change the IP address to your LAN address. Port no as well.
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String prefServer = SP.getString("pref_server", "http://xmpp.pix-art.de/imagehost");
        client.post(prefServer+"/upload_image.php",
                params, new AsyncHttpResponseHandler() {
                    // When the response returned by REST has Http
                    // response code '200'
                    @Override
                    public void onSuccess(String response) {
                        // Hide Progress Dialog
                        prgDialog.hide();
                        //Toast.makeText(getApplicationContext(), response,
                        //        Toast.LENGTH_LONG).show();

			// put URL into clipboard
			ClipboardManager cm = (ClipboardManager)MainActivity.this.getSystemService(CLIPBOARD_SERVICE);
			cm.setPrimaryClip(ClipData.newPlainText(fileName, response));

                        // share link
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, response);
                        sendIntent.setType("text/plain");
                        startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_to)));
                        // share link via button
                        finish();
                        System.exit(0);

                    }

                    // When the response returned by REST has Http
                    // response code other than '200' such as '404',
                    // '500' or '403' etc
                    @Override
                    public void onFailure(int statusCode, Throwable error,
                                          String content) {
                        // Hide Progress Dialog
                        prgDialog.hide();
                        // When Http response code is '404'
                        if (statusCode == 404) {
                            Toast.makeText(getApplicationContext(),
                                    R.string.error_404,
                                    Toast.LENGTH_LONG).show();
                        }
                        // When Http response code is '500'
                        else if (statusCode == 500) {
                            Toast.makeText(getApplicationContext(),
                                    R.string.error_500,
                                    Toast.LENGTH_LONG).show();
                        }
                        // When Http response code other than 404, 500
                        else {
                            Toast.makeText(getApplicationContext(),
                                    R.string.error
                                            + statusCode, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
    	finish();
        System.exit(0);
        // TODO Auto-generated method stub
        super.onDestroy();
        // Dismiss the progress bar when application is closed
        if (prgDialog != null) {
            prgDialog.dismiss();
        }
    }
}

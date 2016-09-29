package com.alderferstudios.learnbyimages;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import com.clarifai.api.ClarifaiClient;
import com.clarifai.api.RecognitionRequest;
import com.clarifai.api.RecognitionResult;
import com.clarifai.api.Tag;
import com.clarifai.api.exception.ClarifaiException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.memetix.mst.language.Language;
import com.memetix.mst.translate.Translate;

/**
 * @author Ben Alderfer, Shashank Gangadhara
 */
public class MainActivity extends AppCompatActivity {

    //Clarifai variables
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int CODE_PICK = 1;
    private final ClarifaiClient client = new ClarifaiClient(Credentials.CLIENT_ID,
            Credentials.CLIENT_SECRET);

    //language spinners and adapter
    private Spinner firstLanguageSpinner, secondLanguageSpinner;
    private ArrayAdapter<String> languageAdapter;

    //tags returned from Clarifai
    String tags[] = new String[5];
    String translatedTags[] = new String[5];

    //selected languages
    com.memetix.mst.language.Language firstLanguage, secondLanguage;

    //misc. views
    private ImageView imageView;
    private TextView tagWordsView, translatedWordsView;

    /**
     * Initial setup
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //lock orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        imageView = (ImageView) findViewById(R.id.image);
        findViewById(R.id.imageCard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Send an intent to launch the media picker.
                final Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, CODE_PICK);
            }
        });

        firstLanguageSpinner = (Spinner) findViewById(R.id.firstLanguageSpinner);
        secondLanguageSpinner = (Spinner) findViewById(R.id.secondLanguageSpinner);

        //set the items in the spinners
        initializeSpinners();

        //set item of first spinner to English
        firstLanguageSpinner.setSelection(1);

        //set item of first spinner to French
        secondLanguageSpinner.setSelection(2);

        tagWordsView = (TextView) findViewById(R.id.tagWords);
        translatedWordsView = (TextView) findViewById(R.id.translatedWords);
    }

    /**
     * Sets the items in the meal option dropdown menu
     */
    private void initializeSpinners() {
        String[] items = new String[]{getString(R.string.language1), getString(R.string.language2),
                getString(R.string.language3), getString(R.string.language4),
                getString(R.string.language5), getString(R.string.language6),
                getString(R.string.language7), getString(R.string.language8),
                getString(R.string.language9)};
        languageAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, items);
        firstLanguageSpinner.setAdapter(languageAdapter);
        secondLanguageSpinner.setAdapter(languageAdapter);
    }

    /**
     * Handle result from choosing an image
     * @param requestCode
     * @param resultCode
     * @param intent
     */
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == CODE_PICK && resultCode == RESULT_OK) {
            // The user picked an image. Send it to Clarifai for recognition.
            Log.d(TAG, "User picked image: " + intent.getData());
            Bitmap bitmap = loadBitmapFromUri(intent.getData());
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                tagWordsView.setText("Recognizing...");

                try {
                    ExifInterface exif = new ExifInterface(getRealPathFromURI(intent.getData()));
                    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

                    Matrix matrix = new Matrix();
                    switch (orientation) {
                        case ExifInterface.ORIENTATION_ROTATE_90:
                            matrix.postRotate(90);
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_180:
                            matrix.postRotate(180);
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_270:
                            matrix.postRotate(270);
                            break;
                        default:
                            break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //save selected languages
                switch (firstLanguageSpinner.getSelectedItemPosition()) {
                    case 0: firstLanguage = Language.CHINESE_SIMPLIFIED; break;
                    case 1: firstLanguage = Language.ENGLISH; break;
                    case 2: firstLanguage = Language.FRENCH; break;
                    case 3: firstLanguage = Language.HINDI; break;
                    case 4: firstLanguage = Language.ITALIAN; break;
                    case 5: firstLanguage = Language.JAPANESE; break;
                    case 6: firstLanguage = Language.KOREAN; break;
                    case 7: firstLanguage = Language.RUSSIAN; break;
                    case 8: firstLanguage = Language.SPANISH; break;
                }
                switch (secondLanguageSpinner.getSelectedItemPosition()) {
                    case 0: secondLanguage = Language.CHINESE_SIMPLIFIED; break;
                    case 1: secondLanguage = Language.ENGLISH; break;
                    case 2: secondLanguage = Language.FRENCH; break;
                    case 3: secondLanguage = Language.HINDI; break;
                    case 4: secondLanguage = Language.ITALIAN; break;
                    case 5: secondLanguage = Language.JAPANESE; break;
                    case 6: secondLanguage = Language.KOREAN; break;
                    case 7: secondLanguage = Language.RUSSIAN; break;
                    case 8: secondLanguage = Language.SPANISH; break;
                }

                // Run recognition on a background thread since it makes a network call.
                new AsyncTask<Bitmap, Void, RecognitionResult>() {
                    @Override protected RecognitionResult doInBackground(Bitmap... bitmaps) {
                        return recognizeBitmap(bitmaps[0]);
                    }
                    @Override protected void onPostExecute(RecognitionResult result) {
                        //display the tags
                        updateUIForResult(result);
                        //translate the tags
                        new TranslateAsyncTask() {
                            protected void onPostExecute(Boolean result) {
                                //build the displayed string
                                String translatedWords = "";
                                for (int i = 0; i < translatedTags.length; ++i) {
                                    translatedWords += (i + 1) + ") " + translatedTags[i];
                                    //do not add new line to last entry
                                    if (i < tags.length - 1) {
                                        translatedWords += '\n';
                                    }
                                }
                                translatedWordsView.setText(translatedWords);
                            }
                        }.execute();
                    }
                }.execute(bitmap);
            } else {
                tagWordsView.setText("Unable to load selected image.");
            }
        }
    }

    /**
     * Loads a Bitmap from a content URI returned by the media picker
     * @param uri
     * @return
     */
    private Bitmap loadBitmapFromUri(Uri uri) {
        try {
            // The image may be large. Load an image that is sized for display. This follows best
            // practices from http://developer.android.com/training/displaying-bitmaps/load-bitmap.html
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, opts);
            int sampleSize = 1;
            while (opts.outWidth / (2 * sampleSize) >= imageView.getWidth() &&
                    opts.outHeight / (2 * sampleSize) >= imageView.getHeight()) {
                sampleSize *= 2;
            }

            opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            return BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, opts);
        } catch (IOException e) {
            Log.e(TAG, "Error loading image: " + uri, e);
        }
        return null;
    }

    /**
     * Sends the given bitmap to Clarifai for recognition and returns the result
     * @param bitmap
     * @return
     */
    private RecognitionResult recognizeBitmap(Bitmap bitmap) {
        try {
            // Scale down the image. This step is optional. However, sending large images over the
            // network is slow and  does not significantly improve recognition performance.
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 320,
                    320 * bitmap.getHeight() / bitmap.getWidth(), true);

            // Compress the image as a JPEG.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out);
            byte[] jpeg = out.toByteArray();

            // Send the JPEG to Clarifai and return the result.
            return client.recognize(new RecognitionRequest(jpeg)).get(0);
        } catch (ClarifaiException e) {
            Log.e(TAG, "Clarifai error", e);
            return null;
        }
    }

    /**
     * Updates the UI by displaying tags for the given result
     * @param result
     */
    private void updateUIForResult(RecognitionResult result) {
        if (result != null) {
            if (result.getStatusCode() == RecognitionResult.StatusCode.OK) {

                //only save top 5 tags
                int count = 0;
                for (Tag tag : result.getTags()) {

                    tags[count] = tag.getName();
                    ++count;
                    if (count >= 5) {
                        break;
                    }
                }

                //build the displayed string
                String taggedWords = "";
                for (int i = 0; i < tags.length; ++i) {
                    taggedWords += (i + 1) + ") " + tags[i];
                    //do not add new line to last entry
                    if (i < tags.length - 1) {
                        taggedWords += '\n';
                    }
                }
                tagWordsView.setText(taggedWords);
            } else {
                Log.e(TAG, "Clarifai: " + result.getStatusMessage());
                tagWordsView.setText("Sorry, there was an error recognizing your image.");
            }
        } else {
            tagWordsView.setText("Sorry, there was an error recognizing your image.");
        }
    }

    /**
     * Gets the real String path from a URI
     * @param contentURI the URI to get the path from
     * @return the String path
     */
    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    /**
     * AsyncTask for getting translated tags
     */
    class TranslateAsyncTask extends AsyncTask<Void, Integer, Boolean> {
        @Override
        protected Boolean doInBackground(Void... arg0) {
            String clientID = "Learn-By-Images";
            String clientSecret = "2kmN37Aa4xgj1OjhhVpxOLvkS9Zo5kzR5dmG5Ldi8Rw";
            Translate.setClientId(clientID);
            Translate.setClientSecret(clientSecret);

            //HttpClient client = new DefaultHttpClient();
            //HttpPost post = new HttpPost(address);

            try {
                /*for (int i = 0; i < tags.length; ++i) {
                    String uri = "http://api.microsofttranslator.com/v2/Http.svc/Translate?text=" + System.Web.HttpUtility.UrlEncode(text) + "&from=" + firstLanguage + "&to=" + secondLanguage;

                    string authToken = "Bearer" + " " + admToken.access_token;

                    HttpWebRequest httpWebRequest = (HttpWebRequest)WebRequest.Create(uri);
                    httpWebRequest.Headers.Add("Authorization", authToken);

                    translatedTags[i] = Translate.execute(tags[i], secondLanguage);
                }*/
                translatedTags[0] = "chien";
                translatedTags[1] = "animal de compagnie";
                translatedTags[2] = "mignon";
                translatedTags[3] = "mammifère";
                translatedTags[4] = "canine";

            } catch(Exception e) {
                Snackbar.make(findViewById(R.id.contentArea), e.toString(), Snackbar.LENGTH_LONG).show();
                Log.e("translate error", e.toString());
            }
            return true;
        }
    }
}

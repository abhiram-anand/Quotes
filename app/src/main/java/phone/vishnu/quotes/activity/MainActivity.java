package phone.vishnu.quotes.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.turkialkhateeb.materialcolorpicker.ColorChooserDialog;
import com.turkialkhateeb.materialcolorpicker.ColorListener;
import com.yalantis.ucrop.UCrop;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import phone.vishnu.quotes.R;
import phone.vishnu.quotes.data.QuoteData;
import phone.vishnu.quotes.data.QuoteListAsyncResponse;
import phone.vishnu.quotes.fragment.AboutFragment;
import phone.vishnu.quotes.fragment.BottomSheetFragment;
import phone.vishnu.quotes.fragment.FavoriteFragment;
import phone.vishnu.quotes.fragment.FontFragment;
import phone.vishnu.quotes.fragment.PickFragment;
import phone.vishnu.quotes.fragment.QuoteFragment;
import phone.vishnu.quotes.helper.ExportHelper;
import phone.vishnu.quotes.helper.QuoteViewPagerAdapter;
import phone.vishnu.quotes.helper.SharedPreferenceHelper;
import phone.vishnu.quotes.model.Quote;
import phone.vishnu.quotes.receiver.NotificationReceiver;

public class MainActivity extends AppCompatActivity implements BottomSheetFragment.BottomSheetListener {

    public static ProgressDialog bgDialog, fontDialog;
    private SharedPreferenceHelper sharedPreferenceHelper;
    private ExportHelper exportHelper;
    private int PICK_IMAGE_ID = 36;
    private int PERMISSION_REQ_CODE = 88;
    private ConstraintLayout constraintLayout;
    private QuoteViewPagerAdapter adapter;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferenceHelper = new SharedPreferenceHelper(this);
        exportHelper = new ExportHelper(this);

        if (savedInstanceState == null) {
            final Bundle extras = getIntent().getExtras();
            if (extras != null && extras.getBoolean("NotificationClick")) {

                if (extras.getBoolean("ShareButton")) {

                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                shareScreenshot(extras.getString("quote"), extras.getString("author"));
                            } catch (Exception e) {
                                FirebaseCrashlytics.getInstance().recordException(e);
                                e.printStackTrace();
                            }
                        }
                    });
                } else if (extras.getBoolean("FavButton")) {


                    Gson gson = new Gson();
                    String jsonSaved = sharedPreferenceHelper.getFavoriteArrayString();
                    String jsonNewProductToAdd = gson.toJson(new Quote(extras.getString("quote"), extras.getString("author")));

                    Type type = new TypeToken<ArrayList<Quote>>() {
                    }.getType();
                    ArrayList<Quote> productFromShared = gson.fromJson(jsonSaved, type);

                    sharedPreferenceHelper.setFavoriteArrayString(String.valueOf(addFavorite(jsonSaved, jsonNewProductToAdd, productFromShared, extras.getString("quote"))));
                }
            }
        }

        setContentView(R.layout.activity_main);
        constraintLayout = findViewById(R.id.constraintLayout);

        if (!isNetworkAvailable())
            Toast.makeText(this, "Please Connect to the Internet...", Toast.LENGTH_SHORT).show();

        final String backgroundPath = sharedPreferenceHelper.getBackgroundPath();

        if (!"-1".equals(backgroundPath))
            constraintLayout.setBackground(Drawable.createFromPath(backgroundPath));
        else {

            Toast.makeText(this, "Choose a background", Toast.LENGTH_LONG).show();

            if (!isPermissionGranted())
                isPermissionGranted();
            else {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(MainActivity.this, R.style.AlertDialogTheme);

                builder.setTitle("Choose a Background");
                builder.setCancelable(false);

                final String[] items = {"Plain Colour", "Image From Gallery", "Default Images"};
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: {
                                ColorChooserDialog colorChooserDialog = new ColorChooserDialog(MainActivity.this);
                                colorChooserDialog.setTitle("Choose Color");
                                colorChooserDialog.setColorListener(new ColorListener() {
                                    @Override
                                    public void OnColorClick(View v, int color) {

                                        DisplayMetrics metrics = new DisplayMetrics();
                                        getWindowManager().getDefaultDisplay().getMetrics(metrics);

                                        Bitmap image = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888);
                                        Canvas canvas = new Canvas(image);
                                        canvas.drawColor(color);

                                        exportHelper.exportBackgroundImage(image);

                                        constraintLayout.setBackground(Drawable.createFromPath(sharedPreferenceHelper.getBackgroundPath()));

                                    }
                                });
                                colorChooserDialog.show();
                                break;
                            }
                            case 1: {
                                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                                intent.setType("image/*");
                                startActivityForResult(intent, PICK_IMAGE_ID);
                                break;
                            }
                            case 2: {
                                bgDialog = ProgressDialog.show(MainActivity.this, "", "Please Wait....");
                                bgDialog.setCancelable(false);
                                getSupportFragmentManager().beginTransaction().add(R.id.constraintLayout, PickFragment.newInstance()).addToBackStack(null).commit();
                                break;
                            }
                            default: {
                                break;
                            }
                        }
                    }
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.setCancelable(false);

                alertDialog.getListView().setOnHierarchyChangeListener(
                        new ViewGroup.OnHierarchyChangeListener() {
                            @Override
                            public void onChildViewAdded(View parent, View child) {
                                CharSequence text = ((TextView) child).getText();
                                int itemIndex = Arrays.asList(items).indexOf(text);
                                if ((itemIndex == 2) && !isNetworkAvailable()) {
                                    child.setEnabled(false);
                                    child.setOnClickListener(null);
                                }
                            }

                            @Override
                            public void onChildViewRemoved(View view, View view1) {
                            }
                        });
                alertDialog.show();
            }
        }

        ImageView menuIcon = findViewById(R.id.homeMenuIcon);
        menuIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BottomSheetFragment bottomSheet = new BottomSheetFragment();
                bottomSheet.show(getSupportFragmentManager(), "bottomSheetTag");
            }
        });

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                adapter = new QuoteViewPagerAdapter(getSupportFragmentManager(), getFragments());
                ((ViewPager) findViewById(R.id.viewPager)).setAdapter(adapter);
            }
        });

        String s = sharedPreferenceHelper.getAlarmString();

//        if (true) {
        if ("At 08:30 Daily".equals(s)) {
            myAlarm();
        }
    }

    private void myAlarm() {

        Calendar calendar = Calendar.getInstance();
//        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 8);
        calendar.set(Calendar.MINUTE, 30);

        if (calendar.getTime().compareTo(new Date()) < 0)
            calendar.add(Calendar.DAY_OF_MONTH, 1);

        Intent intent = new Intent(getApplicationContext(), NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }

    private List<Fragment> getFragments() {

        final List<Fragment> fragments = new ArrayList<>();
        new QuoteData().getQuotes(new QuoteListAsyncResponse() {
            @Override
            public void processFinished(ArrayList<Quote> quotes) {

                Collections.shuffle(quotes);

                for (int i = 0; i < quotes.size(); i++) {
                    QuoteFragment quoteFragment = QuoteFragment.newInstance(quotes.get(i).getQuote(), quotes.get(i).getAuthor());
                    fragments.add(quoteFragment);
                }

                adapter.notifyDataSetChanged();
            }
        });
        return fragments;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && data != null) {

            String file = exportHelper.getBGPath();

            if (requestCode == PICK_IMAGE_ID)

                UCrop.of(data.getData(), Uri.fromFile(new File(file)))
                        .withAspectRatio(1080, 1920)
                        .withMaxResultSize(640, 960)
                        .start(this);

            else if (requestCode == UCrop.REQUEST_CROP) {
                constraintLayout.setBackground(Drawable.createFromPath(file));
                sharedPreferenceHelper.setBackgroundPath(file);
            }
        } else Toast.makeText(this, "Error...", Toast.LENGTH_SHORT).show();


    }

    @Override
    public void onBottomSheetButtonClicked(int id) {
        switch (id) {
            case R.id.bottomSheetFav: {
                FavoriteFragment fragment = FavoriteFragment.newInstance();
                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.beginTransaction()
                        .add(R.id.constraintLayout, fragment)
                        .addToBackStack(null)
                        .commit();
                break;
            }
            case R.id.bottomSheetAbout: {
                AboutFragment fragment = AboutFragment.newInstance();
                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.beginTransaction()
                        .add(R.id.constraintLayout, fragment)
                        .addToBackStack(null)
                        .commit();
                break;
            }
            case R.id.bottomSheetImageChooser: {
                if (!isPermissionGranted())
                    isPermissionGranted();
                else {
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(MainActivity.this, R.style.AlertDialogTheme);

                    final String[] items = {"Plain Colour", "Image From Gallery", "Default Images"};
                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                                case 0: {
                                    ColorChooserDialog colorChooserDialog = new ColorChooserDialog(MainActivity.this);
                                    colorChooserDialog.setTitle("Choose Color");
                                    colorChooserDialog.setColorListener(new ColorListener() {
                                        @Override
                                        public void OnColorClick(View v, int color) {

                                            DisplayMetrics metrics = new DisplayMetrics();
                                            getWindowManager().getDefaultDisplay().getMetrics(metrics);

                                            Bitmap image = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888);
                                            Canvas canvas = new Canvas(image);
                                            canvas.drawColor(color);

                                            exportHelper.exportBackgroundImage(image);

                                            constraintLayout.setBackground(Drawable.createFromPath(sharedPreferenceHelper.getBackgroundPath()));

                                        }
                                    });
                                    colorChooserDialog.show();
                                    break;
                                }
                                case 1: {
                                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                                    intent.setType("image/*");
                                    startActivityForResult(intent, PICK_IMAGE_ID);
                                    break;
                                }
                                case 2: {
                                    bgDialog = ProgressDialog.show(MainActivity.this, "", "Please Wait....");
                                    getSupportFragmentManager().beginTransaction().add(R.id.constraintLayout, PickFragment.newInstance()).addToBackStack(null).commit();
                                    break;
                                }
                                default: {
                                    break;
                                }
                            }
                        }
                    });
                    AlertDialog alertDialog = builder.create();

                    alertDialog.getListView().setOnHierarchyChangeListener(
                            new ViewGroup.OnHierarchyChangeListener() {
                                @Override
                                public void onChildViewAdded(View parent, View child) {
                                    CharSequence text = ((TextView) child).getText();
                                    int itemIndex = Arrays.asList(items).indexOf(text);
                                    if ((itemIndex == 2) && !isNetworkAvailable()) {
                                        child.setEnabled(false);
                                        child.setOnClickListener(null);
                                    }
                                }

                                @Override
                                public void onChildViewRemoved(View view, View view1) {
                                }
                            });
                    alertDialog.show();
                }
                break;
            }
            case R.id.bottomSheetColorChooser: {
                final String COLOR_PREFERENCE_NAME = "colorPreference";

                final ColorChooserDialog dialog = new ColorChooserDialog(MainActivity.this);
                dialog.setTitle("Choose Color");
                dialog.setColorListener(new ColorListener() {
                    @Override
                    public void OnColorClick(View v, int color) {

                        String colorString = Integer.toHexString(color).substring(2);

                        //TODO:Needs Fixing of string "WHITE"
                        if (colorString.toLowerCase().equals("ffffff")) colorString = "00000000";


                        sharedPreferenceHelper.setColorPreference("#" + colorString);
                        Toast.makeText(MainActivity.this, "Accent Colour Set...", Toast.LENGTH_LONG).show();

                        adapter.notifyDataSetChanged();

                        dialog.dismiss();

                    }
                });
                dialog.show();
                break;
            }
            case R.id.bottomSheetFont: {
                fontDialog = ProgressDialog.show(MainActivity.this, "", "Please Wait....");
                getSupportFragmentManager().beginTransaction().add(R.id.constraintLayout, FontFragment.newInstance()).addToBackStack(null).commit();
                break;
            }
        }
    }

    private void showPermissionDeniedDialog() {

        final AlertDialog.Builder builder =
                new AlertDialog.Builder(MainActivity.this, R.style.AlertDialogTheme);
        builder.setTitle("Permission Denied");
        builder.setMessage("Please Accept Permission to Capture Screenshot of the Screen");
        builder.setCancelable(true);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQ_CODE);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();

    }

    private boolean isPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 22) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    showPermissionDeniedDialog();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQ_CODE);
                }
            } else {
                return true;
            }
        }
        return false;
    }

    private void shareScreenshot(String quote, String author) {

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        @SuppressLint("InflateParams") View shareView = inflater.inflate(R.layout.share_layout, null);

        String hexColor = sharedPreferenceHelper.getColorPreference();
        String fontPath = sharedPreferenceHelper.getFontPath();

        String backgroundPath = sharedPreferenceHelper.getBackgroundPath();
        if (!"-1".equals(backgroundPath))
            shareView.findViewById(R.id.shareRelativeLayout).setBackground(Drawable.createFromPath(backgroundPath));

        CardView cardView = shareView.findViewById(R.id.shareCardView);
        cardView.setCardBackgroundColor(Color.parseColor(hexColor));

//        ((ImageView) shareView.findViewById(R.id.shareFavoriteImageView)).setColorFilter(Color.RED);
//        ((ImageView) shareView.findViewById(R.id.shareShareImageView)).setColorFilter(Color.GREEN);

        if (!fontPath.equals("-1")) {
            Typeface face = Typeface.createFromFile(fontPath);
            ((TextView) shareView.findViewById(R.id.shareQuoteTextView)).setTypeface(face);
        }

        ((TextView) shareView.findViewById(R.id.shareQuoteTextView)).setText(quote);
        ((TextView) shareView.findViewById(R.id.shareAuthorTextView)).setText(author);

        DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(metrics);

        shareView.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY));

        shareView.findViewById(R.id.shareRelativeLayout).setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ConstraintLayout.LayoutParams cardParams = new ConstraintLayout.LayoutParams(300, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.verticalBias = 0.5f;
        cardParams.horizontalBias = 0.5f;

        shareView.setDrawingCacheEnabled(true);

        Bitmap bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888);

        Canvas c = new Canvas(bitmap);
        shareView.layout(0, 0, metrics.widthPixels, metrics.heightPixels);
        shareView.draw(c);

        shareView.buildDrawingCache(true);

        File root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Quotes");
        if (!root.exists()) root.mkdirs();
        String imagePath = root.toString() + File.separator + ".Screenshot" + ".jpg";
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(imagePath);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().recordException(e);
            e.printStackTrace();
        }

        Uri uri = FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", new File(imagePath));
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("image/*");
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);

        startActivity(Intent.createChooser(sharingIntent, "Share via"));
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public QuoteViewPagerAdapter getQuoteViewPagerAdapter() {
        return adapter;
    }

    private JSONArray addFavorite(String jsonSaved, String jsonNewProductToAdd, ArrayList<Quote> productFromShared, String quote) {
        JSONArray jsonArrayProduct = new JSONArray();
        try {
            if (jsonSaved.length() != 0) {
                if (!isPresent(productFromShared, quote)) {
                    jsonArrayProduct = new JSONArray(jsonSaved);
                    jsonArrayProduct.put(new JSONObject(jsonNewProductToAdd));
                }
            } else {
                productFromShared = new ArrayList<>();
            }
        } catch (JSONException e) {
            FirebaseCrashlytics.getInstance().recordException(e);
            e.printStackTrace();
        }
        return jsonArrayProduct;
    }

    private boolean isPresent(ArrayList<Quote> productFromShared, String quote) {
        boolean isPresent = false;
        for (int i = 0; i < productFromShared.size(); i++) {
            if (productFromShared.get(i).getQuote().trim().toLowerCase().equals(quote.trim().toLowerCase())) {
                isPresent = true;
            }
        }
        return isPresent;
    }
}

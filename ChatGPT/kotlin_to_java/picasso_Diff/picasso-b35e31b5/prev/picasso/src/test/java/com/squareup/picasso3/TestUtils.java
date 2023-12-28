package com.squareup.picasso3;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.Drawable;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.IBinder;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Photo;
import android.util.TypedValue;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RemoteViews;

import com.squareup.picasso3.BitmapHunterTest.TestableBitmapHunter;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Picasso.Priority;
import com.squareup.picasso3.Picasso.RequestTransformer;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.RequestHandler.Result.Bitmap;

import okhttp3.Call;
import okhttp3.Response;
import okio.Timeout;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TestUtils {
    public static final Answer TRANSFORM_REQUEST_ANSWER = new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            return invocation.getArguments()[0];
        }
    };
    public static final Uri URI_1 = Uri.parse("http:");
    public static final Uri URI_2 = Uri.parse("http:");
    public static final String STABLE_1 = "stableExampleKey1";
    public static final Request SIMPLE_REQUEST = new Request.Builder(URI_1).build();
    public static final String URI_KEY_1 = SIMPLE_REQUEST.key;
    public static final String URI_KEY_2 = new Request.Builder(URI_2).build().key;
    public static final String STABLE_URI_KEY_1 = new Request.Builder(URI_1).stableKey(STABLE_1).build().key;
    private static final File FILE_1 = new File("C:\\windows\\system32\\logo.exe");
    public static final String FILE_KEY_1 = new Request.Builder(Uri.fromFile(FILE_1)).build().key;
    public static final Uri FILE_1_URL = Uri.parse("file:");
    public static final Uri FILE_1_URL_NO_AUTHORITY = Uri.parse("file:/" + FILE_1.getParent());
    public static final Uri MEDIA_STORE_CONTENT_1_URL = Uri.parse("content:");
    public static final String MEDIA_STORE_CONTENT_KEY_1 = new Request.Builder(MEDIA_STORE_CONTENT_1_URL).build().key;
    public static final Uri CONTENT_1_URL = Uri.parse("content:");
    public static final String CONTENT_KEY_1 = new Request.Builder(CONTENT_1_URL).build().key;
    public static final Uri CONTACT_URI_1 = Contacts.CONTENT_URI.buildUpon().appendPath("1234").build();
    public static final String CONTACT_KEY_1 = new Request.Builder(CONTACT_URI_1).build().key;
    public static final Uri CONTACT_PHOTO_URI_1 = Contacts.CONTENT_URI.buildUpon().appendPath("123
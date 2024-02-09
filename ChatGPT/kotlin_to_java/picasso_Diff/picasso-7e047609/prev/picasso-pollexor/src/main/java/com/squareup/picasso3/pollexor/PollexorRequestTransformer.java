
package com.squareup.picasso3.pollexor;

import android.net.Uri;
import android.os.Build;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Request;
import com.squareup.pollexor.Thumbor;
import com.squareup.pollexor.ThumborUrlBuilder;
import com.squareup.pollexor.ThumborUrlBuilder.ImageFormat;

public class PollexorRequestTransformer implements Picasso.RequestTransformer {
    private final Thumbor thumbor;
    private final boolean alwaysTransform;
    private final Callback callback;

    public PollexorRequestTransformer(Thumbor thumbor) {
        this(thumbor, false, NONE);
    }

    public PollexorRequestTransformer(Thumbor thumbor, boolean alwaysTransform, Callback callback) {
        this.thumbor = thumbor;
        this.alwaysTransform = alwaysTransform;
        this.callback = callback;
    }

    @Override
    public Request transformRequest(Request request) {
        if (request.resourceId != 0) {
            return request;
        }
        Uri uri = request.uri;
        if (uri == null) {
            throw new NullPointerException("Null uri passed to " + getClass().getCanonicalName());
        }
        String scheme = uri.getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return request;
        }
        if (!request.hasSize() && !alwaysTransform) {
            return request;
        }
        Request.Builder newRequest = request.newBuilder();
        ThumborUrlBuilder urlBuilder = thumbor.buildImage(uri.toString());
        callback.configure(urlBuilder);
        if (request.hasSize()) {
            urlBuilder.resize(request.targetWidth, request.targetHeight);
            newRequest.clearResize();
        }
        if (request.centerInside) {
            urlBuilder.fitIn();
            newRequest.clearCenterInside();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            urlBuilder.filter(ThumborUrlBuilder.format(ImageFormat.WEBP));
        }
        newRequest.setUri(Uri.parse(urlBuilder.toUrl()));
        return newRequest.build();
    }

    public interface Callback {
        void configure(ThumborUrlBuilder builder);
    }

    private static final Callback NONE = new Callback() {
        @Override
        public void configure(ThumborUrlBuilder builder) {
            // Do nothing
        }
    };
}
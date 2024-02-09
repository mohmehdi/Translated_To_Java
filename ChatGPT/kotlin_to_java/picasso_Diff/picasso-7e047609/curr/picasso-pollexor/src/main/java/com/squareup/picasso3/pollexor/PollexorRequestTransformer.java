
package com.squareup.picasso3.pollexor;

import android.net.Uri;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.pollexor.PollexorRequestTransformer.Callback;
import com.squareup.pollexor.Thumbor;
import com.squareup.pollexor.ThumborUrlBuilder;
import com.squareup.pollexor.ThumborUrlBuilder.ImageFormat;

public class PollexorRequestTransformer implements Picasso.RequestTransformer {
    private final Thumbor thumbor;
    private final boolean alwaysTransform;
    private final Callback callback;

    public PollexorRequestTransformer(Thumbor thumbor, boolean alwaysTransform, Callback callback) {
        this.thumbor = thumbor;
        this.alwaysTransform = alwaysTransform;
        this.callback = callback;
    }

    public PollexorRequestTransformer(Thumbor thumbor, Callback callback) {
        this(thumbor, false, callback);
    }

    @Override
    public Request transformRequest(Request request) {
        if (request.getResourceId() != 0) {
            return request;
        }
        Uri uri = request.getUri();
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
            urlBuilder.resize(request.getTargetWidth(), request.getTargetHeight());
            newRequest.clearResize();
        }
        if (request.isCenterInside()) {
            urlBuilder.fitIn();
            newRequest.clearCenterInside();
        }
        urlBuilder.filter(ThumborUrlBuilder.format(ImageFormat.WEBP));
        newRequest.setUri(Uri.parse(urlBuilder.toUrl()));
        return newRequest.build();
    }

    public interface Callback {
        void configure(ThumborUrlBuilder builder);
    }

    private static final Callback NONE = new Callback() {
        @Override
        public void configure(ThumborUrlBuilder builder) {
        }
    };
}
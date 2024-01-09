package com.google.samples.apps.sunflower.adapters;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.data.UnsplashPhoto;
import com.google.samples.apps.sunflower.databinding.ListItemPhotoBinding;

public class GalleryAdapter extends PagingDataAdapter < UnsplashPhoto, GalleryAdapter.GalleryViewHolder > {

    private static class GalleryDiffCallback extends DiffUtil.ItemCallback < UnsplashPhoto > {
        @Override
        public boolean areItemsTheSame(UnsplashPhoto oldItem, UnsplashPhoto newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(UnsplashPhoto oldItem, UnsplashPhoto newItem) {
            return oldItem.equals(newItem);
        }
    }

    public GalleryAdapter() {
        super(new GalleryDiffCallback());
    }

    @NonNull
    @Override
    public GalleryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ListItemPhotoBinding binding = ListItemPhotoBinding.inflate(
            LayoutInflater.from(parent.getContext()),
            parent,
            false
        );
        return new GalleryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryViewHolder holder, int position) {
        UnsplashPhoto photo = getItem(position);
        if (photo != null) {
            holder.bind(photo);
        }
    }

    public static class GalleryViewHolder extends RecyclerView.ViewHolder {
        private final ListItemPhotoBinding binding;

        public GalleryViewHolder(ListItemPhotoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(view - > {
                UnsplashPhoto photo = binding.getPhoto();
                if (photo != null) {
                    Uri uri = Uri.parse(photo.getUser().getAttributionUrl());
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    view.getContext().startActivity(intent);
                }
            });
        }

        public void bind(UnsplashPhoto item) {
            binding.setPhoto(item);
            binding.executePendingBindings();
        }
    }
}
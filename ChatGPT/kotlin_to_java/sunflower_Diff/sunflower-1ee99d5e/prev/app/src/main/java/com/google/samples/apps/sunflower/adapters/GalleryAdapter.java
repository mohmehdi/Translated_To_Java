
package com.google.samples.apps.sunflower.adapters;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.samples.apps.sunflower.GalleryFragment;
import com.google.samples.apps.sunflower.data.UnsplashPhoto;
import com.google.samples.apps.sunflower.databinding.ListItemPhotoBinding;

public class GalleryAdapter extends PagingDataAdapter<UnsplashPhoto, GalleryAdapter.GalleryViewHolder> {

    public GalleryAdapter() {
        super(new GalleryDiffCallback());
    }

    @NonNull
    @Override
    public GalleryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new GalleryViewHolder(
                ListItemPhotoBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                )
        );
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

            binding.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (binding.getPhoto() != null) {
                        Uri uri = Uri.parse(binding.getPhoto().getUser().getAttributionUrl());
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        view.getContext().startActivity(intent);
                    }
                }
            });
        }

        public void bind(UnsplashPhoto item) {
            binding.setPhoto(item);
            binding.executePendingBindings();
        }
    }

    private static class GalleryDiffCallback extends DiffUtil.ItemCallback<UnsplashPhoto> {
        @Override
        public boolean areItemsTheSame(@NonNull UnsplashPhoto oldItem, @NonNull UnsplashPhoto newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull UnsplashPhoto oldItem, @NonNull UnsplashPhoto newItem) {
            return oldItem.equals(newItem);
        }
    }
}

package com.github.shadowsocks.subscription;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.shadowsocks.MainActivity;
import com.github.shadowsocks.R;
import com.github.shadowsocks.ToolbarFragment;
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment;
import com.github.shadowsocks.utils.readableMessage;
import com.github.shadowsocks.widget.ListHolderListener;
import com.github.shadowsocks.widget.MainListListener;
import com.github.shadowsocks.widget.UndoSnackbarManager;
import com.google.android.material.textfield.TextInputLayout;
import kotlinx.parcelize.Parcelize;

import java.net.MalformedURLException;
import java.net.URL;

public class SubscriptionFragment extends ToolbarFragment implements Toolbar.OnMenuItemClickListener {

    @Parcelize
    public static class SubItem implements Parcelable {
        public String item;

        public SubItem(String item) {
            this.item = item;
        }
    }

    @Parcelize
    public static class SubEditResult implements Parcelable {
        public String edited;
        public String replacing;

        public SubEditResult(String edited, String replacing) {
            this.edited = edited;
            this.replacing = replacing;
        }
    }

    public class SubDialogFragment extends AlertDialogFragment<SubItem, SubEditResult>
            implements TextWatcher, AdapterView.OnItemSelectedListener {

        private EditText editText;
        private TextInputLayout inputLayout;
        private final View positive;

        @Override
        protected void prepare(AlertDialog.Builder builder, DialogInterface.OnClickListener listener) {
            final MainActivity activity = requireActivity();
            @SuppressLint("InflateParams")
            final View view = activity.getLayoutInflater().inflate(R.layout.dialog_subscription, null);
            editText = view.findViewById(R.id.content);
            inputLayout = view.findViewById(R.id.content_layout);
            editText.setText(arg.item);
            editText.addTextChangedListener(this);
            builder.setTitle(R.string.edit_subscription)
                    .setPositiveButton(android.R.string.ok, listener)
                    .setNegativeButton(android.R.string.cancel, null);
            if (!arg.item.isEmpty()) {
                builder.setNeutralButton(R.string.delete, listener);
            }
            builder.setView(view);
        }

        @Override
        public void onStart() {
            super.onStart();
            validate();
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            validate(s);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            check(false);
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            validate();
        }

        private void validate(Editable value) {
            String message = "";
            positive.setEnabled(true);
            try {
                URL url = new URL(value.toString());
                if ("http".equalsIgnoreCase(url.getProtocol())) {
                    message = getString(R.string.cleartext_http_warning);
                }
            } catch (MalformedURLException e) {
                message = e.readableMessage;
                positive.setEnabled(false);
            }
            inputLayout.setError(message);
        }

        @Override
        public SubEditResult ret(int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    return new SubEditResult(editText.getText().toString(), arg.item);
                case DialogInterface.BUTTON_NEUTRAL:
                    return new SubEditResult(null, arg.item);
                default:
                    return null;
            }
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which != DialogInterface.BUTTON_NEGATIVE) {
                super.onClick(dialog, which);
            }
        }
    }

    private class SubViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        URL item;
        private final TextView text;

        public SubViewHolder(View view) {
            super(view);
            view.setFocusable(true);
            view.setOnClickListener(this);
            view.setBackgroundResource(R.drawable.background_selectable);
            text = view.findViewById(android.R.id.text1);
        }

        public void bind(URL url) {
            item = url;
            text.setText(url.toString());
        }

        @Override
        public void onClick(View v) {
            new SubDialogFragment().apply {
                arg(new SubItem(item.toString()));
                key();
            }.show(parentFragmentManager, null);
        }
    }

    private class SubscriptionAdapter extends RecyclerView.Adapter<SubViewHolder> {

        private final Subscription subscription = Subscription.getInstance();
        private boolean savePending = false;

        @Override
        public void onBindViewHolder(SubViewHolder holder, int i) {
            holder.bind(subscription.getUrls().get(i));
        }

        @Override
        public SubViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new SubViewHolder(LayoutInflater
                    .from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false));
        }

        @Override
        public int getItemCount() {
            return subscription.getUrls().size();
        }

        private void apply() {
            if (!savePending) {
                savePending = true;
                list.post(() -> {
                    Subscription.getInstance().setUrls(subscription.getUrls());
                    savePending = false;
                });
            }
        }

        public int add(URL url) {
            int old = subscription.getUrls().size();
            int index = subscription.getUrls().add(url);
            if (old != subscription.getUrls().size()) {
                notifyItemInserted(index);
                apply();
            }
            return index;
        }

        public void remove(int i) {
            undoManager.remove(new Pair<>(i, subscription.getUrls().get(i)));
            subscription.getUrls().removeItemAt(i);
            notifyItemRemoved(i);
            apply();
        }

        public void remove(URL item) {
            notifyItemRemoved(subscription.getUrls().indexOf(item));
            subscription.getUrls().remove(item);
            apply();
        }

        public void undo(List<Pair<Integer, Object>> actions) {
            for (Pair<Integer, Object> action : actions) {
                Object item = action.second;
                if (item instanceof URL) {
                    add((URL) item);
                }
            }
        }
    }

    private final SubscriptionAdapter adapter = new SubscriptionAdapter();
    private RecyclerView list;
    private ActionMode mode;
    private UndoSnackbarManager<URL> undoManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_subscriptions, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, ListHolderListener);
        AlertDialogFragment.setResultListener(this, (which, ret) -> {
            if (ret != null) {
                String edited = ret.edited;
                String replacing = ret.replacing;
                if (replacing != null) {
                    URL url = new URL(replacing);
                    adapter.remove(url);
                    if (which == DialogInterface.BUTTON_NEUTRAL) {
                        undoManager.remove(-1, url);
                    }
                }
                if (edited != null) {
                    int position = adapter.add(new URL(edited));
                    list.post(() -> list.scrollToPosition(position));
                }
            }
        });
        toolbar.setTitle(R.string.subscriptions);
        toolbar.inflateMenu(R.menu.subscription_menu);
        toolbar.setOnMenuItemClickListener(this);
        SubscriptionService.getIdle().observe(getViewLifecycleOwner(), it -> {
            toolbar.getMenu().findItem(R.id.action_update_subscription).setEnabled(it);
        });
        MainActivity activity = (MainActivity) requireActivity();
        list = view.findViewById(R.id.list);
        ViewCompat.setOnApplyWindowInsetsListener(list, MainListListener);
        list.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.VERTICAL, false));
        list.setItemAnimator(new DefaultItemAnimator());
        list.setAdapter(adapter);
        new FastScrollerBuilder(list).useMd2Style().build();
        undoManager = new UndoSnackbarManager<>(activity, adapter::undo);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START) {
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                adapter.remove(viewHolder.getBindingAdapterPosition());
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }
        }).attachToRecyclerView(list);
    }

    @Override
    public boolean onBackPressed() {
        if (mode != null) {
            mode.finish();
            return true;
        } else {
            return super.onBackPressed();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_manual_settings:
                new SubDialogFragment().apply {
                    arg(new SubItem());
                    key();
                }.show(parentFragmentManager, null);
                return true;
            case R.id.action_update_subscription:
                Context context = requireContext();
                context.startService(new Intent(context, SubscriptionService.class));
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDetach() {
        undoManager.flush();
        if (mode != null) {
            mode.finish();
        }
        super.onDetach();
    }
}
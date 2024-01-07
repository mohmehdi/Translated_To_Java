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

import java.net.MalformedURLException;
import java.net.URL;

public class SubscriptionFragment extends ToolbarFragment implements Toolbar.OnMenuItemClickListener {
    @SuppressLint("ParcelCreator")
    public static class SubItem implements Parcelable {
        private String item;

        public SubItem(String item) {
            this.item = item;
        }

        public String getItem() {
            return item;
        }
    }

    @SuppressLint("ParcelCreator")
    public static class SubEditResult implements Parcelable {
        private String edited;
        private String replacing;

        public SubEditResult(String edited, String replacing) {
            this.edited = edited;
            this.replacing = replacing;
        }

        public String getEdited() {
            return edited;
        }

        public String getReplacing() {
            return replacing;
        }
    }

    public static class SubDialogFragment extends AlertDialogFragment<SubItem, SubEditResult>
            implements TextWatcher, AdapterView.OnItemSelectedListener {
        private EditText editText;
        private TextInputLayout inputLayout;
        private View positive;

        @Override
        protected void prepareAlertDialog(AlertDialog.Builder builder, DialogInterface.OnClickListener listener) {
            final MainActivity activity = (MainActivity) requireActivity();
            final View view = activity.getLayoutInflater().inflate(R.layout.dialog_subscription, null);
            editText = view.findViewById(R.id.content);
            inputLayout = view.findViewById(R.id.content_layout);
            editText.setText(getArg().getItem());
            editText.addTextChangedListener(this);
            builder.setTitle(R.string.edit_subscription)
                    .setPositiveButton(android.R.string.ok, listener)
                    .setNegativeButton(android.R.string.cancel, null);
            if (!getArg().getItem().isEmpty()) {
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
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

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
            try {
                URL url = new URL(value.toString());
                if ("http".equalsIgnoreCase(url.getProtocol())) {
                    message = getString(R.string.cleartext_http_warning);
                }
                positive.setEnabled(true);
            } catch (MalformedURLException e) {
                message = e.getReadableMessage();
                positive.setEnabled(false);
            }
            inputLayout.setError(message);
        }

        @Override
        protected SubEditResult getResult(int which) {
            String edited = editText.getText().toString();
            String replacing = getArg().getItem();
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    return new SubEditResult(edited, replacing);
                case DialogInterface.BUTTON_NEUTRAL:
                    return new SubEditResult(null, replacing);
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
        private URL item;
        private TextView text;

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
            SubDialogFragment dialogFragment = new SubDialogFragment();
            dialogFragment.setArg(new SubItem(item.toString()));
            dialogFragment.setKey();
            dialogFragment.show(getParentFragmentManager(), null);
        }
    }

    private class SubscriptionAdapter extends RecyclerView.Adapter<SubViewHolder> {
        private Subscription subscription = Subscription.getInstance();
        private boolean savePending = false;

        @Override
        public SubViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new SubViewHolder(view);
        }

        @Override
        public void onBindViewHolder(SubViewHolder holder, int position) {
            holder.bind(subscription.getUrls().get(position));
        }

        @Override
        public int getItemCount() {
            return subscription.getUrls().size();
        }

        private void apply() {
            if (!savePending) {
                savePending = true;
                list.post(new Runnable() {
                    @Override
                    public void run() {
                        Subscription.getInstance().setUrls(subscription.getUrls());
                        savePending = false;
                    }
                });
            }
        }

        public int add(URL url) {
            int oldSize = subscription.getUrls().size();
            int index = subscription.getUrls().add(url);
            if (oldSize != subscription.getUrls().size()) {
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
            int index = subscription.getUrls().indexOf(item);
            notifyItemRemoved(index);
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

    private SubscriptionAdapter adapter;
    private RecyclerView list;
    private UndoSnackbarManager<URL> undoManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_subscriptions, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, ListHolderListener.INSTANCE);
        AlertDialogFragment.setResultListener(this, SubDialogFragment.class, SubEditResult.class,
                (which, ret) -> {
                    SubEditResult result = ret != null ? ret : null;
                    String edited = result.getEdited();
                    String replacing = result.getReplacing();
                    if (replacing != null) {
                        URL url;
                        try {
                            url = new URL(replacing);
                            adapter.remove(url);
                            if (which == DialogInterface.BUTTON_NEUTRAL) {
                                undoManager.remove(new Pair<>(-1, url));
                            }
                        } catch (MalformedURLException e) {
                            // Ignore
                        }
                    }
                    if (edited != null) {
                        URL url;
                        try {
                            url = new URL(edited);
                            int index = adapter.add(url);
                            list.post(() -> list.scrollToPosition(index));
                        } catch (MalformedURLException e) {
                            // Ignore
                        }
                    }
                });
        Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.subscriptions);
        toolbar.inflateMenu(R.menu.subscription_menu);
        toolbar.setOnMenuItemClickListener(this);
        SubscriptionService.getIdle().observe(getViewLifecycleOwner(), idle -> {
            MenuItem menuItem = toolbar.getMenu().findItem(R.id.action_update_subscription);
            menuItem.setEnabled(idle);
        });
        MainActivity activity = (MainActivity) getActivity();
        list = view.findViewById(R.id.list);
        ViewCompat.setOnApplyWindowInsetsListener(list, MainListListener.INSTANCE);
        list.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.VERTICAL, false));
        list.setItemAnimator(new DefaultItemAnimator());
        adapter = new SubscriptionAdapter();
        list.setAdapter(adapter);
        new FastScrollerBuilder(list).useMd2Style().build();
        undoManager = new UndoSnackbarManager<>(activity, adapter::undo);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                adapter.remove(viewHolder.getBindingAdapterPosition());
            }
        });
        itemTouchHelper.attachToRecyclerView(list);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_manual_settings:
                SubDialogFragment dialogFragment = new SubDialogFragment();
                dialogFragment.setArg(new SubItem());
                dialogFragment.setKey();
                dialogFragment.show(getParentFragmentManager(), null);
                return true;
            case R.id.action_update_subscription:
                Intent intent = new Intent(requireContext(), SubscriptionService.class);
                requireContext().startService(intent);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDetach() {
        undoManager.flush();
        super.onDetach();
    }
}
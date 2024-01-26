package com.github.shadowsocks.acl;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
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
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.github.shadowsocks.Core;
import com.github.shadowsocks.MainActivity;
import com.github.shadowsocks.R;
import com.github.shadowsocks.ToolbarFragment;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.net.Subnet;
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment;
import com.github.shadowsocks.utils.UndoSnackbarManager;
import com.github.shadowsocks.widget.ListHolderListener;
import com.github.shadowsocks.widget.MainListListener;
import com.google.android.material.textfield.TextInputLayout;
import kotlinx.parcelize.Parcelize;
import timber.log.Timber;

import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

public class CustomRulesFragment extends ToolbarFragment implements Toolbar.OnMenuItemClickListener, ActionMode.Callback {
    private static final String SELECTED_SUBNETS = "com.github.shadowsocks.acl.CustomRulesFragment.SELECTED_SUBNETS";
    private static final String SELECTED_HOSTNAMES = "com.github.shadowsocks.acl.CustomRulesFragment.SELECTED_HOSTNAMES";
    private static final String SELECTED_URLS = "com.github.shadowsocks.acl.CustomRulesFragment.SELECTED_URLS";

    private static final String domainPattern =
            "(?<=^(?:\\(\\^\\|\\\\\\.\\)|\\^\\(\\.\\*\\\\\\.\\)\\?|\\(\\?:\\^\\|\\\\\\.\\))).*(?=\\$$)";

    private static AclItem AclItem(Object item) {
        if (item instanceof String) {
            return new AclItem((String) item, false);
        } else if (item instanceof Subnet) {
            return new AclItem(item.toString(), false);
        } else if (item instanceof URL) {
            return new AclItem(item.toString(), true);
        } else {
            throw new IllegalArgumentException("item");
        }
    }

    private enum Template {
        Generic,
        Domain,
        Url;
    }

    @Parcelize
    public static class AclItem implements Parcelable {
        private final String item;
        private final boolean isUrl;

        public AclItem(String item, boolean isUrl) {
            this.item = item;
            this.isUrl = isUrl;
        }

        public Object toAny() {
            return isUrl ? new URL(item) : Subnet.fromString(item);
        }
    }

    @Parcelize
    public static class AclArg implements Parcelable {
        private final AclItem item;

        public AclArg(AclItem item) {
            this.item = item;
        }
    }

    @Parcelize
    public static class AclEditResult implements Parcelable {
        private final AclItem edited;
        private final AclItem replacing;

        public AclEditResult(AclItem edited, AclItem replacing) {
            this.edited = edited;
            this.replacing = replacing;
        }
    }

    public static class AclRuleDialogFragment extends AlertDialogFragment<AclArg, AclEditResult>
            implements TextWatcher, AdapterView.OnItemSelectedListener {
        private Spinner templateSelector;
        private EditText editText;
        private TextInputLayout inputLayout;
        private AlertDialog positive;

        @Override
        protected void prepare(AlertDialog.Builder builder, DialogInterface.OnClickListener listener) {
            MainActivity activity = requireActivity();
            View view = activity.getLayoutInflater().inflate(R.layout.dialog_acl_rule, null);
            templateSelector = view.findViewById(R.id.template_selector);
            editText = view.findViewById(R.id.content);
            inputLayout = view.findViewById(R.id.content_layout);
            templateSelector.setSelection(Template.Generic.ordinal());
            AclArg arg = arg.item;
            editText.setText(arg != null ? arg.item.item : "");
            if (arg == null) {
                // Do nothing
            } else if (arg.item.isUrl) {
                templateSelector.setSelection(Template.Url.ordinal());
            } else {
                Subnet subnet = Subnet.fromString(arg.item.item);
                if (subnet == null) {
                    java.util.regex.Matcher match = Pattern.compile(domainPattern).matcher(arg.item.item);
                    if (match.find()) {
                        templateSelector.setSelection(Template.Domain.ordinal());
                        editText.setText(IDN.toUnicode(match.group().replace("\\.", "."),
                                IDN.ALLOW_UNASSIGNED | IDN.USE_STD3_ASCII_RULES));
                    }
                }
            }

            templateSelector.setOnItemSelectedListener(this);
            editText.addTextChangedListener(this);
            builder.setTitle(R.string.edit_rule);
            builder.setPositiveButton(android.R.string.ok, listener);
            builder.setNegativeButton(android.R.string.cancel, null);
            if (arg != null && !arg.item.item.isEmpty()) {
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
            // Do nothing
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Do nothing
        }

        @Override
        public void afterTextChanged(Editable s) {
            validate(s.toString());
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            check(false);
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            validate(position);
        }

        private void validate() {
            validate(templateSelector.getSelectedItemPosition(), editText.getText());
        }

        private void validate(int template, Editable value) {
            String message = "";
            boolean isEnabled = false;
            switch (Template.values()[template]) {
                case Generic:
                    isEnabled = value.toString().trim().matches(".*");
                    break;
                case Domain:
                    try {
                        IDN.toASCII(value.toString(), IDN.ALLOW_UNASSIGNED | IDN.USE_STD3_ASCII_RULES);
                        isEnabled = true;
                    } catch (IllegalArgumentException e) {
                        message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    }
                    break;
                case Url:
                    try {
                        URL url = new URL(value.toString());
                        if ("http".equalsIgnoreCase(url.getProtocol())) {
                            message = getString(R.string.cleartext_http_warning);
                        }
                        isEnabled = true;
                    } catch (MalformedURLException e) {
                        message = e.getMessage();
                    }
                    break;
            }

            inputLayout.setError(message);
            positive.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(isEnabled);
        }

        @Override
        public AclEditResult ret(int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                int template = templateSelector.getSelectedItemPosition();
                String text = editText.getText().toString();
                switch (Template.values()[template]) {
                    case Generic:
                        return new AclEditResult(new AclItem(text), arg.item.item);
                    case Domain:
                        return new AclEditResult(new AclItem(
                                IDN.toASCII(text, IDN.ALLOW_UNASSIGNED | IDN.USE_STD3_ASCII_RULES)
                                        .replace(".", "\\.")),
                                arg.item.item);
                    case Url:
                        return new AclEditResult(new AclItem(text, true), arg.item.item);
                    default:
                        return null;
                }
            } else if (which == DialogInterface.BUTTON_NEUTRAL) {
                return new AclEditResult(null, arg.item.item);
            } else {
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

    private class AclRuleViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {
        private Object item;
        private TextView text;

        public AclRuleViewHolder(View view) {
            super(view);
            view.setFocusable(true);
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
            view.setBackgroundResource(R.drawable.background_selectable);
            text = view.findViewById(android.R.id.text1);
        }

        public void bind(String hostname) {
            item = hostname;
            text.setText(hostname);
            itemView.setSelected(selectedItems.contains(hostname));
        }

        public void bind(Subnet subnet) {
            item = subnet;
            text.setText(subnet.toString());
            itemView.setSelected(selectedItems.contains(subnet));
        }

        public void bind(URL url) {
            item = url;
            text.setText(url.toString());
            itemView.setSelected(selectedItems.contains(url));
        }

        @Override
        public void onClick(View v) {
            if (!selectedItems.isEmpty()) {
                onLongClick(v);
            } else {
                new AclRuleDialogFragment().apply {
                    arg(new AclArg(AclItem(item)));
                    key();
                }.show(parentFragmentManager, null);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (!selectedItems.add(item)) {
                selectedItems.remove(item);
            }
            onSelectedItemsUpdated();
            itemView.setSelected(!itemView.isSelected());
            return true;
        }
    }

    private boolean isEnabled() {
        return ((MainActivity) getActivity()).getState() == BaseService.State.Stopped ||
                (Core.getCurrentProfile() != null &&
                        Core.getCurrentProfile().main.route == Acl.CUSTOM_RULES);
    }

    private Set<Object> selectedItems = new HashSet<>();
    private AclRulesAdapter adapter;
    private RecyclerView list;
    private ActionMode mode;
    private UndoSnackbarManager<Object> undoManager;

    private void onSelectedItemsUpdated() {
        if (selectedItems.isEmpty()) {
            if (mode != null) {
                mode.finish();
            }
        } else if (mode == null) {
            mode = toolbar.startActionMode(this);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_custom_rules, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, ListHolderListener);
        AlertDialogFragment.setResultListener(this, AclRuleDialogFragment.class, AclEditResult.class,
                (which, ret) -> {
                    AclEditResult result = ret != null ? ret : null;
                    if (result == null) {
                        return;
                    }

                    if (result.replacing != null) {
                        adapter.remove(result.replacing.toAny());
                        if (which == DialogInterface.BUTTON_NEUTRAL) {
                            undoManager.remove(new Pair<>(-1, result.replacing.toAny()));
                        }
                    }

                    if (result.edited != null) {
                        Object editedItem = result.edited.toAny();
                        Integer position = adapter.add(editedItem);
                        if (position != null) {
                            list.post(() -> list.scrollToPosition(position));
                        }
                    }
                });

        if (savedInstanceState != null) {
            String[] selectedSubnets = savedInstanceState.getStringArray(SELECTED_SUBNETS);
            if (selectedSubnets != null) {
                for (String subnet : selectedSubnets) {
                    selectedItems.add(Subnet.fromString(subnet));
                }
            }

            String[] selectedHostnames = savedInstanceState.getStringArray(SELECTED_HOSTNAMES);
            if (selectedHostnames != null) {
                selectedItems.addAll(Arrays.asList(selectedHostnames));
            }

            String[] selectedUrls = savedInstanceState.getStringArray(SELECTED_URLS);
            if (selectedUrls != null) {
                for (String url : selectedUrls) {
                    try {
                        selectedItems.add(new URL(url));
                    } catch (MalformedURLException e) {
                        // Ignore invalid URLs
                    }
                }
            }

            onSelectedItemsUpdated();
        }

        toolbar.setTitle(R.string.custom_rules);
        toolbar.inflateMenu(R.menu.custom_rules_menu);
        toolbar.setOnMenuItemClickListener(this);

        MainActivity activity = (MainActivity) getActivity();
        list = view.findViewById(R.id.list);
        ViewCompat.setOnApplyWindowInsetsListener(list, MainListListener);
        list.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.VERTICAL, false));
        list.setItemAnimator(new DefaultItemAnimator());
        adapter = new AclRulesAdapter();
        list.setAdapter(adapter);

        new FastScrollerBuilder(list).useMd2Style().build();
        undoManager = new UndoSnackbarManager<>(activity, adapter::undo);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START) {
            @Override
            public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                if (isEnabled() && selectedItems.isEmpty()) {
                    return super.getSwipeDirs(recyclerView, viewHolder);
                } else {
                    return 0;
                }
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                adapter.remove(viewHolder.getBindingAdapterPosition());
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                  RecyclerView.ViewHolder target) {
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

 override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArray(
            SELECTED_SUBNETS,
            selectedItems.filterIsInstance<Subnet>().map(Subnet::toString).toTypedArray()
        )
        outState.putStringArray(SELECTED_HOSTNAMES, selectedItems.filterIsInstance<String>().toTypedArray())
        outState.putStringArray(SELECTED_URLS, selectedItems.filterIsInstance<URL>().map(URL::toString).toTypedArray())
    }

    private fun copySelected() {
        val acl = Acl()
        acl.bypass = true
        selectedItems.forEach {
            when (it) {
                is Subnet -> acl.subnets.add(it)
                is String -> acl.proxyHostnames.add(it)
                is URL -> acl.urls.add(it)
            }
        }
        val success = Core.trySetPrimaryClip(acl.toString())
        (activity as MainActivity).snackbar().setText(
            if (success) R.string.action_export_msg else R.string.action_export_err
        ).show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_manual_settings -> {
            AclRuleDialogFragment().apply {
                arg(AclArg(AclItem()))
                key()
            }.show(parentFragmentManager, null)
            true
        }
        R.id.action_import_clipboard -> {
            try {
                check(adapter.addToProxy(Core.clipboard.primaryClip!!.getItemAt(0).text.toString()) != null)
            } catch (exc: Exception) {
                (activity as MainActivity).snackbar().setText(R.string.action_import_err).show()
                Timber.d(exc)
            }
            true
        }
        R.id.action_import_gfwlist -> {
            val acl = Acl().fromId(Acl.GFWLIST)
            if (acl.bypass) acl.subnets.asIterable().forEach { adapter.addSubnet(it) }
            acl.proxyHostnames.asIterable().forEach { adapter.addHostname(it) }
            acl.urls.asIterable().forEach { adapter.addURL(it) }
            true
        }
        else -> false
    }

    override fun onDetach() {
        undoManager.flush()
        mode?.finish()
        super.onDetach()
    }

    @SuppressLint("ResourceType")
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val activity = requireActivity()
        activity.window.statusBarColor = ContextCompat.getColor(activity, android.R.color.black)
        activity.menuInflater.inflate(R.menu.custom_rules_selection, menu)
        toolbar.touchscreenBlocksFocus = true
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_select_all -> {
            adapter.selectAll()
            true
        }
        R.id.action_cut -> {
            copySelected()
            adapter.removeSelected()
            true
        }
        R.id.action_copy -> {
            copySelected()
            true
        }
        R.id.action_delete -> {
            adapter.removeSelected()
            true
        }
        else -> false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        val activity = requireActivity()
        activity.window.statusBarColor = ContextCompat.getColor(
            activity,
            activity.theme.resolveResourceId(android.R.attr.statusBarColor)
        )
        toolbar.touchscreenBlocksFocus = false
        selectedItems.clear()
        onSelectedItemsUpdated()
        adapter.notifyDataSetChanged()
        this.mode = null
    }

private class AclRuleViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener, View.OnLongClickListener {

    private Object item;
    private TextView text;

    public AclRuleViewHolder(View view) {
        super(view);
        view.setFocusable(true);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
        view.setBackgroundResource(R.drawable.background_selectable);
        text = view.findViewById(android.R.id.text1);
    }

    public void bind(String hostname) {
        item = hostname;
        text.setText(hostname);
        itemView.setSelected(selectedItems.contains(hostname));
    }

    public void bind(Subnet subnet) {
        item = subnet;
        text.setText(subnet.toString());
        itemView.setSelected(selectedItems.contains(subnet));
    }

    public void bind(URL url) {
        item = url;
        text.setText(url.toString());
        itemView.setSelected(selectedItems.contains(url));
    }

    @Override
    public void onClick(View v) {
        if (!selectedItems.isEmpty()) {
            onLongClick(v);
        } else {
            new AclRuleDialogFragment().apply {
                arg(new AclArg(new AclItem(item)));
                key();
            }.show(parentFragmentManager, null);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (!selectedItems.add(item)) {
            selectedItems.remove(item);
        }
        onSelectedItemsUpdated();
        itemView.setSelected(!itemView.isSelected());
        return true;
    }
}

}
package com.github.shadowsocks.acl;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
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
import com.github.shadowsocks.utils.asIterable;
import com.github.shadowsocks.utils.readableMessage;
import com.github.shadowsocks.utils.resolveResourceId;
import com.github.shadowsocks.widget.ListHolderListener;
import com.github.shadowsocks.widget.MainListListener;
import com.github.shadowsocks.widget.UndoSnackbarManager;
import com.google.android.material.textfield.TextInputLayout;
import kotlinx.parcelize.Parcelize;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.PatternSyntaxException;

public class CustomRulesFragment extends ToolbarFragment implements Toolbar.OnMenuItemClickListener, ActionMode.Callback {

    private static final String SELECTED_SUBNETS = "com.github.shadowsocks.acl.CustomRulesFragment.SELECTED_SUBNETS";
    private static final String SELECTED_HOSTNAMES = "com.github.shadowsocks.acl.CustomRulesFragment.SELECTED_HOSTNAMES";
    private static final String SELECTED_URLS = "com.github.shadowsocks.acl.CustomRulesFragment.SELECTED_URLS";

    private static final java.util.regex.Pattern domainPattern =
            java.util.regex.Pattern.compile("(?<=^(?:\\(\\^\\|\\\\\\.\\)|\\^\\(\\.\\*\\\\\\.\\)\\?|\\(\\?:\\^\\|\\\\\\.\\))).*(?=\\$$)");

    private CustomRulesFragment() {
    }

    private enum Template {
        Generic,
        Domain,
        Url
    }

    @Parcelize
    public static class AclItem implements Parcelable {
        public final String item;
        public final boolean isUrl;

        public AclItem(String item, boolean isUrl) {
            this.item = item;
            this.isUrl = isUrl;
        }

        public Object toAny() {
            return isUrl ? new URL(item) : Subnet.fromString(item) != null ? Subnet.fromString(item) : item;
        }
    }

    @Parcelize
    public static class AclArg implements Parcelable {
        public final AclItem item;

        public AclArg(AclItem item) {
            this.item = item;
        }
    }

    @Parcelize
    public static class AclEditResult implements Parcelable {
        public final AclItem edited;
        public final AclItem replacing;

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
        private TextView positive;

        @Override
        protected void prepare(AlertDialog.Builder builder, DialogInterface.OnClickListener listener) {
            Context activity = requireActivity();
            @SuppressLint("InflateParams")
            View view = activity.getLayoutInflater().inflate(R.layout.dialog_acl_rule, null);
            templateSelector = view.findViewById(R.id.template_selector);
            editText = view.findViewById(R.id.content);
            inputLayout = view.findViewById(R.id.content_layout);
            positive = ((AlertDialog) builder.create()).getButton(AlertDialog.BUTTON_POSITIVE);

            templateSelector.setSelection(Template.Generic.ordinal);
            AclArg arg = getArguments().getParcelable("arg");
            editText.setText(arg != null ? arg.item.item : null);

            if (arg != null) {
                if (arg.item != null && arg.item.isUrl) {
                    templateSelector.setSelection(Template.Url.ordinal);
                } else if (Subnet.fromString(arg.item.item) == null) {
                    java.util.regex.Matcher match = domainPattern.matcher(arg.item.item);
                    if (match.find()) {
                        templateSelector.setSelection(Template.Domain.ordinal);
                        editText.setText(IDN.toUnicode(match.group().replace("\\.", "."),
                                IDN.ALLOW_UNASSIGNED | IDN.USE_STD3_ASCII_RULES));
                    }
                }
            }

            templateSelector.setOnItemSelectedListener(this);
            editText.addTextChangedListener(this);
            builder.setTitle(R.string.edit_rule)
                    .setPositiveButton(android.R.string.ok, listener)
                    .setNegativeButton(android.R.string.cancel, null);

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
            validate(position);
        }

        private void validate() {
            validate(templateSelector.getSelectedItemPosition(), editText.getText());
        }

        private void validate(int template, Editable value) {
            String message = "";
            boolean isEnabled;
            switch (Template.values()[template]) {
                case Generic:
                    isEnabled = value.toString().matches(".+");
                    break;
                case Domain:
                    try {
                        IDN.toASCII(value.toString(), IDN.ALLOW_UNASSIGNED | IDN.USE_STD3_ASCII_RULES);
                        isEnabled = true;
                    } catch (IllegalArgumentException e) {
                        message = e.getCause() != null ? e.getCause().getLocalizedMessage() : e.getLocalizedMessage();
                        isEnabled = false;
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
                        message = e.getLocalizedMessage();
                        isEnabled = false;
                    }
                    break;
                default:
                    isEnabled = false;
            }
            inputLayout.setError(message);
            positive.setEnabled(isEnabled);
        }

        @Override
        protected AclEditResult ret(int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    return new AclEditResult(new AclItem(editText.getText().toString(), Template.values()[templateSelector.getSelectedItemPosition()] == Template.Url), getArguments().getParcelable("arg").item);
                case DialogInterface.BUTTON_NEUTRAL:
                    return new AclEditResult(null, getArguments().getParcelable("arg").item);
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




    private class AclRulesAdapter extends RecyclerView.Adapter<AclRuleViewHolder> {
        private final Acl acl = Acl.customRules;
        private boolean savePending = false;

        @Override
        public void onBindViewHolder(AclRuleViewHolder holder, int i) {
            int j = i - acl.getSubnets().size();
            if (j < 0) {
                holder.bind(acl.getSubnets().get(i));
            } else {
                int k = j - acl.getProxyHostnames().size();
                if (k < 0) {
                    holder.bind(acl.getProxyHostnames().get(j));
                } else {
                    holder.bind(acl.getUrls().get(k));
                }
            }
        }

        @Override
        public AclRuleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new AclRuleViewHolder(view);
        }

        @Override
        public int getItemCount() {
            return acl.getSubnets().size() + acl.getProxyHostnames().size() + acl.getUrls().size();
        }

        private void apply() {
            if (!savePending) {
                savePending = true;
                list.post(() -> {
                    Acl.customRules = acl;
                    savePending = false;
                });
            }
        }

        public Integer add(Object item) {
            if (item instanceof Subnet) {
                return addSubnet((Subnet) item);
            } else if (item instanceof String) {
                return addHostname((String) item);
            } else if (item instanceof URL) {
                return addURL((URL) item);
            } else {
                return null;
            }
        }

        public int addSubnet(Subnet subnet) {
            int oldSize = acl.getSubnets().size();
            int index = acl.getSubnets().add(subnet);
            if (oldSize != acl.getSubnets().size()) {
                notifyItemInserted(index);
                apply();
            }
            return index;
        }

        public int addHostname(String hostname) {
            int oldSize = acl.getProxyHostnames().size();
            int index = acl.getSubnets().size() + acl.getProxyHostnames().add(hostname);
            if (oldSize != acl.getProxyHostnames().size()) {
                notifyItemInserted(index);
                apply();
            }
            return index;
        }

        public int addURL(URL url) {
            int oldSize = acl.getUrls().size();
            int index = acl.getSubnets().size() + acl.getProxyHostnames().size() + acl.getUrls().add(url);
            if (oldSize != acl.getUrls().size()) {
                notifyItemInserted(index);
                apply();
            }
            return index;
        }

        public Integer addToProxy(String input) {
            Acl acl = new Acl().fromReader(new StringReader(input), true);
            Integer result = null;
            if (acl.isBypass()) {
                for (Subnet subnet : acl.getSubnets()) {
                    int currentIndex = addSubnet(subnet);
                    if (result == null) {
                        result = currentIndex;
                    }
                }
            }
            Stream.concat(
                    acl.getProxyHostnames().stream().map(this::addHostname),
                    acl.getUrls().stream().map(this::addURL)
            ).forEach(currentIndex -> {
                if (result == null) {
                    result = currentIndex;
                }
            });
            return result;
        }

        public void remove(int i) {
            int j = i - acl.getSubnets().size();
            if (j < 0) {
                undoManager.remove(new Pair<>(i, acl.getSubnets().get(i)));
                acl.getSubnets().removeItemAt(i);
            } else {
                int k = j - acl.getProxyHostnames().size();
                if (k < 0) {
                    undoManager.remove(new Pair<>(j, acl.getProxyHostnames().get(j)));
                    acl.getProxyHostnames().removeItemAt(j);
                } else {
                    undoManager.remove(new Pair<>(k, acl.getUrls().get(k)));
                    acl.getUrls().removeItemAt(k);
                }
            }
            notifyItemRemoved(i);
            apply();
        }

        public void remove(Object item) {
            if (item instanceof Subnet) {
                int index = acl.getSubnets().indexOf(item);
                notifyItemRemoved(index);
                acl.getSubnets().remove(item);
                apply();
            } else if (item instanceof String) {
                int index = acl.getSubnets().size() + acl.getProxyHostnames().indexOf(item);
                notifyItemRemoved(index);
                acl.getProxyHostnames().remove(item);
                apply();
            } else if (item instanceof URL) {
                int index = acl.getSubnets().size() + acl.getProxyHostnames().size() + acl.getUrls().indexOf(item);
                notifyItemRemoved(index);
                acl.getUrls().remove(item);
                apply();
            }
        }

        public void removeSelected() {
            undoManager.remove(selectedItems.stream().map(item -> new Pair<>(0, item)).collect(Collectors.toList()));
            selectedItems.forEach(this::remove);
            selectedItems.clear();
            onSelectedItemsUpdated();
        }

        public void undo(List<Pair<Integer, Object>> actions) {
            for (Pair<Integer, Object> action : actions) {
                add(action.getSecond());
            }
        }

        public void selectAll() {
            selectedItems.clear();
            selectedItems.addAll(acl.getSubnets().asIterable());
            selectedItems.addAll(acl.getProxyHostnames().asIterable());
            selectedItems.addAll(acl.getUrls().asIterable());
            onSelectedItemsUpdated();
            notifyDataSetChanged();
        }
    }
    private class AclRuleViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private Object item;
        private final TextView text;

        public AclRuleViewHolder(View view) {
            super(view);
            text = view.findViewById(android.R.id.text1);
            view.setFocusable(true);
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
            view.setBackgroundResource(R.drawable.background_selectable);
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
            if (selectedItems.isNotEmpty()) {
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
            itemView.setSelected(!itemView.isSelected);
            return true;
        }
    }
      private void onSelectedItemsUpdated() {
        if (selectedItems.isEmpty()) {
            if (mode != null) mode.finish();
        } else if (mode == null) {
            mode = toolbar.startActionMode(this);
            backHandler.setEnabled(true);
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
        AlertDialogFragment.setResultListener(this, (which, ret) -> {
            if (ret != null) {
                AclItem edited = ret.getFirst();
                AclItem replacing = ret.getSecond();
                if (replacing != null) {
                    adapter.remove(replacing.toAny());
                    if (which == DialogInterface.BUTTON_NEUTRAL) {
                        undoManager.remove(new Pair<>(-1, replacing.toAny()));
                    }
                }
                if (edited != null) {
                    adapter.add(edited.toAny());
                    list.post(() -> list.scrollToPosition(edited.getPosition()));
                }
            }
        });

        if (savedInstanceState != null) {
            selectedItems.addAll(Subnet.fromStrings(savedInstanceState.getStringArray(SELECTED_SUBNETS)));
            selectedItems.addAll(savedInstanceState.getStringArrayList(SELECTED_HOSTNAMES));
            selectedItems.addAll(URLsFromStringList(savedInstanceState.getStringArrayList(SELECTED_URLS)));
            onSelectedItemsUpdated();
        }

        toolbar.setTitle(R.string.custom_rules);
        toolbar.inflateMenu(R.menu.custom_rules_menu);
        toolbar.setOnMenuItemClickListener(this);

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
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }
        }).attachToRecyclerView(list);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        requireActivity().getOnBackPressedDispatcher().addCallback(backHandler);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArray(SELECTED_SUBNETS, Subnet.toStringArray(selectedItems));
        outState.putStringArrayList(SELECTED_HOSTNAMES, new ArrayList<>(selectedItems));
        outState.putStringArrayList(SELECTED_URLS, URLsToStringList(selectedItems));
    }

    private void copySelected() {
        Acl acl = new Acl();
        acl.setBypass(true);

        for (Object it : selectedItems) {
            if (it instanceof Subnet) {
                acl.getSubnets().add((Subnet) it);
            } else if (it instanceof String) {
                acl.getProxyHostnames().add((String) it);
            } else if (it instanceof URL) {
                acl.getUrls().add((URL) it);
            }
        }

        boolean success = Core.trySetPrimaryClip(acl.toString());
        ((MainActivity) requireActivity()).snackbar().setText(
                success ? R.string.action_export_msg : R.string.action_export_err).show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_manual_settings:
                new AclRuleDialogFragment().apply {
                    arg(AclArg(AclItem()))
                    key();
                }.show(getParentFragmentManager(), null);
                return true;
            case R.id.action_import_clipboard:
                try {
                    CharSequence clipText = Core.getClipboard().getPrimaryClip().getItemAt(0).getText();
                    check(adapter.addToProxy(clipText.toString()) != null);
                } catch (Exception exc) {
                    ((MainActivity) requireActivity()).snackbar().setText(R.string.action_import_err).show();
                    Timber.d(exc);
                }
                return true;
            case R.id.action_import_gfwlist:
                Acl acl = new Acl().fromId(Acl.GFWLIST);
                if (acl.isBypass()) {
                    for (Subnet subnet : acl.getSubnets()) {
                        adapter.addSubnet(subnet);
                    }
                }
                for (String hostname : acl.getProxyHostnames()) {
                    adapter.addHostname(hostname);
                }
                for (URL url : acl.getUrls()) {
                    adapter.addURL(url);
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDetach() {
        backHandler.remove();
        undoManager.flush();
        if (mode != null) mode.finish();
        super.onDetach();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MainActivity activity = (MainActivity) requireActivity();
        activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, android.R.color.black));
        activity.getMenuInflater().inflate(R.menu.custom_rules_selection, menu);
        toolbar.setTouchscreenBlocksFocus(true);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_select_all:
                adapter.selectAll();
                return true;
            case R.id.action_cut:
                copySelected();
                adapter.removeSelected();
                return true;
            case R.id.action_copy:
                copySelected();
                return true;
            case R.id.action_delete:
                adapter.removeSelected();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        MainActivity activity = (MainActivity) requireActivity();
        activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity,
                activity.getTheme().resolveResourceId(android.R.attr.statusBarColor)));
        toolbar.setTouchscreenBlocksFocus(false);
        selectedItems.clear();
        onSelectedItemsUpdated();
        adapter.notifyDataSetChanged();
        backHandler.setEnabled(false);
        this.mode = null;
    }
}
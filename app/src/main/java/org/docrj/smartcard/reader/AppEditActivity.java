/*
 * Copyright 2015 Ryan Jones
 *
 * This file is part of smartcard-reader, package org.docrj.smartcard.reader.
 *
 * smartcard-reader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * smartcard-reader is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with smartcard-reader. If not, see <http://www.gnu.org/licenses/>.
 */

package org.docrj.smartcard.reader;

import android.app.Activity;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;


public class AppEditActivity extends ActionBarActivity {

    private static final String TAG = LaunchActivity.TAG;

    private static final String[] DEFAULT_GROUPS = SmartcardApp.GROUPS;

    // actions
    private static final String ACTION_NEW_APP = AppsListActivity.ACTION_NEW_APP;
    private static final String ACTION_VIEW_APP = AppsListActivity.ACTION_VIEW_APP;
    private static final String ACTION_EDIT_APP = AppsListActivity.ACTION_EDIT_APP;
    private static final String ACTION_COPY_APP = AppsListActivity.ACTION_COPY_APP;

    // extras
    private static final String EXTRA_APP_POS = AppsListActivity.EXTRA_APP_POS;

    private static final int NEW_APP_POS = -1;

    private SharedPreferences.Editor mEditor;
    private ArrayList<SmartcardApp> mApps;
    private String mAction;
    private int mAppPos;

    private EditText mName;
    private EditText mAid;
    private RadioGroup mType;

    // groups created by user (no payment/other)
    private HashSet<String> mUserGroups;
    // groups of which the app is member
    private HashSet<String> mAppGroups;

    private Button mGrpButton;
    // group items for the popup window list adapter
    private ArrayList<GroupItem> mGrpItems = new ArrayList<>();
    private GroupAdapter mGrpAdapter;
    private ListPopupWindow mPopup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_app_edit);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        findViewById(R.id.actionbar_cancel).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // "cancel"
                        finish();
                    }
                });
        findViewById(R.id.actionbar_save).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // "save"
                        saveAndFinish(false);
                    }
                });

        // persistent data in shared prefs
        SharedPreferences ss = getSharedPreferences("prefs", Context.MODE_PRIVATE);
        mEditor = ss.edit();

        Gson gson = new Gson();
        String json = ss.getString("apps", null);
        if (json != null) {
            // deserialize list of SmartcardApp
            Type collectionType = new TypeToken<ArrayList<SmartcardApp>>() {
            }.getType();
            mApps = gson.fromJson(json, collectionType);
        }

        if (savedInstanceState == null) {
            json = ss.getString("groups", null);
            if (json == null) {
                mUserGroups = new LinkedHashSet<>();
            } else {
                Type collectionType = new TypeToken<LinkedHashSet<String>>() {
                }.getType();
                mUserGroups = gson.fromJson(json, collectionType);
                // here we do not need the default groups payment/other
                //mUserGroups.removeAll(Arrays.asList(DEFAULT_GROUPS));
            }
        } else {
            mUserGroups =
                new LinkedHashSet<>(savedInstanceState.getStringArrayList("user_groups"));
        }

        Intent intent = getIntent();
        mAction = intent.getAction();
        mAppPos = intent.getIntExtra(EXTRA_APP_POS, NEW_APP_POS);

        mName = (EditText) findViewById(R.id.app_name);
        mAid = (EditText) findViewById(R.id.app_aid);
        mType = (RadioGroup) findViewById(R.id.radio_grp_type);

        if (ACTION_EDIT_APP.equals(mAction) || ACTION_COPY_APP.equals(mAction)) {
            SmartcardApp app = mApps.get(mAppPos);
            mName.setText(app.getName());
            mAid.setText(app.getAid());
            mType.check((app.getType() == SmartcardApp.TYPE_OTHER) ? R.id.radio_other
                    : R.id.radio_payment);
        }
        if (savedInstanceState == null) {
            if (ACTION_EDIT_APP.equals(mAction) || ACTION_COPY_APP.equals(mAction)) {
                SmartcardApp app = mApps.get(mAppPos);
                // clone so that we modify a new set for comparison later
                mAppGroups = (HashSet<String>) app.getGroups().clone();
            } else {
                mAppGroups = new LinkedHashSet<>();
                mAppGroups.add(DEFAULT_GROUPS[SmartcardApp.TYPE_PAYMENT]);
            }
        } else {
            mAppGroups =
                new LinkedHashSet<>(savedInstanceState.getStringArrayList("app_groups"));
        }

        mType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int type = getType();
                mAppGroups.remove(DEFAULT_GROUPS[Math.abs(type-1)]);
                mAppGroups.add(DEFAULT_GROUPS[type]);
                updateGrpButton();
            }
        });

        if (ACTION_COPY_APP.equals(mAction)) {
            // now that we have populated the fields, treat copy like new
            mAppPos = NEW_APP_POS;
        }
        mName.requestFocus();

        mGrpAdapter = new GroupAdapter(this, mGrpItems);
        mGrpButton = (Button) findViewById(R.id.group_list);
        updateGrpButton();

        mGrpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPopup != null) {
                    mPopup.dismiss();
                }
                // update group list on click to make sure all recent input
                // (eg. type payment/other) is taken into account
                updateGrpList();
                // use a list popup window populated with checked textview rows
                ListPopupWindow popup = new ListPopupWindow(AppEditActivity.this, null);
                popup.setWidth(((View)mGrpButton.getParent()).getWidth());
                popup.setAnchorView(mGrpButton);
                popup.setAdapter(mGrpAdapter);
                popup.setOnItemClickListener(mItemClickListener);
                popup.show();
                mPopup = popup;
                // setup the popup window's listview
                ListView listView = popup.getListView();
                listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                int size = mGrpItems.size();
                for (int i = 0; i < size; i++) {
                    listView.setItemChecked(i, mGrpItems.get(i).isChecked());
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = getWindow();
            w.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            w.setStatusBarColor(getResources().getColor(R.color.primary_dark));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putStringArrayList("user_groups", new ArrayList<>(mUserGroups));
        outState.putStringArrayList("app_groups", new ArrayList<>(mAppGroups));
    }

    @Override
    public void onBackPressed() {
        saveAndFinish(true);
        super.onBackPressed();
    }

    private boolean validateNameAndAid(String name, String aid, boolean backPressed) {
        if (name.isEmpty()) {
            if (!(backPressed && mAction.equals(ACTION_NEW_APP) && aid.isEmpty())) {
                showToast(getString(aid.isEmpty() ?
                        R.string.empty_name_aid : R.string.empty_name));
            }
            return false;
        }
        if (aid.isEmpty()) {
            showToast(getString(R.string.empty_aid));
            return false;
        }
        if (aid.length() < 10 || aid.length() > 32
                || aid.length() % 2 != 0) {
            showToast(getString(R.string.invalid_aid));
            return false;
        }
        // ensure name is unique
        for (int i = 0; i < mApps.size(); i++) {
            // skip the app being edited
            if (mAppPos == i)
                continue;
            SmartcardApp app = mApps.get(i);
            if (app.getName().equals(name)) {
                showToast(getString(R.string.name_exists,
                        name));
                return false;
            }
        }
        return true;
    }

    private int getType() {
        int selectedId = mType.getCheckedRadioButtonId();
        RadioButton radioBtn = (RadioButton) findViewById(selectedId);
        return radioBtn.getText().toString()
                .equals(getString(R.string.radio_payment)) ? SmartcardApp.TYPE_PAYMENT
                : SmartcardApp.TYPE_OTHER;
    }

    private void saveAndFinish(boolean backPressed) {
        // validate name and aid
        String name = mName.getText().toString();
        String aid = mAid.getText().toString();
        if (!validateNameAndAid(name, aid, backPressed)) {
            return;
        }
        // app type radio group
        int type = getType();

        boolean appChanged = false;
        SmartcardApp newApp = new SmartcardApp(name, aid, type);
        newApp.setGroups(mAppGroups);

        if (mAction == ACTION_NEW_APP || mAction == ACTION_COPY_APP) {
            appChanged = true;
            synchronized (mApps) {
                mApps.add(newApp);
            }
        } else { // edit app
            SmartcardApp app = mApps.get(mAppPos);
            if (!newApp.equals(app)) {
                appChanged = true;
                mApps.set(mAppPos, newApp);
            }
        }

        if (appChanged) {
            writePrefs();
        }
        if (appChanged || !backPressed) {
            showToast(getString(R.string.app_saved));
        }

        if (mAction == ACTION_COPY_APP) {
            Intent i = new Intent(this, AppViewActivity.class);
            i.setAction(ACTION_VIEW_APP);
            i.putExtra(EXTRA_APP_POS, mApps.size()-1);
            startActivity(i);
            // calling activity (copy-from app view) will finish
            setResult(RESULT_OK);
        }
        finish();
    }

    private void writePrefs() {
        // serialize list of SmartcardApp
        Gson gson = new Gson();
        String json = gson.toJson(mApps);
        mEditor.putString("apps", json);

        // TODO: check this. shouldn't we store defaults: payment/other
        json = gson.toJson(mUserGroups);
        mEditor.putString("groups", json);
        mEditor.commit();
    }

    private void showToast(String text) {
        Toast toast = Toast.makeText(this, text,
                Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, -100);
        toast.show();
    }

    private void updateGrpButton() {
        String grpText;
        if (mAppGroups.size() <= 1) {
            grpText = getString(R.string.new_app_groups_hint);
        } else {
            grpText = mAppGroups.toString().replaceAll("[\\[\\]]", "");
        }
        mGrpButton.setText(grpText);
    }

    private void updateGrpList() {
        mGrpItems.clear();
        for (String group : mUserGroups) {
            mGrpItems.add(new GroupItem(group, mAppGroups.contains(group)));
        }
        mGrpItems.add(new GroupItem(DEFAULT_GROUPS[getType()], true));
        mGrpItems.add(new GroupItem(getString(R.string.new_group), false));
    }

    private void createNewGroup() {
        if (mPopup != null) {
            mPopup.dismiss();
        }
        NewGroupDialogFragment.show(getFragmentManager(),
                new NewGroupDialogFragment.OnNewGroupListener() {
                    @Override
                    public void onNewGroup(String name) {
                        if (Arrays.asList(DEFAULT_GROUPS).contains(name)) {
                            showToast(getString(R.string.default_group_exists));
                            return;
                        }
                        mAppGroups.add(name);
                        mUserGroups.add(name);
                        updateGrpButton();
                    }
                });
    }

    public static final class GroupItem {
        private final String mName;
        private boolean mChecked;

        public GroupItem(String name, boolean checked) {
            mName = name;
            mChecked = checked;
        }

        public boolean isChecked() {
            return mChecked;
        }

        public void setChecked(boolean checked) {
            mChecked = checked;
        }

        @Override
        public String toString() {
            return mName;
        }
    }

    private class GroupAdapter extends ArrayAdapter<GroupItem> {
        public GroupAdapter(Activity context, ArrayList<GroupItem> groupItems) {
            super(context, R.layout.group_list_item, groupItems);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final View itemView = super.getView(position, convertView, parent);
            if (itemView == null) {
                return null;
            }
            final CheckedTextView checkedTextView = (CheckedTextView)itemView;

            if (hasCheckbox(position)) {
                checkedTextView.setEnabled(isEnabled(position));
            } else {
                checkedTextView.setCheckMarkDrawable(null);
                checkedTextView.setTextColor(getResources().getColor(R.color.accent));
            }
            return checkedTextView;
        }

        @Override
        public boolean isEnabled(int position) {
            return position != getCount()-2;
        }

        public boolean hasCheckbox(int position) {
            return position != getCount()-1;
        }

        @Override
        public int getItemViewType(int position) {
            if (hasCheckbox(position)) {
                return isEnabled(position) ? 0 : 1;
            } else {
                return 2;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }
    }

    final AdapterView.OnItemClickListener mItemClickListener =
            new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    ListView list = (ListView) parent;
                    int count = mGrpItems.size();

                    if (list.isItemChecked(count - 1)) {
                        list.setItemChecked(count - 1, false);
                        createNewGroup();
                        return;
                    }

                    boolean checked = list.isItemChecked(position);
                    mGrpItems.get(position).setChecked(checked);
                    String name = mGrpItems.get(position).toString();

                    if (checked) {
                        mAppGroups.add(name);
                    } else {
                        mAppGroups.remove(name);
                    }
                    updateGrpButton();
                }
            };
}

/*
 * Copyright (C) 2015 Domoticz - Mark Heinis
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package nl.hnogames.domoticz.Welcome;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;

import com.afollestad.materialdialogs.MaterialDialog;
import com.fastaccess.permission.base.PermissionFragmentHelper;
import com.fastaccess.permission.base.callback.OnPermissionCallback;
import com.marvinlabs.widget.floatinglabel.edittext.FloatingLabelEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import nl.hnogames.domoticz.R;
import nl.hnogames.domoticz.ServerSettingsActivity;
import nl.hnogames.domoticz.UI.MultiSelectionSpinner;
import nl.hnogames.domoticz.Utils.PermissionsUtil;
import nl.hnogames.domoticz.Utils.SharedPrefUtil;
import nl.hnogames.domoticz.Utils.UsefulBits;
import nl.hnogames.domoticz.app.AppController;
import nl.hnogames.domoticzapi.Containers.ServerInfo;
import nl.hnogames.domoticzapi.Domoticz;
import nl.hnogames.domoticzapi.DomoticzValues;
import nl.hnogames.domoticzapi.Interfaces.WifiSSIDListener;
import nl.hnogames.domoticzapi.Utils.PhoneConnectionUtil;
import nl.hnogames.domoticzapi.Utils.ServerUtil;

public class SetupServerSettings extends Fragment implements OnPermissionCallback {

    private static final String INSTANCE = "INSTANCE";
    private static final int ADDSERVER = 22;
    boolean activeServerChanged = false;
    private SharedPrefUtil mSharedPrefs;
    private ServerUtil mServerUtil;

    private FloatingLabelEditText remote_server_input, remote_port_input,
            remote_username_input, remote_password_input,
            remote_directory_input, local_server_input, local_password_input,
            local_username_input, local_port_input, local_directory_input, server_name_input;

    private Spinner remote_protocol_spinner, local_protocol_spinner, startScreen_spinner;
    private Switch localServer_switch;
    private int remoteProtocolSelectedPosition, localProtocolSelectedPosition, startScreenSelectedPosition;
    private View v;
    private boolean hasBeenVisibleToUser = false;
    private MultiSelectionSpinner local_wifi_spinner;
    private int callingInstance;
    private PhoneConnectionUtil mPhoneConnectionUtil;
    private Switch advancedSettings_switch;
    private CheckBox cbShowPassword, cbShowPasswordLocal;
    private Button saveButton;
    private Switch useSameAddress;
    private ServerInfo newServer;
    private boolean isUpdateRequest = false;
    private Context mContext;
    private Button btnManualSSID;

    private PermissionFragmentHelper permissionFragmentHelper;

    public static SetupServerSettings newInstance(int instance) {
        SetupServerSettings f = new SetupServerSettings();

        Bundle bdl = new Bundle(1);
        bdl.putInt(INSTANCE, instance);
        f.setArguments(bdl);

        return f;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
        if (mServerUtil == null)
            mServerUtil = new ServerUtil(mContext);
        mSharedPrefs = new SharedPrefUtil(mContext);

        Bundle extras = getArguments();
        if (extras != null) {
            String updateServerName = extras.getString("SERVERNAME");
            if (!UsefulBits.isEmpty(updateServerName)) {
                newServer = mServerUtil.getServerInfo(updateServerName);
            }
            activeServerChanged = extras.getBoolean("SERVERACTIVE", false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        callingInstance = getArguments().getInt(INSTANCE);
        if (mSharedPrefs.darkThemeEnabled())
            v = inflater.inflate(R.layout.fragment_add_server_dark, container, false);
        else
            v = inflater.inflate(R.layout.fragment_add_server, container, false);

        permissionFragmentHelper = PermissionFragmentHelper.getInstance(this);
        getLayoutReferences();
        setPreferenceValues();
        return v;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (!isVisibleToUser) {
            if (hasBeenVisibleToUser) writePreferenceValues();
        } else hasBeenVisibleToUser = true;
    }

    private void getLayoutReferences() {
        useSameAddress = (Switch) v.findViewById(R.id.localServer_switch);
        saveButton = (Button) v.findViewById(R.id.save_server);
        server_name_input = (FloatingLabelEditText) v.findViewById(R.id.server_name_input);
        remote_server_input = (FloatingLabelEditText) v.findViewById(R.id.remote_server_input);
        remote_port_input = (FloatingLabelEditText) v.findViewById(R.id.remote_port_input);
        remote_username_input = (FloatingLabelEditText) v.findViewById(R.id.remote_username_input);
        remote_password_input = (FloatingLabelEditText) v.findViewById(R.id.remote_password_input);
        remote_directory_input = (FloatingLabelEditText) v.findViewById(R.id.remote_directory_input);
        remote_protocol_spinner = (Spinner) v.findViewById(R.id.remote_protocol_spinner);
        local_server_input = (FloatingLabelEditText) v.findViewById(R.id.local_server_input);
        local_port_input = (FloatingLabelEditText) v.findViewById(R.id.local_port_input);
        local_username_input = (FloatingLabelEditText) v.findViewById(R.id.local_username_input);
        local_password_input = (FloatingLabelEditText) v.findViewById(R.id.local_password_input);
        local_directory_input = (FloatingLabelEditText) v.findViewById(R.id.local_directory_input);
        local_protocol_spinner = (Spinner) v.findViewById(R.id.local_protocol_spinner);
        local_wifi_spinner = (MultiSelectionSpinner) v.findViewById(R.id.local_wifi);
        cbShowPassword = (CheckBox) v.findViewById(R.id.showpassword);
        cbShowPasswordLocal = (CheckBox) v.findViewById(R.id.showpasswordlocal);

        startScreen_spinner = (Spinner) v.findViewById(R.id.startScreen_spinner);

        btnManualSSID = (Button) v.findViewById(R.id.set_ssid);
        btnManualSSID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialDialog.Builder(getContext())
                        .title(R.string.welcome_ssid_button_prompt)
                        .content(R.string.welcome_msg_no_ssid_found)
                        .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                        .input(null, null, new MaterialDialog.InputCallback() {
                            @Override
                            public void onInput(MaterialDialog dialog, CharSequence input) {
                                Set<String> ssidFromPrefs = mServerUtil.getActiveServer().getLocalServerSsid();
                                final ArrayList<String> ssidListFromPrefs = new ArrayList<>();
                                if (ssidFromPrefs != null) {
                                    if (ssidFromPrefs.size() > 0) {
                                        for (String wifi : ssidFromPrefs) {
                                            ssidListFromPrefs.add(wifi);
                                        }
                                    }
                                }
                                ssidListFromPrefs.add(String.valueOf(input));
                                mServerUtil.getActiveServer().setLocalServerSsid(ssidListFromPrefs);

                                setSsid_spinner();
                            }
                        }).show();
            }
        });

        startScreen_spinner.setVisibility(View.GONE);
        v.findViewById(R.id.startScreen_title).setVisibility(View.GONE);
        v.findViewById(R.id.server_settings_title).setVisibility(View.GONE);

        useSameAddress.setChecked(false);
        final LinearLayout localServerSettingsLayout = (LinearLayout)
                v.findViewById(R.id.local_server_settings);
        localServer_switch = (Switch) v.findViewById(R.id.localServer_switch);
        localServer_switch.setChecked(false);//default setting
        localServer_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (checked) localServerSettingsLayout.setVisibility(View.VISIBLE);
                else localServerSettingsLayout.setVisibility(View.GONE);
            }
        });

        final LinearLayout advancedSettings_layout = (LinearLayout)
                v.findViewById(R.id.advancedSettings_layout);
        advancedSettings_switch = (Switch) v.findViewById(R.id.advancedSettings_switch);
        advancedSettings_layout.setVisibility(View.INVISIBLE);
        advancedSettings_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSharedPrefs.setAdvancedSettingsEnabled(isChecked);

                if (isChecked) advancedSettings_layout.setVisibility(View.VISIBLE);
                else advancedSettings_layout.setVisibility(View.GONE);
            }
        });
        advancedSettings_layout.setVisibility(View.GONE);
        advancedSettings_switch.setChecked(false);
        cbShowPassword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    remote_password_input.getInputWidget().setTransformationMethod(PasswordTransformationMethod.getInstance());
                } else {
                    remote_password_input.getInputWidget().setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                }
            }
        });
        cbShowPasswordLocal.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    local_password_input.getInputWidget().setTransformationMethod(PasswordTransformationMethod.getInstance());
                } else {
                    local_password_input.getInputWidget().setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                }
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkConnectionData();
            }
        });
    }

    private void checkConnectionData() {
        buildServerInfo();

        if (mServerUtil == null)
            mServerUtil = new ServerUtil(getActivity());

        final Domoticz mDomoticz = new Domoticz(getActivity(), AppController.getInstance().getRequestQueue());
        String status = mDomoticz.isConnectionDataComplete(newServer, false);

        if (!UsefulBits.isEmpty(status)) {
            showErrorPopup(getString(R.string.welcome_msg_connectionDataIncomplete) + "\n\n" + status + "\n\n"
                    + getString(R.string.welcome_msg_correctOnPreviousPage));
        } else if (!mDomoticz.isUrlValid(newServer)) {
            showErrorPopup(getString(R.string.welcome_msg_connectionDataInvalid) + "\n\n"
                    + getString(R.string.welcome_msg_correctOnPreviousPage));
        } else if (!isUpdateRequest && !mServerUtil.checkUniqueServerName(newServer)) {
            showErrorPopup("Server name must be unique!");
        } else {
            writePreferenceValues();
        }
    }

    private void showErrorPopup(String error) {
        new AlertDialog.Builder(getActivity())
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle("Failed")
                .setMessage(error)
                .setPositiveButton(this.getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
    }

    private void setPreferenceValues() {
        if (newServer != null) {
            isUpdateRequest = true;
            server_name_input.setInputWidgetText(newServer.getServerName());
            server_name_input.getInputWidget().setEnabled(false);//we dont allow updates on the key
            remote_username_input.setInputWidgetText(newServer.getRemoteServerUsername());
            remote_password_input.setInputWidgetText(newServer.getRemoteServerPassword());
            remote_server_input.setInputWidgetText(newServer.getRemoteServerUrl());
            remote_server_input.requestFocus();
            remote_port_input.setInputWidgetText(newServer.getRemoteServerPort());
            remote_directory_input.setInputWidgetText(newServer.getRemoteServerDirectory());

            local_username_input.setInputWidgetText(newServer.getLocalServerUsername());
            local_password_input.setInputWidgetText(newServer.getLocalServerPassword());
            local_server_input.setInputWidgetText(newServer.getLocalServerUrl());
            local_port_input.setInputWidgetText(newServer.getLocalServerPort());
            local_directory_input.setInputWidgetText(newServer.getLocalServerDirectory());

            localServer_switch.setChecked(newServer.getIsLocalServerAddressDifferent());
            advancedSettings_switch.setChecked(newServer.getIsLocalServerAddressDifferent());
        } else {
            server_name_input.getInputWidget().setEnabled(true);
            remote_username_input.setInputWidgetText("");
        }

        setProtocol_spinner();
        setStartScreen_spinner();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!PermissionsUtil.canAccessLocation(getActivity())) {
                permissionFragmentHelper.request(PermissionsUtil.INITIAL_LOCATION_PERMS);
            } else
                setSsid_spinner();
        } else
            setSsid_spinner();
    }

    private void setSsid_spinner() {
        Set<String> ssidFromPrefs = mServerUtil.getActiveServer().getLocalServerSsid();
        final ArrayList<String> ssidListFromPrefs = new ArrayList<>();
        //noinspection SpellCheckingInspection
        final ArrayList<String> ssids = new ArrayList<>();
        if (ssidFromPrefs != null) {
            if (ssidFromPrefs.size() > 0) {
                for (String wifi : ssidFromPrefs) {
                    ssids.add(wifi);
                    ssidListFromPrefs.add(wifi);
                }
            }
        }

        mPhoneConnectionUtil = new PhoneConnectionUtil(getActivity(), new WifiSSIDListener() {
            @Override
            public void ReceiveSSIDs(CharSequence[] ssidFound) {
                if (ssidFound == null || ssidFound.length < 1) {
                    // No wifi ssid nearby found!
                    local_wifi_spinner.setEnabled(false);                       // Disable spinner
                    ssids.add(getString(R.string.welcome_msg_no_ssid_found));
                    // Set selection to the 'no ssids found' message to inform user
                    local_wifi_spinner.setItems(ssids);
                    local_wifi_spinner.setSelection(0);
                } else {
                    for (CharSequence ssid : ssidFound) {
                        if (!UsefulBits.isEmpty(ssid) && !ssids.contains(ssid))
                            ssids.add(ssid.toString());  // Prevent double SSID's
                    }
                    local_wifi_spinner.setTitle(R.string.welcome_ssid_spinner_prompt);
                    local_wifi_spinner.setItems(ssids);

                    local_wifi_spinner.setSelection(ssidListFromPrefs);
                }
                mPhoneConnectionUtil.stopReceiver();
            }
        });
        mPhoneConnectionUtil.startSsidScan();
    }

    private void setProtocol_spinner() {
        String[] protocols = getResources().getStringArray(R.array.remote_server_protocols);
        ArrayAdapter<String> protocolAdapter
                = new ArrayAdapter<>(getActivity(), R.layout.spinner_list_item, protocols);
        remote_protocol_spinner.setAdapter(protocolAdapter);
        remote_protocol_spinner.setSelection(getPrefsDomoticzRemoteSecureIndex());
        remote_protocol_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView,
                                       View view, int position, long id) {
                remoteProtocolSelectedPosition = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        local_protocol_spinner.setAdapter(protocolAdapter);
        local_protocol_spinner.setSelection(getPrefsDomoticzLocalSecureIndex());
        local_protocol_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView,
                                       View view, int position, long id) {
                localProtocolSelectedPosition = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    private void setStartScreen_spinner() {
        if (mSharedPrefs == null)
            mSharedPrefs = new SharedPrefUtil(getActivity().getApplicationContext());

        String[] startScreens = getResources().getStringArray(R.array.drawer_actions);
        ArrayAdapter<String> startScreenAdapter
                = new ArrayAdapter<>(getActivity(), R.layout.spinner_list_item, startScreens);

        startScreen_spinner.setAdapter(startScreenAdapter);
        if (mSharedPrefs != null)
            startScreen_spinner.setSelection(mSharedPrefs.getStartupScreenIndex());
        startScreen_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView,
                                       View view, int position, long id) {
                startScreenSelectedPosition = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    private void buildServerInfo() {

        newServer = new ServerInfo();
        newServer.setRemoteServerUsername(
                remote_username_input.getInputWidgetText().toString().trim());
        newServer.setRemoteServerPassword(
                remote_password_input.getInputWidgetText().toString().trim());
        newServer.setRemoteServerUrl(
                remote_server_input.getInputWidgetText().toString().trim());
        newServer.setRemoteServerPort(
                remote_port_input.getInputWidgetText().toString().trim());
        newServer.setRemoteServerDirectory(
                remote_directory_input.getInputWidgetText().toString().trim());
        newServer.setRemoteServerSecure(
                getSpinnerDomoticzRemoteSecureBoolean());
        newServer.setEnabled(false);

        if (!useSameAddress.isChecked()) {
            newServer.setLocalSameAddressAsRemote();
            newServer.setIsLocalServerAddressDifferent(false);
        } else {
            newServer.setLocalServerUsername(
                    local_username_input.getInputWidgetText().toString().trim());
            newServer.setLocalServerPassword(
                    local_password_input.getInputWidgetText().toString().trim());
            newServer.setLocalServerUrl(
                    local_server_input.getInputWidgetText().toString().trim());
            newServer.setLocalServerPort(
                    local_port_input.getInputWidgetText().toString().trim());
            newServer.setLocalServerDirectory(
                    local_directory_input.getInputWidgetText().toString().trim());
            newServer.setLocalServerSecure(
                    getSpinnerDomoticzLocalSecureBoolean());
            newServer.setIsLocalServerAddressDifferent(true);
        }

        newServer.setEnabled(activeServerChanged);
        newServer.setServerName(server_name_input.getInputWidgetText().toString());
        newServer.setLocalServerSsid(local_wifi_spinner.getSelectedStrings());
    }

    private void writePreferenceValues() {
        buildServerInfo();
        if (!isUpdateRequest) {
            if (mServerUtil.addDomoticzServer(newServer)) {
                ((ServerSettingsActivity) getActivity()).ServerAdded(true);
            } else {
                showErrorPopup("Server name must be unique!");
            }
        } else {
            mServerUtil.putServerInList(newServer);
            mServerUtil.saveDomoticzServers(false);
            ((ServerSettingsActivity) getActivity()).ServerAdded(true);
            if (activeServerChanged)
                mServerUtil.setActiveServer(newServer);
        }
    }

    private boolean getSpinnerDomoticzRemoteSecureBoolean() {
        String[] protocols = getResources().getStringArray(R.array.remote_server_protocols);
        return protocols[remoteProtocolSelectedPosition].equalsIgnoreCase(DomoticzValues.Protocol.SECURE);
    }

    private boolean getSpinnerDomoticzLocalSecureBoolean() {
        String[] protocols = getResources().getStringArray(R.array.remote_server_protocols);
        return protocols[localProtocolSelectedPosition].equalsIgnoreCase(DomoticzValues.Protocol.SECURE);
    }

    private int getPrefsDomoticzRemoteSecureIndex() {
        boolean isSecure = false;
        String[] protocols = getResources().getStringArray(R.array.remote_server_protocols);
        int i = 0;
        String protocolString;

        if (isSecure) protocolString = DomoticzValues.Protocol.SECURE;
        else protocolString = DomoticzValues.Protocol.INSECURE;

        for (String protocol : protocols) {
            if (protocol.equalsIgnoreCase(protocolString)) return i;
            i++;
        }
        return i;
    }

    private int getPrefsDomoticzLocalSecureIndex() {
        boolean isSecure = false;
        String[] protocols = getResources().getStringArray(R.array.remote_server_protocols);
        int i = 0;
        String protocolString;

        if (isSecure) protocolString = DomoticzValues.Protocol.SECURE;
        else protocolString = DomoticzValues.Protocol.INSECURE;

        for (String protocol : protocols) {
            if (protocol.equalsIgnoreCase(protocolString)) return i;
            i++;
        }
        return i;
    }

    @Override
    public void onStop() {
        if (mPhoneConnectionUtil != null)
            mPhoneConnectionUtil.stopReceiver();

        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onPermissionDeclined(@NonNull String[] permissionName) {
        Log.i("onPermissionDeclined", "Permission(s) " + Arrays.toString(permissionName) + " Declined");
        String[] neededPermission = PermissionFragmentHelper.declinedPermissions(this, PermissionsUtil.INITIAL_LOCATION_PERMS);
        AlertDialog alert = PermissionsUtil.getAlertDialog(getActivity(), permissionFragmentHelper, getActivity().getString(R.string.permission_title),
                getActivity().getString(R.string.permission_desc_location), neededPermission, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mPhoneConnectionUtil != null)
                            mPhoneConnectionUtil.stopReceiver();
                    }
                });
        if (!alert.isShowing()) {
            alert.show();
        }
    }

    @Override
    public void onPermissionPreGranted(@NonNull String permissionsName) {
        Log.i("onPermissionPreGranted", "Permission( " + permissionsName + " ) preGranted");
    }

    @Override
    public void onPermissionNeedExplanation(@NonNull String permissionName) {
        Log.i("NeedExplanation", "Permission( " + permissionName + " ) needs Explanation");
    }

    @Override
    public void onPermissionReallyDeclined(@NonNull String permissionName) {
        Log.i("ReallyDeclined", "Permission " + permissionName + " can only be granted from settingsScreen");
    }

    @Override
    public void onNoPermissionNeeded() {
        Log.i("onNoPermissionNeeded", "Permission(s) not needed");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionFragmentHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onPermissionGranted(@NonNull String[] permissionName) {
        Log.i("onPermissionGranted", "Permission(s) " + Arrays.toString(permissionName) + " Granted");
        if (PermissionsUtil.canAccessLocation(getActivity()))
            setSsid_spinner();
    }
}
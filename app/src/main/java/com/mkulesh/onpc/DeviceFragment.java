/*
 * Copyright (C) 2018. Mikhail Kulesh
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program.
 */

package com.mkulesh.onpc;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.mkulesh.onpc.iscp.BroadcastSearch;
import com.mkulesh.onpc.iscp.EISCPMessage;
import com.mkulesh.onpc.iscp.ISCPMessage;
import com.mkulesh.onpc.iscp.messages.AutoPowerMsg;
import com.mkulesh.onpc.iscp.messages.DigitalFilterMsg;
import com.mkulesh.onpc.iscp.messages.DimmerLevelMsg;
import com.mkulesh.onpc.iscp.messages.FirmwareUpdateMsg;
import com.mkulesh.onpc.iscp.messages.GoogleCastAnalyticsMsg;
import com.mkulesh.onpc.utils.Logging;

import java.net.InetAddress;
import java.util.HashSet;

public class DeviceFragment extends BaseFragment implements View.OnClickListener
{
    private EditText deviceName, devicePort;

    public DeviceFragment()
    {
        // Empty constructor required for fragment subclasses
    }

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        initializeFragment(inflater, container, R.layout.device_fragment);

        final AppCompatImageButton btnConnect = prepareImageButton(R.id.device_connect, null);
        setButtonEnabled(btnConnect, true);
        btnConnect.setOnClickListener(this);

        deviceName = rootView.findViewById(R.id.device_name);
        deviceName.setText(preferences.getString(DeviceFragment.SERVER_NAME, ""));
        devicePort = rootView.findViewById(R.id.device_port);
        devicePort.setText(Integer.toString(
                preferences.getInt(DeviceFragment.SERVER_PORT, BroadcastSearch.ISCP_PORT)));

        final AppCompatImageButton btnSearchDevice = prepareImageButton(R.id.btn_search_device, null);
        setButtonEnabled(btnSearchDevice, true);
        btnSearchDevice.setOnClickListener(this);

        prepareImageButton(R.id.btn_firmware_update, new FirmwareUpdateMsg(FirmwareUpdateMsg.Status.NET));
        prepareImageButton(R.id.device_dimmer_level_toggle, new DimmerLevelMsg(DimmerLevelMsg.Level.TOGGLE));
        prepareImageButton(R.id.device_digital_filter_toggle, new DigitalFilterMsg(DigitalFilterMsg.Filter.TOGGLE));
        prepareImageButton(R.id.device_auto_power_toggle, new AutoPowerMsg(AutoPowerMsg.Status.TOGGLE));
        prepareImageButton(R.id.google_cast_analytics_toggle, null);

        update(null, null);
        return rootView;
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onClick(View v)
    {
        if (v.getId() == R.id.device_connect)
        {
            final String serverName = ((EditText) rootView.findViewById(R.id.device_name)).getText().toString();
            final String serverPortStr = ((EditText) rootView.findViewById(R.id.device_port)).getText()
                    .toString();
            final int serverPort = Integer.parseInt(serverPortStr);
            if (activity.connectToServer(serverName, serverPort))
            {
                SharedPreferences.Editor prefEditor = preferences.edit();
                prefEditor.putString(SERVER_NAME, serverName);
                prefEditor.putInt(SERVER_PORT, serverPort);
                prefEditor.commit();
            }
        }
        if (v.getId() == R.id.btn_search_device)
        {
            try
            {
                final BroadcastSearch bs = new BroadcastSearch(activity,
                        new BroadcastSearch.SearchListener()
                        {
                            // These methods will be called from GUI thread
                            @Override
                            public void onDeviceFound(InetAddress deviceAddress, EISCPMessage response)
                            {
                                DeviceFragment.this.onDeviceFound(deviceAddress, response);
                            }

                            @Override
                            public void noDevice()
                            {
                                DeviceFragment.this.noDevice();
                            }
                        }, 5000, 5);
                bs.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
            }
            catch (Exception e)
            {
                Logging.info(this, "Error by device search: " + e.getLocalizedMessage());
                noDevice();
            }
        }
    }

    void onDeviceFound(InetAddress deviceAddress, EISCPMessage response)
    {
        deviceName.setText(deviceAddress.getHostAddress());
        devicePort.setText(Integer.toString(BroadcastSearch.ISCP_PORT));
    }

    void noDevice()
    {
        Toast.makeText(activity, R.string.error_connection_timeout, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void updateStandbyView(@Nullable final State state, @NonNull final HashSet<State.ChangeType> eventChanges)
    {
        if (state != null)
        {
            updateDeviceProperties(state);
        }
    }

    @Override
    protected void updateActiveView(@NonNull final State state, @NonNull final HashSet<State.ChangeType> eventChanges)
    {
        if (eventChanges.contains(State.ChangeType.COMMON))
        {
            Logging.info(this, "Updating device properties");
            updateDeviceProperties(state);
        }
    }

    private void updateDeviceProperties(@NonNull final State state)
    {
        if (!state.deviceProperties.isEmpty())
        {
            ((TextView) rootView.findViewById(R.id.device_brand)).setText(state.deviceProperties.get("brand"));
            ((TextView) rootView.findViewById(R.id.device_model)).setText(state.deviceProperties.get("model"));
            ((TextView) rootView.findViewById(R.id.device_year)).setText(state.deviceProperties.get("year"));
            // Firmware version
            {
                StringBuilder version = new StringBuilder();
                version.append(state.deviceProperties.get("firmwareversion"));
                if (state.firmwareStatus != FirmwareUpdateMsg.Status.NONE)
                {
                    version.append(", ").append(
                            activity.getResources().getString(state.firmwareStatus.getDescriptionId()));
                }
                ((TextView) rootView.findViewById(R.id.device_firmware)).setText(version.toString());
            }
            // Update button
            {
                final AppCompatImageButton b = rootView.findViewById(R.id.btn_firmware_update);
                b.setVisibility(state.firmwareStatus == FirmwareUpdateMsg.Status.NEW_VERSION ?
                        View.VISIBLE : View.GONE);
                if (b.getVisibility() == View.VISIBLE)
                {
                    setButtonEnabled(b, true);
                }
            }
        }

        ((TextView) rootView.findViewById(R.id.google_cast_version)).setText(state.googleCastVersion);

        // Dimmer level
        if (state.isControlExists(state.CONTROL_DIMMER))
        {
            rootView.findViewById(R.id.device_dimmer_level_layout).setVisibility(View.VISIBLE);
            ((TextView) rootView.findViewById(R.id.device_dimmer_level)).setText(state.dimmerLevel.getDescriptionId());
            setButtonEnabled(R.id.device_dimmer_level_toggle, state.isOn());
        }
        else
        {
            rootView.findViewById(R.id.device_dimmer_level_layout).setVisibility(View.GONE);
        }

        // Digital filter
        if (state.isControlExists(state.CONTROL_DIGITAL_FILTER))
        {
            rootView.findViewById(R.id.device_digital_filter_layout).setVisibility(View.VISIBLE);
            ((TextView) rootView.findViewById(R.id.device_digital_filter)).setText(state.digitalFilter.getDescriptionId());
            setButtonEnabled(R.id.device_digital_filter_toggle, state.isOn());
        }
        else
        {
            rootView.findViewById(R.id.device_digital_filter_layout).setVisibility(View.GONE);
        }

        // Auto power
        {
            ((TextView) rootView.findViewById(R.id.device_auto_power)).setText(state.autoPower.getDescriptionId());
            setButtonEnabled(R.id.device_auto_power_toggle, state.isOn());
        }

        // Google Cast analytics
        {
            ((TextView) rootView.findViewById(R.id.google_cast_analytics)).setText(state.googleCastAnalytics.getDescriptionId());
            final GoogleCastAnalyticsMsg toggleMsg = new GoogleCastAnalyticsMsg(
                    (state.googleCastAnalytics == GoogleCastAnalyticsMsg.Status.OFF) ?
                            GoogleCastAnalyticsMsg.Status.ON : GoogleCastAnalyticsMsg.Status.OFF);
            prepareImageButton(R.id.google_cast_analytics_toggle, toggleMsg);
            setButtonEnabled(R.id.google_cast_analytics_toggle, state.isOn());
        }
    }

    protected AppCompatImageButton prepareImageButton(@IdRes int buttonId, final ISCPMessage msg)
    {
        final AppCompatImageButton b = rootView.findViewById(buttonId);
        prepareButtonListeners(b, msg);
        setButtonEnabled(b, false);
        return b;
    }
}

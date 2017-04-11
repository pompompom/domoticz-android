package nl.hnogames.domoticz.Service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.ArrayList;

import nl.hnogames.domoticz.Containers.LocationInfo;
import nl.hnogames.domoticz.R;
import nl.hnogames.domoticz.Utils.NotificationUtil;
import nl.hnogames.domoticz.Utils.SharedPrefUtil;
import nl.hnogames.domoticz.Utils.UsefulBits;
import nl.hnogames.domoticz.app.AppController;
import nl.hnogames.domoticzapi.Containers.DevicesInfo;
import nl.hnogames.domoticzapi.Domoticz;
import nl.hnogames.domoticzapi.DomoticzValues;
import nl.hnogames.domoticzapi.Interfaces.DevicesReceiver;
import nl.hnogames.domoticzapi.Interfaces.setCommandReceiver;

public class GeofenceReceiver extends BroadcastReceiver
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private final String TAG = "GEOFENCE";
    Intent broadcastIntent = new Intent();
    private Context context;
    private SharedPrefUtil mSharedPrefs;
    private Domoticz domoticz;

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;
        if (mSharedPrefs == null)
            mSharedPrefs = new SharedPrefUtil(context);

        GeofencingEvent geoFenceEvent = GeofencingEvent.fromIntent(intent);
        if (geoFenceEvent.hasError()) {
            handleError(intent, geoFenceEvent);
        } else {
            handleEnterExit(geoFenceEvent);
        }
    }

    private void handleError(Intent intent, GeofencingEvent event) {
        //TODO: Implement error handler
    }

    private void handleEnterExit(GeofencingEvent geoFenceEvent) {
        try {
            if (geoFenceEvent.hasError()) {
                int errorCode = geoFenceEvent.getErrorCode();
                Log.e(TAG, "Location Services error: " + errorCode);
            } else {
                int transitionType = geoFenceEvent.getGeofenceTransition();
                if (Geofence.GEOFENCE_TRANSITION_ENTER == transitionType) {
                    for (Geofence geofence : geoFenceEvent.getTriggeringGeofences()) {
                        LocationInfo locationFound =
                                mSharedPrefs.getLocation(Integer.valueOf(geofence.getRequestId()));
                        Log.d(TAG, "Triggered entering a geofence location: "
                                + locationFound.getName());
                        String text = String.format(
                                context.getString(R.string.geofence_location_entering),
                                locationFound.getName());
                        NotificationUtil.sendSimpleNotification(text,
                                context.getString(R.string.geofence_location_entering_text), 0, context);

                        if (locationFound.getSwitchIdx() > 0) {
                            handleSwitch(locationFound.getSwitchIdx(), locationFound.getSwitchPassword(), true, locationFound.getValue());
                        }
                    }
                } else if (Geofence.GEOFENCE_TRANSITION_DWELL == transitionType) {
                    for (Geofence geofence : geoFenceEvent.getTriggeringGeofences()) {
                        LocationInfo locationFound =
                                mSharedPrefs.getLocation(Integer.valueOf(geofence.getRequestId()));
                        if (locationFound != null) {
                            Log.d(TAG, "Triggered dwelling a geofence location: "
                                    + locationFound.getName());
                            //keep the switch on for dwelling, but don't send a notification
                            if (locationFound.getSwitchIdx() > 0) {
                                handleSwitch(locationFound.getSwitchIdx(), locationFound.getSwitchPassword(), true, locationFound.getValue());
                            }
                        }
                    }
                } else if (Geofence.GEOFENCE_TRANSITION_EXIT == transitionType) {
                    for (Geofence geofence : geoFenceEvent.getTriggeringGeofences()) {
                        LocationInfo locationFound
                                = mSharedPrefs.getLocation(Integer.valueOf(geofence.getRequestId()));
                        Log.d(TAG, "Triggered leaving a geofence location: "
                                + locationFound.getName());

                        String text = String.format(
                                context.getString(R.string.geofence_location_leaving),
                                locationFound.getName());
                        NotificationUtil.sendSimpleNotification(text,
                                context.getString(R.string.geofence_location_leaving_text), 0, context);

                        if (locationFound.getSwitchIdx() > 0)
                            handleSwitch(locationFound.getSwitchIdx(), locationFound.getSwitchPassword(), false, locationFound.getValue());
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void handleSwitch(final int idx, final String password, final boolean checked, final String value) {
        if (domoticz == null)
            domoticz = new Domoticz(context, AppController.getInstance().getRequestQueue());

        domoticz.getDevice(new DevicesReceiver() {
            @Override
            public void onReceiveDevices(ArrayList<DevicesInfo> mDevicesInfo) {
            }

            @Override
            public void onReceiveDevice(DevicesInfo mDevicesInfo) {
                if (mDevicesInfo == null)
                    return;

                if(mDevicesInfo.getStatusBoolean() != checked) {
                    int jsonAction;
                    int jsonUrl = DomoticzValues.Json.Url.Set.SWITCHES;
                    int jsonValue = 0;

                    if (mDevicesInfo.getSwitchTypeVal() == DomoticzValues.Device.Type.Value.BLINDS ||
                            mDevicesInfo.getSwitchTypeVal() == DomoticzValues.Device.Type.Value.BLINDPERCENTAGE) {
                        if (checked) jsonAction = DomoticzValues.Device.Switch.Action.OFF;
                        else jsonAction = DomoticzValues.Device.Switch.Action.ON;
                    } else {
                        if (checked) jsonAction = DomoticzValues.Device.Switch.Action.ON;
                        else jsonAction = DomoticzValues.Device.Switch.Action.OFF;
                    }

                    switch (mDevicesInfo.getSwitchTypeVal()) {
                        case DomoticzValues.Device.Type.Value.PUSH_ON_BUTTON:
                            jsonAction = DomoticzValues.Device.Switch.Action.ON;
                            break;
                        case DomoticzValues.Device.Type.Value.PUSH_OFF_BUTTON:
                            jsonAction = DomoticzValues.Device.Switch.Action.OFF;
                            break;
                    }

                    domoticz.setAction(idx, jsonUrl, jsonAction, jsonValue, password, new setCommandReceiver() {
                        @Override
                        public void onReceiveResult(String result) {
                            Log.d(TAG, result);
                        }

                        @Override
                        public void onError(Exception error) {
                            if (error != null)
                                onErrorHandling(error);
                        }
                    });
                }
                else{
                    Log.i("GEOFENCE", "Switch was already turned " + (checked ? "on": "off"));
                }
            }

            @Override
            public void onError(Exception error) {
                if (error != null)
                    onErrorHandling(error);
            }

        }, idx, false);
    }

    private void onErrorHandling(Exception error) {
        if (error != null) {
            Toast.makeText(
                    context,
                    "Domoticz: " +
                            context.getString(R.string.unable_to_get_switches),
                    Toast.LENGTH_SHORT).show();

            if (domoticz != null && UsefulBits.isEmpty(domoticz.getErrorMessage(error)))
                Log.e(TAG, domoticz.getErrorMessage(error));
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
    }

    @Override
    public void onConnectionSuspended(int cause) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
    }
}
package nisargpatel.inertialnavigation.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import nisargpatel.inertialnavigation.activity.GraphActivity;
import nisargpatel.inertialnavigation.extra.ExtraFunctions;
import nisargpatel.inertialnavigation.filewriting.DataFileWriter;
import nisargpatel.inertialnavigation.heading.GyroscopeBias;
import nisargpatel.inertialnavigation.heading.InitialOrientation;
import nisargpatel.inertialnavigation.heading.MagneticFieldBias;

public class CalibrationFragment extends DialogFragment implements SensorEventListener {

    public static final String DIALOG_MESSAGE = "To calibrate: set the phone on a flat surface, away from any large metal objects.";

    private static final String FOLDER_NAME = "Inertial_Navigation_Data/Calibration_Fragment";
    private static final String[] DATA_FILE_NAMES = {"Gravity", "Gyroscope-Uncalibrated", "Magnetic-Field-Uncalibrated"};
    private static final String[] DATA_FILE_HEADINGS = {"t;gx;gy;gz;",
                                                        "t;uGx;uGy;uGz;xBias;yBias;zBias;",
                                                        "t;uMx;uMy;uMz;xBias;yBias;zBias;"};

    private DataFileWriter dataFileWriter;

    private GyroscopeBias gyroscopeBias;
    private MagneticFieldBias magneticFieldBias;

    private SensorManager sensorManager;
    private Sensor sensorGravity;
    private Sensor sensorGyroscopeUncalibrated;
    private Sensor sensorMagneticFieldUncalibrated;

    private float[] currGravityValues;
    private float[] currMagneticFieldValues;

    private ProgressDialog progressDialog;

    boolean startGraph;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        try {
            dataFileWriter = new DataFileWriter(FOLDER_NAME, ExtraFunctions.arrayToList(DATA_FILE_NAMES), ExtraFunctions.arrayToList(DATA_FILE_HEADINGS));
        } catch (IOException e) {
            e.printStackTrace();
        }

        progressDialog = new ProgressDialog(getActivity());

        sensorManager = (SensorManager) getActivity().getSystemService(Service.SENSOR_SERVICE);
        sensorGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sensorGyroscopeUncalibrated = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
        sensorMagneticFieldUncalibrated = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);

        gyroscopeBias = new GyroscopeBias(300);
        magneticFieldBias = new MagneticFieldBias(300);

        startGraph = false;

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());
        dialogBuilder
                .setMessage(DIALOG_MESSAGE)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {}
                })
                .setPositiveButton("Calibrate", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {}
                });

        return dialogBuilder.create();
    }

    @Override
    public void onStart() {
        super.onStart();

        AlertDialog alertDialog = (AlertDialog)getDialog();
        if (alertDialog != null) {
            Button negativeButton = alertDialog.getButton(Dialog.BUTTON_NEGATIVE);
            negativeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });

            Button positiveButton = alertDialog.getButton(Dialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sensorManager.registerListener(CalibrationFragment.this, sensorGravity, SensorManager.SENSOR_DELAY_FASTEST);
                    sensorManager.registerListener(CalibrationFragment.this, sensorGyroscopeUncalibrated, SensorManager.SENSOR_DELAY_FASTEST);
                    sensorManager.registerListener(CalibrationFragment.this, sensorMagneticFieldUncalibrated, SensorManager.SENSOR_DELAY_FASTEST);

                    progressDialog = ProgressDialog.show(getActivity(), "Calibrating", "Please wait.", true, false);
                }
            });
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onSensorChanged(SensorEvent event) {

        ArrayList<Float> dataValues = ExtraFunctions.arrayToList(event.values);
        dataValues.add(0, (float) event.timestamp);

        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            currGravityValues = event.values.clone();
            dataFileWriter.writeToFile("Gravity", dataValues);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {
            gyroscopeBias.calcBias(event.values);
            dataFileWriter.writeToFile("Gyroscope-Uncalibrated", dataValues);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
            magneticFieldBias.calcBias(event.values);
            currMagneticFieldValues = new float[] {event.values[0], event.values[1], event.values[2]}; //only need the first 3 values
            dataFileWriter.writeToFile("Magnetic-Field-Uncalibrated", dataValues);
        }

        if (gyroscopeBias.calcBias(event.values) && magneticFieldBias.calcBias(event.values)) {

            Log.d("bias", "magnetic field bias = " + Arrays.toString(gyroscopeBias.getBias()));

            startGraph = true;
            sensorManager.unregisterListener(this);
            progressDialog.dismiss();
            getDialog().dismiss();

        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

        Log.d("checkpoint", "onDismiss called");

        if (startGraph) {

            Intent myIntent = new Intent(getActivity(), GraphActivity.class);
            myIntent = addExtras(myIntent);

            startActivity(myIntent);
        }

    }

    private Intent addExtras(Intent myIntent) {

        float[][] initialOrientation = InitialOrientation.calcOrientation(currGravityValues, currMagneticFieldValues, magneticFieldBias.getBias());

        myIntent.putExtra("user_name", getArguments().getString("user_name", "Default"));
        myIntent.putExtra("stride_length", getArguments().getDouble("stride_length", 2.5));

        myIntent.putExtra("gyroscope_bias", gyroscopeBias.getBias());
        myIntent.putExtra("initial_orientation", ExtraFunctions.floatArrayToStringArray(initialOrientation)); //this is getting serialized since it's a String[][] matrix (putExtra() only accepts vectors)

        return myIntent;

    }


}
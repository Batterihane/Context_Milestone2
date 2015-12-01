package context.milestone2;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LearnerActivity extends Activity
{

    public static final int WINDOW_SIZE = 128;
    public static final int OVERLAP_SIZE = 64;
    private List<Double> measurements;
    private SensorEventListener listener;
    private SensorManager sensor;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        sensor = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);

        measurements = new ArrayList<Double>();
    }

    public void onStartWalking(View view)
    {

        getAccelerometerData();
    }

    public void onStartRunning(View view)
    {
        getAccelerometerData();
    }

    public void onStop(View view)
    {
        sensor.unregisterListener(listener);

        for (int windowStartIndex = 0;
             windowStartIndex + WINDOW_SIZE <= measurements.size();
             windowStartIndex += OVERLAP_SIZE)
        {
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            
            double sum = 0.0;
            for (int i = windowStartIndex; i < windowStartIndex + WINDOW_SIZE; i++)
            {
                sum += measurements.get(i);
            }
            double average = sum / (double) WINDOW_SIZE;
            
            double squaredVarianceSum = 0.0;
            for (int i = windowStartIndex; i < windowStartIndex + WINDOW_SIZE; i++)
            {
                double measurement = measurements.get(i);
                if (measurement < min)
                { min = measurement; }
                if (measurement > max)
                { max = measurement; }
                
                squaredVarianceSum += Math.pow(measurement - average, 2);
            }
            double standardDeviation = Math.sqrt(squaredVarianceSum / WINDOW_SIZE);

        }
        saveToArff();
    }

    private boolean isExternalStorageWritable()
    {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private Instances concatInstances(Instances inst1, Instances inst2)
    {
        for (int i = 0; i < inst2.numInstances(); i++)
        {
            inst1.add(inst2.instance(i));
        }

        return inst1;
    }

    public void saveToArff()
    {
        Attribute minAttribute = new Attribute("min");
        Attribute maxAttribute = new Attribute("max");
        Attribute stdDevAttribute = new Attribute("stdDev");

        FastVector activities = new FastVector(2);
        activities.addElement("walking");
        activities.addElement("running");
        Attribute activityAttribute = new Attribute("activity", activities);

        FastVector features = new FastVector(4);
        features.addElement(minAttribute);
        features.addElement(maxAttribute);
        features.addElement(stdDevAttribute);
        features.addElement(activityAttribute);

        Instances data = new Instances("MyRelation", features, 0);
        Instance dataInstance = new Instance(4);
        dataInstance.setValue(minAttribute, 1.0);
        dataInstance.setValue(maxAttribute, 5);
        dataInstance.setValue(stdDevAttribute, 3.3);
        dataInstance.setValue(activityAttribute, "walking");
        data.add(dataInstance);

        if (isExternalStorageWritable())
        {
            try
            {
                File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(path, "data.arff");

                ArffLoader loader = new ArffLoader();
                loader.setFile(file);
                Instances existingData = loader.getDataSet();

                data = concatInstances(existingData, data);

                ArffSaver saver = new ArffSaver();
                saver.setInstances(data);

                saver.setFile(file);
                saver.writeBatch();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void getAccelerometerData()
    {
        listener = new SensorEventListener()
        {
            public void onSensorChanged(SensorEvent event)
            {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                double norm = Math.sqrt(x * x + y * y + z * z);
                measurements.add(norm);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy)
            {

            }
        };
        Sensor accelerometer = sensor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensor.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }
}

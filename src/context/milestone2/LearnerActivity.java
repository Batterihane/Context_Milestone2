package context.milestone2;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LearnerActivity extends Activity
{
    public static final int NUMBER_OF_ATTRIBUTES = 5;

    public static final int WINDOW_SIZE = 128;
    public static final int OVERLAP_SIZE = 64;

    public static final String WALKING = "walking";
    public static final String RUNNING = "running";

    public static final String OUTPUT_FILENAME = "data.arff";

    private SensorManager sensor;

    private Attribute minAttribute;
    private Attribute maxAttribute;
    private Attribute meanAttribute;
    private Attribute stdDevAttribute;
    private Attribute activityAttribute;

    private String activityLabel;

    private Instances instances;
    private LinkedList<Double> samples = new LinkedList<Double>();

    private File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    private File file = new File(path, OUTPUT_FILENAME);

    private long calibrationStartTime;

    private SensorEventListener sampleListener = new SensorEventListener()
    {
        public void onSensorChanged(final SensorEvent event)
        {
            samples.add(readSensorData(event));
            updateSlidingWindow();
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy)
        {
        }
    };

    private void initialiseRelation()
    {
        minAttribute = new Attribute("min");
        maxAttribute = new Attribute("max");
        meanAttribute = new Attribute("mean");
        stdDevAttribute = new Attribute("stdDev");

        FastVector activities = new FastVector(2);
        activities.addElement("walking");
        activities.addElement("running");
        activityAttribute = new Attribute("activity", activities);

        FastVector features = new FastVector(NUMBER_OF_ATTRIBUTES);
        features.addElement(minAttribute);
        features.addElement(maxAttribute);
        features.addElement(meanAttribute);
        features.addElement(stdDevAttribute);
        features.addElement(activityAttribute);

        instances = new Instances("Activity", features, 0);

        loadExistingData();
    }

    private void loadExistingData()
    {
        if (isExternalStorageAvailable())
        {
            try
            {
                if (file.exists())
                {
                    ArffLoader loader = new ArffLoader();
                    loader.setFile(file);

                    Instances existingData = loader.getDataSet();
                    addManyInstances(existingData);
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private boolean isExternalStorageAvailable()
    {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        sensor = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);

        initialiseRelation();
    }

    public void onStartWalking(final View view)
    {
        stopSampling();
        activityLabel = WALKING;
        startSampling(sampleListener);
        Toast.makeText(this, "Walking", Toast.LENGTH_SHORT).show();
    }

    public void onStartRunning(final View view)
    {
        stopSampling();
        activityLabel = RUNNING;
        startSampling(sampleListener);
        Toast.makeText(this, "Running", Toast.LENGTH_SHORT).show();
    }

    public void onStop(final View view)
    {
        stopSampling();
    }

    public void saveInstances()
    {
        if (isExternalStorageAvailable())
        {
            try
            {
                ArffSaver saver = new ArffSaver();
                saver.setInstances(instances);
                saver.setFile(file);
                saver.writeBatch();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void startSampling(final SensorEventListener listener)
    {
        samples.clear();

        Sensor accelerometer = sensor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        calibrationStartTime = System.currentTimeMillis();
        sensor.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopSampling()
    {
        sensor.unregisterListener(sampleListener);
    }

    private double readSensorData(final SensorEvent event)
    {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        return Math.sqrt(x * x + y * y + z * z);
    }

    private void updateSlidingWindow()
    {
        if (samples.size() == WINDOW_SIZE)
        {
            double min = Collections.min(samples);
            double max = Collections.max(samples);
            double mean = mean(samples);
            double stdDev = standardDeviation(samples);

            displayElapsedTime();

            addInstance(min, max, mean, stdDev, activityLabel);
            saveInstances();

            discardOverlap();
        }
    }

    private void displayElapsedTime()
    {
        long calibrationStopTime = System.currentTimeMillis();
        long elapsedTime = calibrationStopTime - calibrationStartTime;

        Toast.makeText(this, "Elapsed time: " + elapsedTime + " ms", Toast.LENGTH_LONG).show();

        calibrationStartTime = System.currentTimeMillis();
    }

    private double mean(final List<Double> samples)
    {
        double sum = 0.0;

        for (int i = 0; i < WINDOW_SIZE; i++)
        {
            sum += samples.get(i);
        }

        return sum / (double) WINDOW_SIZE;
    }

    private double standardDeviation(final List<Double> samples)
    {
        double mean = mean(samples);
        double squaredVarianceSum = 0.0;

        for (int i = 0; i < WINDOW_SIZE; i++)
        {
            double measurement = samples.get(i);
            squaredVarianceSum += Math.pow(measurement - mean, 2);
        }

        return Math.sqrt(squaredVarianceSum / WINDOW_SIZE);
    }

    private void discardOverlap()
    {
        for (int i = 0; i < OVERLAP_SIZE; i++)
        {
            samples.removeFirst();
        }
    }

    private void addInstance(final double min,
                             final double max,
                             final double mean,
                             final double stdDev,
                             final String label)
    {
        Instance dataInstance = new Instance(NUMBER_OF_ATTRIBUTES);
        dataInstance.setValue(minAttribute, min);
        dataInstance.setValue(maxAttribute, max);
        dataInstance.setValue(meanAttribute, mean);
        dataInstance.setValue(stdDevAttribute, stdDev);
        dataInstance.setValue(activityAttribute, label);
        instances.add(dataInstance);
    }

    private void addManyInstances(final Instances instancesToBeAdded)
    {
        for (int i = 0; i < instancesToBeAdded.numInstances(); i++)
        {
            instances.add(instancesToBeAdded.instance(i));
        }
    }
}

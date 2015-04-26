package devsung.dashcam;

/**
 * Created by Peter on 15-04-04.
 */
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.SystemClock;
import android.util.Log;

public class CrashTester implements SensorEventListener{
    static private float[] gravity = new float[]{0,0,0};
    static private float[] linear_acceleration = new float[]{0,0,0};

    @Override
    public void onAccuracyChanged(Sensor sensor, int arg){

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent){
        accelorometerEvent(sensorEvent);
    }

    public boolean accelorometerEvent(SensorEvent sensorEvent){
        final float alpha = 0.8f;

        //Isolate force of gravity with low-pass filter
        gravity[0] = alpha * gravity[0] + (1 - alpha) * sensorEvent.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * sensorEvent.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * sensorEvent.values[2];

        //Remove the gravity contribution with high-pass filter
        linear_acceleration[0] = sensorEvent.values[0] - gravity[0];
        linear_acceleration[1] = sensorEvent.values[1] - gravity[1];
        linear_acceleration[2] = sensorEvent.values[2] - gravity[2];

        linear_acceleration[0] = (float) Math.sqrt(linear_acceleration[0] * linear_acceleration[0] +
                linear_acceleration[1] * linear_acceleration[1] +
                linear_acceleration[2] * linear_acceleration[2]);
        if (Math.abs(linear_acceleration[0]) > 20){
            return true;
        }
        return false;
    }

}

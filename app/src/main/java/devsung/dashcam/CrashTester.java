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
    static private long timeAcc = -1000;
    static private long lastVideoCall = 0;
    static private float[] gravity = new float[]{0,0,0};
    static private float[] linear_acceleration = new float[]{0,0,0};
    private static Sensors sensors;
    static private long lastUpdate = 0;
    static private float lastPitch = 0;
    static private long currentTime = 0;
    static private float speed;

    @Override
    public void onAccuracyChanged(Sensor sensor, int arg){

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent){
        accelorometerEvent(sensorEvent);
    }

    public static void accelorometerEvent(SensorEvent sensorEvent){
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
            timeAcc = SystemClock.uptimeMillis();
        }
        sensors.sensorChanged(test());
    }

    public MySensor(Sensors sensors) {
        this.sensors = sensors;
    }

    public static void accelerometerEvent1(SensorEvent event) {
        final float alpha = 0.8f;
        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        // Remove the gravity contribution with the high-pass filter.
        linear_acceleration[0] = event.values[0] - gravity[0];
        linear_acceleration[1] = event.values[1] - gravity[1];
        linear_acceleration[2] = event.values[2] - gravity[2];

        linear_acceleration[0] = (float) Math.sqrt(linear_acceleration[0] * linear_acceleration[0] +
                linear_acceleration[1] * linear_acceleration[1] +
                linear_acceleration[2] * linear_acceleration[2]);

        //Log.e("Accelerometer","Accelerometer X: "+linear_acceleration[0]+": Pitch speed "+speed);
        if (Math.abs(linear_acceleration[0]) > 20) {
            timeAcc = SystemClock.uptimeMillis();
        }
        sensors.sensorChanged(test());
    }

    public static boolean myoEvent(float pitch) {
        currentTime = SystemClock.uptimeMillis();
        speed = (pitch - lastPitch) / (currentTime - lastMyoCall) * 1000;
        //Log.e("Myo", "pitch speed: " + speed + " over " + (currentTime - lastMyoCall) + "ms" + ": Accel X " + linear_acceleration[0]);

        if (Math.abs(speed) > 1000) {
            timeMyo = currentTime;
        }
        lastPitch = pitch;
        lastMyoCall = currentTime;
        return test();
    }

    private static boolean test() {
        long currentTime = SystemClock.uptimeMillis();
        if (currentTime - timeMyo < 300 && currentTime - timeAcc < 300) {
            //HelloWorldActivity.mTextView.setText(true+" "+(Math.abs(timeMyoDown-timeAccDown)<50 && Math.abs(timeMyoUp-timeAccUp)<50));
            //if(Math.abs(timeMyoDown-timeAccDown)<150 && Math.abs(timeMyoUp-timeAccUp)<150){
            Log.e("Sensor", "Success!");
            return true;
            //}
        }
        if (currentTime - lastUpdate > 500) {
            //HelloWorldActivity.mTextView.setText(MySensor.speed+" "+MySensor.linear_acceleration[0]+" "+lastPitch);
            lastUpdate = currentTime;
        }
        return false;
    }

    interface Sensors {
        public void sensorChanged(boolean threshold);
    }
}

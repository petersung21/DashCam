package devsung.dashcam;


import android.app.Activity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activitycamera);
        if(savedInstanceState == null){
            getFragmentManager().beginTransaction().replace(R.id.container,Camera2.newInstance()).commit();
        }
    }

}

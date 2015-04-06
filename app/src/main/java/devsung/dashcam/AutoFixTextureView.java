package devsung.dashcam;

import android.view.TextureView;
import android.util.AttributeSet;
import android.content.Context;

/**
 * Created by Peter on 15-04-03.
 */


public class AutoFixTextureView extends TextureView{
    private int mRatioWidth = 0;
    private int mRatioHeight = 0 ;

    public AutoFixTextureView(Context context){
        this(context, null);
    }
    public AutoFixTextureView(Context context, AttributeSet attrs){
        this(context,attrs,0);
    }
    public AutoFixTextureView(Context context, AttributeSet attrs, int defStyle){
        super (context,attrs,defStyle);
    }

    /*
    Setting the aspect ratio of the views. It is based on ratio calculated from the parameters
     */
    public void setAspectRatio(int width, int height){
        if (width<0 || height<0){
            throw new IllegalArgumentException("Size must be greater than 0");
        }else {
            mRatioWidth = width;
            mRatioHeight = height;
            requestLayout();
        }
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (mRatioWidth == 0 || mRatioHeight == 0){
            setMeasuredDimension(width,height);
        }else{
            if (width < height * mRatioWidth / mRatioHeight){
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            }else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }
}

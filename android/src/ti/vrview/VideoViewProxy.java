package ti.vrview;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.io.TiFile;
import org.appcelerator.titanium.io.TiFileFactory;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.view.TiUIView;
import org.appcelerator.titanium.util.TiSensorHelper;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import ti.modules.titanium.filesystem.FileProxy;
import ti.vrview.VrviewModule;
import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Message;
import android.util.Pair;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.google.vr.sdk.widgets.video.*;
import com.google.vr.sdk.widgets.video.VrVideoView.Options;

@Kroll.proxy(creatableInModule = VrviewModule.class)
public class VideoViewProxy extends TiViewProxy implements
		SensorEventListener {

	TiUIView view;
	KrollProxy proxy;
	public boolean loadImageSuccessful;
	private int type = VrviewModule.TYPE_MONO;
	private static final int MSG_FIRST_ID = TiViewProxy.MSG_LAST_ID + 1;
	private static final int MSG_START = MSG_FIRST_ID + 500;
	private static final int MSG_STOP = MSG_FIRST_ID + 501;
	public static final int LOAD_VIDEO_STATUS_UNKNOWN = 0;
	  public static final int LOAD_VIDEO_STATUS_SUCCESS = 1;
	  public static final int LOAD_VIDEO_STATUS_ERROR = 2;

	  private int loadVideoStatus = LOAD_VIDEO_STATUS_UNKNOWN;
	private static final String LCAT = "TiVR";

	
	private Uri fileUriOfVideo;
	private BackgroundVideoLoaderTask backgroundVideoLoaderTask;
	public VrVideoView videoWidgetView;
	KrollFunction headRotationCallback = null;
	KrollFunction onLoadCallback = null;

	private static Context ctx = TiApplication.getInstance()
			.getApplicationContext();
	private static SensorManager sensorManager = TiSensorHelper
			.getSensorManager();

	private float[] headRotation = new float[2];
	private boolean fullscreenButtonEnabled = false;
	private boolean infoButtonEnabled = false;
	private boolean stereoModeButtonEnabled = false;
	private boolean touchTrackingEnabled = true;
	private boolean transitionViewEnabled = false;
	private int sensorDelay = SensorManager.SENSOR_DELAY_UI;

	private class VideoView extends TiUIView {
		private Options videoOptions = new Options();

		public VideoView(final TiViewProxy proxy) {
			super(proxy);
			this.proxy = proxy;
			Activity ctx = proxy.getActivity();
			Log.d(LCAT,
					"Start PanoramaView with " + fileUriOfVideo.toString());

			LinearLayout container = new LinearLayout(ctx);
			container.setLayoutParams(new LayoutParams(
					LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
			videoWidgetView = new VrVideoView(ctx);
			// setting all params:
			videoWidgetView.setInfoButtonEnabled(infoButtonEnabled);
			videoWidgetView.setStereoModeButtonEnabled(stereoModeButtonEnabled);
			videoWidgetView.setFullscreenButtonEnabled(fullscreenButtonEnabled);
			videoWidgetView.setTouchTrackingEnabled(touchTrackingEnabled);
			videoWidgetView.setTransitionViewEnabled(transitionViewEnabled);
			sensorManager.registerListener(VideoViewProxy.this,
					sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),sensorDelay);

			//videoWidgetView.setEventListener(new ActivityEventListener());
			videoOptions.inputType = type;
			if (backgroundVideoLoaderTask != null) {
				backgroundVideoLoaderTask.cancel(true);
			}
			backgroundVideoLoaderTask = new BackgroundVideoLoaderTask();
			Log.d(LCAT, "Starting backgroundLoader");
			backgroundVideoLoaderTask.execute(Pair.create(fileUriOfVideo,
					videoOptions));
			container.addView(videoWidgetView);
			setNativeView(container);
		}
	}

	// Constructor
	public VideoViewProxy() {
		super();
	}

	@Override
	public boolean handleMessage(Message msg) {
		AsyncResult result = null;
		switch (msg.what) {

		case MSG_START: {
			result = (AsyncResult) msg.obj;
			handleStart();
			result.setResult(null);
			return true;
		}
		case MSG_STOP: {
			result = (AsyncResult) msg.obj;
			handleStop();
			result.setResult(null);
			return true;
		}
		default: {
			return super.handleMessage(msg);
		}
		}
	}

	@Kroll.method
	public void start() {
		if (TiApplication.isUIThread()) {
			handleStart();
		} else {
			TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(
					MSG_START));

		}
	}

	private void handleStart() {
		videoWidgetView.resumeRendering();
	}

	@Kroll.method
	public void stop() {
		if (TiApplication.isUIThread()) {
			handleStop();
		} else {
			TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(
					MSG_STOP));

		}
	}

	private void handleStop() {
		videoWidgetView.pauseRendering();
	}

	@Override
	public TiUIView createView(Activity activity) {
		Log.d(LCAT, "TiUIView createView");
		view = new VideoView(this);
		view.getLayoutParams().autoFillsHeight = true;
		view.getLayoutParams().autoFillsWidth = true;
		return view;
	}

	// Handle creation options
	@Override
	public void handleCreationDict(KrollDict opts) {
		super.handleCreationDict(opts);
		if (opts.containsKeyAndNotNull(TiC.PROPERTY_TYPE)) {
			type = opts.getInt(TiC.PROPERTY_TYPE);
		}
		if (opts.containsKeyAndNotNull("changed")) {
			headRotationCallback = (KrollFunction) opts.get("changed");
		}
		if (opts.containsKeyAndNotNull("onload")) {
			onLoadCallback = (KrollFunction) opts.get("onload");
		}
		if (opts.containsKeyAndNotNull("fullscreenButtonEnabled")) {
			fullscreenButtonEnabled = opts
					.getBoolean("fullscreenButtonEnabled");
		}
		if (opts.containsKeyAndNotNull("infoButtonEnabled")) {
			infoButtonEnabled = opts.getBoolean("infoButtonEnabled");
		}
		if (opts.containsKeyAndNotNull("stereoModeButtonEnabled")) {
			stereoModeButtonEnabled = opts
					.getBoolean("stereoModeButtonEnabled");
		}
		if (opts.containsKeyAndNotNull("touchTrackingEnabled ")) {
			touchTrackingEnabled = opts.getBoolean("touchTrackingEnabled ");
		}
		if (opts.containsKeyAndNotNull("transitionViewEnabled")) {
			transitionViewEnabled = opts.getBoolean("transitionViewEnabled");
		}

		if (opts.containsKeyAndNotNull(TiC.PROPERTY_IMAGE)) {
			Log.d(LCAT, TiC.PROPERTY_IMAGE + " found");
			Object inputValue = opts.get(TiC.PROPERTY_IMAGE);
			TiBaseFile inputFile = null;
			if (inputValue instanceof TiFile) {
				Log.d(LCAT, "image  TiFile");
				inputFile = TiFileFactory.createTitaniumFile(
						((TiFile) inputValue).getFile().getAbsolutePath(),
						false);
			} else {
				if (inputValue instanceof FileProxy) {
					Log.d(LCAT, "image  FileProxy");
					inputFile = ((FileProxy) inputValue).getBaseFile();
				} else {
					if (inputValue instanceof TiBaseFile) {
						Log.d(LCAT, "image  TiBaseFile");
						inputFile = (TiBaseFile) inputValue;
					} else {
						Log.d(LCAT, "image  String " + inputValue.toString());
						inputFile = TiFileFactory.createTitaniumFile(
								inputValue.toString(), false);
					}
				}
			}
			if (inputFile.exists()) {
				fileUriOfVideo = Uri.fromFile(inputFile.getNativeFile());
				Log.d(LCAT, "Uri=" + fileUriOfVideo.toString());

			} else
				Log.e(LCAT, "(pano) file not exists");

		} else
			Log.w(LCAT, "image missing");

	}

	/**
	 * Helper class to manage threading.
	 */
	

	private class ActivityEventListener extends VrVideoEventListener {
		/**
		 * Called by pano widget on the UI thread when it's done loading the
		 * image.
		 */
		@Override
		public void onLoadSuccess() {
			Log.d(LCAT, "VrPanoramaEventListener: success");
			loadImageSuccessful = true;
			if (onLoadCallback != null) {
				onLoadCallback.call(getKrollObject(), new KrollDict());
			}
		}

		/**
		 * Called by pano widget on the UI thread on any asynchronous error.
		 */
		@Override
		public void onLoadError(String errorMessage) {
			Log.e(LCAT, "VrPanoramaEventListener: error");
			loadImageSuccessful = false;

		}
	}

	
	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		videoWidgetView.getHeadRotation(headRotation);
		if (headRotationCallback != null) {
			KrollDict dict = new KrollDict();
			dict.put("yaw", headRotation[0]);
			dict.put("pitch", headRotation[1]);
			headRotationCallback.call(getKrollObject(), dict);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub

	}
	
	/**
	   * Helper class to manage threading.
	   */
	  class BackgroundVideoLoaderTask extends AsyncTask<Pair<Uri, Options>, Void, Boolean> {
	    @Override
	    protected Boolean doInBackground(Pair<Uri, Options>... fileInformation) {
	      try {
	         
	         videoWidgetView.loadVideo(fileInformation[0].first, fileInformation[0].second);
	        
	      } catch (IOException e) {
	        // An error here is normally due to being unable to locate the file.
	        loadVideoStatus = LOAD_VIDEO_STATUS_ERROR;
	        // Since this is a background thread, we need to switch to the main thread to show a toast.
	        videoWidgetView.post(new Runnable() {
	          @Override
	          public void run() {
	            
	          }
	        });
	       
	      }

	      return true;
	    }
	  }
}
package com.samss;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PulseInput;
import ioio.lib.api.PulseInput.PulseMode;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.AbstractIOIOActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.sip.SipAudioCall;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;



@SuppressWarnings("deprecation")
public class SAMSSActivity extends AbstractIOIOActivity implements  OnUtteranceCompletedListener, LocationListener{
	
	/////////////////////////////////////////////////////
	//	UI ELEMENTS
	//	
	
	//
	//	GENERAL
	//
	private TextView tv1_;
	private TextView roll_tv; 		// declare X axis object
	private TextView pitch_tv; 		// declare Y axis object
	//private TextView yaw; 		// declare Z axis object
	

	//
	//	BLINDSPOT
	//	
	private Button calibrateButton;
	private Button rightSensorButton;
	private Button leftSensorButton;
	private Button setDistThreshButton;
	private EditText distThresh;
	
	//
	//	VOIP
	//
	private Button voipChatButton;
	
	
	//
	//	VOICE RECOGNITION
	//	
	private ListView mList;
	
	//
	//	END UI ELEMENTS
	//
	/////////////////////////////////////////////////////

	
	/////////////////////////////////////////////////////
	//	GLOBAL CONSTANTS
	//
	
	private final int MILLISECONDS_MIN 			= 60000;
	private final int AUDIOMSG_TYPE_LOCAL 		= 1;
	private final int AUDIOMSG_TYPE_WEBSERVICE 	= 2;
	private final int ROLL_ACCEL_SENSITIVITY 	= 300; 	// mV/g
	private final String SIP_ADDRESS 			= "sip:9996183605@sip.tropo.com";
	private final float RAD_TO_DEG 				= (float) 57.295779513082320876798154814105;
	
	//
	//	END GLOBAL CONSTANTS
	//
	/////////////////////////////////////////////////////

	/////////////////////////////////////////////////////
	//	VARIABLES & OBJECTS
	//
		

	//
	//	GPS
	//	
	private boolean gpsLock = false;
	private LocationManager locationManager;
	private String provider;
	    
	double lat,lng;
	float heading;	
	
	//
	//	System Audio
	//		
	private AudioManager amanager = null;
	
	//
	//	TTS alerts
	//	
	private boolean declinedAlerts = false;
	
	private TextToSpeech tts; //made private
	private HashMap<String, String> myHash; //made private 


	//
	//	BLINDSPOTS
	//	
	private int distance_threshold_inches = 12;	
	
	
	//
	//	Bike Roll (Lean) Sensing (ADXL 335 Accelerometer)
	//
	private boolean prev_leftVal, prev_rightVal;
	private boolean leaning_right 	= true;
	private boolean leaning_left 	= true;
	private boolean prevLean_right 	= false;
	private boolean prevLean_left 	= false;
	
	private float ZEROGx = (float) 1500; // mV 
	private float ZEROGy = (float) 1500; // mV 
	private float ZEROGz = (float) 1500; // mV 
	private float gyroXZero = (float) 1500;
	private float gyroYZero = (float) 1500;
	
	private final int accelSENSITIVITY = 300; // mV/g
	private final int gyroSENSITIVITY = 2; // mV/ deg/sec
	
	private Float accelX_voltage;
	private Float accelY_voltage;
	private Float accelZ_voltage;
	private Float gyroX_voltage;
	private Float gyroY_voltage;
	
	
	//
	//	VOIP
	//
		
	private boolean voiceRecognizerBusy = false;
	private boolean voipEstablished = false;
	
	private SpeechRecognizer mSpeechRecognizer;
	private Intent mRecognizerIntent;
	    
	       
    public SipManager manager 		= null;
    public SipAudioCall call 		= null;
    public SipProfile me 			= null;
    
    SipProfile.Builder builder 		= null;
    
    //
    //	END VARIABLES & OBJECTS
    //	
    /////////////////////////////////////////////////////
    
    

  	
  	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		
		calibrateButton = (Button) findViewById(R.id.calibratebutton1);
		rightSensorButton = (Button) findViewById(R.id.rightSensorButton);
		leftSensorButton = (Button) findViewById(R.id.leftSensorButton);
		
		rightSensorButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.blackbutton));
		rightSensorButton.setTextColor(Color.WHITE);
		leftSensorButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.blackbutton));
		leftSensorButton.setTextColor(Color.WHITE);
		
		tv1_ = (TextView) findViewById(R.id.textView1);
		tv1_.setSingleLine(false);
		tv1_.setTextSize(14);
		
		distThresh = (EditText) findViewById(R.id.distThresh);
		distThresh.setText(Integer.toString(distance_threshold_inches));
		setDistThreshButton = (Button) findViewById(R.id.setDistThresholdButton);
		voipChatButton = (Button) findViewById(R.id.voipChatButton);
		
		voipChatButton.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				if(call == null || !call.isInCall()){

					initiateCall();

				}
				else {
					try {
						call.endCall();
					} catch (SipException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
		      }
		  });
		
		
		setDistThreshButton.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
		        distance_threshold_inches = Integer.parseInt(distThresh.getText().toString());
		      }
		  });
		
	    
		try {
			builder = new SipProfile.Builder("tester", "samss");
			builder.setPassword("");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    me = builder.build();
		
/*		//sensor shit 
    	roll_tv=(TextView)findViewById(R.id.xcoor); // create X axis object
		pitch_tv=(TextView)findViewById(R.id.ycoor); // create Y axis object
		*/

		
		// Get the location manager
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		
		
		// Define the criteria how to select the location provider -> use
		// default
		Criteria criteria = new Criteria();
		provider = locationManager.getBestProvider(criteria, false);
		//final Location location = locationManager.getLastKnownLocation(provider);
		//final Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		
		
		//locationManager.requestLocationUpdates(provider, MILLISECONDS_MIN * 10 , 0, this);

		
		
		
		amanager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		
		tts= new TextToSpeech(SAMSSActivity.this, new TextToSpeech.OnInitListener() {
			@Override
			public void onInit(int status) {
				// TODO Auto-generated method stub
				if (status!= TextToSpeech.ERROR){
					tts.setLanguage(Locale.US);
				}
			}
		});
		
		 mList = (ListView) findViewById(R.id.ListView01);

		// match.add(0, "cancel");
		 
		 initializeManager();
		 
		 
		 mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this.getApplicationContext());
	        
	        
		 mRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		 mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		 mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,"com.samss");
	        
	        
		 
	}
	
	public void initializeManager() {
		//tv1_.setText(tv1_.getText() +"\n" + "init manager: " + "\n");
	    if(manager == null) {
	      manager = SipManager.newInstance(this);
	      try {
	    	  if(manager != null){
	    		  manager.open(me);
	    	  }
	    	  else
	    		 Toast.makeText(getApplicationContext(), "SIP API not supported", 1000);
	      } catch (SipException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
	      }
	    }
	
	}


	/**
	 * Make an outgoing call.
	 */
	public void initiateCall() {
	
	    //updateStatus(sipAddress);
		//tv1_.setText(tv1_.getText() +"\n" + "init call: " + "\n");
	    try {
	        SipAudioCall.Listener listener = new SipAudioCall.Listener() {
	            // Much of the client's interaction with the SIP Stack will
	            // happen via listeners.  Even making an outgoing call, don't
	            // forget to set up a listener to set things up once the call is established.
	            @Override
	            public void onCallEstablished(SipAudioCall call) {
	            	
	            	amanager.setBluetoothScoOn(true);
	                amanager.startBluetoothSco();
	                voipEstablished =true;
	            	log("onCallEstablished:");
	            	
	                call.startAudio();
	                //call.setSpeakerMode(false);
	                //call.toggleMute();
	                //updateStatus(call);
	            }
	
	            @Override
	            public void onCallEnded(SipAudioCall call) {
	                //updateStatus("Ready.");
	            	log("onCallEnded: ");
	            	amanager.stopBluetoothSco();
	        		amanager.setBluetoothScoOn(false);
	        		amanager.abandonAudioFocus(null);
	            }
	            @Override
	            public void onError(SipAudioCall call, int errorCode, String errorMessage) {
	                //updateStatus("Ready.");
	            	log("onError: " + errorCode + "\n");
	            	amanager.stopBluetoothSco();
	        		amanager.setBluetoothScoOn(false);
	        		amanager.abandonAudioFocus(null);
	            }
	            @Override
	            public void onCalling(SipAudioCall call) {
	                //updateStatus("Ready.");
	            	log("calling " + "\n");
	            }            
	        };
	
	        call = manager.makeAudioCall(me.getUriString(), SIP_ADDRESS, listener, 30);
	
	    }
	    catch (Exception e) {
	        //Log.i("WalkieTalkieActivity/InitiateCall", "Error when trying to close manager.", e);
	        try {
				if (manager.isOpened(me.getUriString())) {
				    try {
				        manager.close(me.getUriString());
				    } catch (Exception ee) {
				        //Log.i("WalkieTalkieActivity/InitiateCall", "Error when trying to close manager.", ee);
				        ee.printStackTrace();
				    }
				}
			} catch (SipException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
	        if (call != null) {
	            call.close();
	        }
	    }
	}	

	private RecognitionListener mCancelRecognitionListener = new RecognitionListener() {
        @Override
        public void onBufferReceived(byte[] buffer) {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onBufferReceived");
        }

        @Override
        public void onError(int error) {
                // TODO Auto-generated method stub
                log("onError: " + error);
                //tv1_.setText(tv1_.getText() +"\n" + "onError: " + error + "\n");
                //mSpeechRecognizer.startListening(mRecognizerIntent);
                
                if(error == 7 || error == 6 || error == 2){
                	
                	Toast.makeText(getBaseContext(), "no response from rider", Toast.LENGTH_SHORT);
                	
                	
                	try {
	        	        Intent callIntent = new Intent(Intent.ACTION_CALL);
	        	        callIntent.setData(Uri.parse("tel:6177748487"));
	        	        startActivity(callIntent);
	        	    } catch (ActivityNotFoundException e) {
	        	    	log("Call failed with: " + e);
	        	    }
                	
                
                }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onEvent");
        		//tv1_.setText(tv1_.getText() +"\n" + "onEvent: " + eventType + "\n");
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onPartialResults");
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
                // TODO Auto-generated method stub
                log("onReadyForSpeech");
        		//tv1_.setText(tv1_.getText() + "\n" + "Ready for speech! \n");
        }

        @Override
        public void onResults(Bundle results) {

                log("got Results");
                //tv1_.setText(tv1_.getText() +"\n" + "onResults: " + "\n");
                Toast.makeText(getBaseContext(), "got voice results!", Toast.LENGTH_SHORT);

                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(!matches.contains("cancel")){
                	
                	Toast.makeText(getBaseContext(), "did not get cancel", Toast.LENGTH_SHORT);
                	
                	
                	try {
	        	        Intent callIntent = new Intent(Intent.ACTION_CALL);
	        	        callIntent.setData(Uri.parse("tel:6177748487"));
	        	        startActivity(callIntent);
	        	    } catch (ActivityNotFoundException e) {
	        	    	log("Call failed with: " + e);
	        	    }
                	
                
                }
                //lv1_.setAdapter(new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_list_item_1, matches));
                //mSpeechRecognizer.startListening(mRecognizerIntent);
        }

        @Override
        public void onRmsChanged(float rmsdB) {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onRmsChanged");
        }

        @Override
        public void onBeginningOfSpeech() {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onBeginningOfSpeech");
        }

        @Override
        public void onEndOfSpeech() {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onEndOfSpeech");
        }
	    
	};
	private RecognitionListener mYesNoRecognitionListener = new RecognitionListener() {

		
        @Override
        public void onBufferReceived(byte[] buffer) {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onBufferReceived");
        }

        @Override
        public void onError(int error) {
                // TODO Auto-generated method stub
                log("onError: " + error);
                //tv1_.setText(tv1_.getText() +"\n" + "onError: " + error + "\n");
                //mSpeechRecognizer.startListening(mRecognizerIntent);
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onEvent");
        		//tv1_.setText(tv1_.getText() +"\n" + "onEvent: " + eventType + "\n");
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onPartialResults");
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
                // TODO Auto-generated method stub
                log("onReadyForSpeech");
        		//tv1_.setText(tv1_.getText() + "\n" + "Ready for speech! \n");
        }

        @Override
        public void onResults(Bundle results) {
        		voiceRecognizerBusy = false;
                log("got Results");
                //tv1_.setText(tv1_.getText() +"\n" + "onResults: " + "\n");
                //Toast.makeText(getApplicationContext(), "got voice results!", Toast.LENGTH_SHORT);

                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(!matches.contains("yes") && matches.contains("no")){
                	declinedAlerts = true;
                	//Toast.makeText(getApplicationContext(), "did not here yes/no", Toast.LENGTH_SHORT);
                	
                	
                	
                
                }
                else if(matches.contains("yes") && !matches.contains("no")){
                	
                	declinedAlerts = false;
                	
                }
                mList.setAdapter(new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_list_item_1, matches));
                //mSpeechRecognizer.startListening(mRecognizerIntent);
        }

        @Override
        public void onRmsChanged(float rmsdB) {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onRmsChanged");
        }

        @Override
        public void onBeginningOfSpeech() {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onBeginningOfSpeech");
        }

        @Override
        public void onEndOfSpeech() {
                // TODO Auto-generated method stub
                //Log.d(TAG, "onEndOfSpeech");
        }
	    
	};	

	
	
	
	public static JSONObject connect(String url)
	{

		HttpClient httpclient = new DefaultHttpClient();

		// Prepare a request object
		HttpGet httpget = new HttpGet(url); 

		// Execute the request
		HttpResponse response;

		JSONObject json = new JSONObject();

		try {
			response = httpclient.execute(httpget);

			HttpEntity entity = response.getEntity();

			if (entity != null) {

				// A Simple JSON Response Read
				InputStream instream = entity.getContent();
				String result= convertStreamToString(instream);

				json = new JSONObject(result);

				instream.close();
			}

		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return json;
	}
	/**
	 *
	 * @param is
	 * @return String
	 */
	public static String convertStreamToString(InputStream is) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return sb.toString();
	}	
	
	
	
	
	/**
	 * This is the thread on which all the IOIO activity happens. It will be run
	 * every time the application is resumed and aborted when it is paused. The
	 * method setup() will be called right after a connection with the IOIO has
	 * been established (which might happen several times!). Then, loop() will
	 * be called repetitively until the IOIO gets disconnected.
	 */
	class IOIOThread extends AbstractIOIOActivity.IOIOThread {
		/** The on-board LED. */
		private DigitalOutput led_crash, led_left, led_right, helmet_enable_L, helmet_enable_R;
		//private DigitalInput left_buttonInput_;
		//private DigitalInput right_buttonInput_;
		private AnalogInput sensorValueL_input, sensorValueR_input;
		//bike analog inputs
		private AnalogInput accelX, accelY, accelZ, gyroX, gyroY;
		
		private Float gyroXrate, gyroYrate, gyroXangle;
		private Float accelYval, accelXval, accelZval, accelXangle, accelYangle;
		
		private PwmOutput led_left_helmet, led_right_helmet;

		private PulseInput crash_x, crash_y;
		
		Float timer;
		int dtime;


		ArrayList<Float> sensorMedian_L = new ArrayList<Float>();
		ArrayList<Float> sensorMedian_R = new ArrayList<Float>();

		private Float sensorvalueL, inchvalueL, sensorvalueR, inchvalueR;
		private boolean leftBlindspot 		= false;
		private boolean prev_leftBlindspot 	= false; 
		private boolean rightBlindspot	 	= false;
		private boolean prev_rightBlindspot = false;
		private boolean calibrated = false;
			
		private Float roll, pitch;
		
		private Float xDuration, yDuration, xRatio, yRatio;

		private int bufferSize = 100;



		/**
		 * Called every time a connection with IOIO has been established.
		 * Typically used to open pins.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#setup()
		 */
		@Override
		protected void setup() throws ConnectionLostException {

			
			//log("Connection to XBee establised.");
			led_left_helmet = ioio_.openPwmOutput(12, 16000);
			led_right_helmet = ioio_.openPwmOutput(13, 16000);
			led_left_helmet.setDutyCycle(0);
			led_right_helmet.setDutyCycle(0);


			led_crash = ioio_.openDigitalOutput(3, false);
			led_left = ioio_.openDigitalOutput(2, false);
			led_right = ioio_.openDigitalOutput(1, false);
			//helmet_enable_L = ioio_.openDigitalOutput(6, true);
			//helmet_enable_R = ioio_.openDigitalOutput(6, DigitalOutput.Spec.Mode.OPEN_DRAIN, false);


			sensorValueR_input = ioio_.openAnalogInput(40); 
			sensorValueL_input = ioio_.openAnalogInput(41); 
			accelX = ioio_.openAnalogInput(37);
			accelY = ioio_.openAnalogInput(36);
			accelZ = ioio_.openAnalogInput(35);
			gyroX = ioio_.openAnalogInput(38);
			gyroY = ioio_.openAnalogInput(39);

			//crash_x = ioio_.openPulseInput(10, PulseMode.FREQ);
			//crash_y = ioio_.openPulseInput(11, PulseMode.FREQ);

			timer = (float) System.nanoTime();

			//log("Connecting IOIO UART to XBee...");

			//uart_ = ioio_.openUart(4, 5, 57600, Uart.Parity.NONE, Uart.StopBits.ONE); //blue is 4, green is 5

			//if(uart_ != null) log("Success.");
			
			calibrated = false;

			log("Listening to Blindspot sensors...");

			//xbeeIn = uart_.getInputStream();
			//xbeeOut = uart_.getOutputStream();

		}

		/**
		 * Called repetitively while the IOIO is connected.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#loop()
		 */
		@Override
		protected void loop() throws ConnectionLostException {

			//read analog inputs
			try {
				//blindspot sensors
				//left
				sensorMedian_L.clear();
				for ( int i = 0; i <= 501; i++){
					sensorMedian_L.add(sensorValueL_input.getVoltage() *1000);
				}
				sensorvalueL = Median(sensorMedian_L);
				//Log.d("SAMSS_median_LEFT", sensorvalueL.toString());
				leftBlindspot = ( sensorvalueL / (6.45f) ) < distance_threshold_inches; 
				//inchvalueL = sensorvalueL / (6.45f);   //convert to inches
				
				//right
				sensorMedian_R.clear();
				for ( int i = 0; i <= 501; i++){
					sensorMedian_R.add(sensorValueR_input.getVoltage() *1000);
				}
				sensorvalueR = Median(sensorMedian_R);
				//Log.d("SAMSS_median_RIGHT", sensorvalueR.toString());
				rightBlindspot = ( sensorvalueR / (6.45f) ) < distance_threshold_inches; 
				//inchvalueR = sensorvalueR / (6.45f);   //convert to inches


			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			//read analog inputs
			try {
				
				
				for (int j = 0; j < 10000; j++){
					
				}
				
				//roll accelerometer
				accelX_voltage = accelX.getVoltage() * 1000; //convert to mV
				accelY_voltage = accelY.getVoltage() * 1000;
				accelZ_voltage = accelZ.getVoltage() * 1000;
				gyroX_voltage = gyroX.getVoltage() * 1000;
				gyroY_voltage = gyroY.getVoltage() * 1000;
				
				
				if (!calibrated){
					ZEROGx = accelX_voltage; // mV 
					ZEROGy = accelY_voltage; // mV 
					ZEROGz = accelZ_voltage - 310; // mV 
					gyroXZero = gyroX_voltage;
					gyroYZero = gyroY_voltage;
					log("IMU calibrated...");
					calibrated = true;
				}
				
				accelYval = (accelY_voltage - ZEROGy) / accelSENSITIVITY;
				accelXval = (accelX_voltage - ZEROGx) / accelSENSITIVITY;
				accelZval = (accelZ_voltage - ZEROGz) / accelSENSITIVITY;
				//double R = Math.sqrt(Math.pow(accelYval, 2)+Math.pow(accelXval, 2)+Math.pow(accelZval, 2));
				//accelXangle = (float) (Math.acos(accelXval/R)*RAD_TO_DEG-90);
				double R = Math.sqrt(Math.pow(accelYval, 2)+Math.pow(accelZval, 2));
				accelXangle = (float) (Math.atan(accelXval/R)*RAD_TO_DEG);
				
				
				
				gyroXrate = (gyroX_voltage - gyroXZero) / gyroSENSITIVITY;
				
				dtime = (int) (System.nanoTime() - timer);
				timer = (float) System.nanoTime();
				roll = kalmanCalculateX(accelXangle, gyroXrate, dtime);
				
				Log.d("SAMSS_angle", roll.toString());
				//log(roll.toString());
				//updateRoll_nd_Pitch(roll.toString());
				

				//pitch = convertToPitch(accelX_voltage, accelY_voltage, accelZ_voltage);
				//roll = convertToRoll(accelY_voltage, accelZ_voltage); //get roll in degrees

				if ( roll > 20 )
					leaning_right=true;	
				else if ( roll < 20 ){ //made else if
					leaning_right=false;
					prevLean_right=false;
				}
				
				if ( roll < -20 )
					leaning_left=true;	
				else if ( roll > -20 ){ //made else if
					leaning_left=false;
					prevLean_left=false;
				}


				//updateRoll_nd_Pitch(roll, pitch);

			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			if(leftBlindspot && !prev_leftBlindspot){
						led_left.write(true);
						//xbeeOut.write(72);
						//write to analog pin instead of writing xbee
						//helmet_enable_L.write(false);
						led_left_helmet.setDutyCycle(1);

						setSensorButtonBackground(leftSensorButton, R.drawable.redbutton);
						//log("Transmitted Left: H");
						prev_leftBlindspot = true;
			} 
			else if(!leftBlindspot && prev_leftBlindspot){
				led_left.write(false);
				//xbeeOut.write(76);
				//write to analog pin instead of writing xbee
				led_left_helmet.setDutyCycle(0);
				//helmet_enable_L.write(true);

				setSensorButtonBackground(leftSensorButton, R.drawable.blackbutton);
				//log("Transmitted Left: L"); 
				prev_leftBlindspot = false;	
			}

			if(rightBlindspot && !prev_rightBlindspot){
						led_right.write(true);
						//xbeeOut.write(74);
						//write to analog pin instead of writing xbee
						led_right_helmet.setDutyCycle(1);

						setSensorButtonBackground(rightSensorButton, R.drawable.bluebutton);
						//log("Transmitted Right: H (J)"); 
						prev_rightBlindspot = true;
			} 
			else if(!rightBlindspot && prev_rightBlindspot){
				led_right.write(false);
				//xbeeOut.write(75);
				//write to analog pin instead of writing xbee
				led_right_helmet.setDutyCycle(0);

				setSensorButtonBackground(rightSensorButton, R.drawable.blackbutton);
				//log("Transmitted Right: L (K)"); 
				prev_rightBlindspot = false;	
			}




			//if x < -2 send BT audio warning
			if((leaning_right && !prevLean_right && rightBlindspot) ){
				prevLean_right = leaning_right;
				String[] msg = {"Right Blindspot Warning"};
				sendBTaudio(AUDIOMSG_TYPE_LOCAL,msg);
			}

			//if x > 2 send BT audio warning
			if((leaning_left && !prevLean_left && leftBlindspot )){
				prevLean_left = leaning_left;
				String[] msg = {"Left Blindspot Warning"};
				sendBTaudio(AUDIOMSG_TYPE_LOCAL, msg);
			}
			
			//start of crash detection shit
			/*try {
				xDuration = crash_x.getDuration();
				xRatio = xDuration / .000064f;
				Log.d("CRASH", xRatio.toString());
				//xDuration = crash_x.getFrequency();
				//Log.d("CRASH", xDuration.toString());
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
			
 /*         try { 
            	int availableBytes =xbeeIn.available(); 
            	 
			    if (availableBytes > 0) { 
			           log("available bytes " + Integer.toString(availableBytes));
			           
			           byte[] readBuffer = new byte[bufferSize]; 
			           xbeeIn.read(readBuffer, 0, availableBytes); 
			           char[] Temp = (new String(readBuffer, 0, availableBytes)).toCharArray(); 
			           String Temp2 = new String(Temp); 
			           			           
			           if ( Temp2.matches("P+")){
			        	   led_crash.write(true);
			        	 
			        	String[] msg = {"Crash detected. Dialing 9 1 1. Say cancel for false alarm."};
						sendBTaudio(AUDIOMSG_TYPE_LOCAL, msg);

			       		View v = findViewById(R.id.calibratebutton1);

			       		v.post(new Runnable(){ public void run(){ mSpeechRecognizer.startListening(mRecognizerIntent); }});
			       	
			       		   
			               
			        	   sleep(1000);
			        	   led_crash.write(false);
			        	   
			           }
			           else
			        	   led_crash.write(false);
			           
			           log("Received: " + Temp2); 
			    } 
			    sleep(30); 
            } 
            catch (InterruptedException e) { 
            	log("Error: " +e ); 
            } 
            catch (IOException e) { 
			    // TODO Auto-generated catch block 
			    log("Error: " +e ); 
			    e.printStackTrace(); 
            } */
		}

        

	}
	/*=================================================
	 * calman Filter x
	 */
    float Q_angleX  =  0.001f; 
    float Q_gyroX   =  0.003f;  
    float R_angleX  =  0.03f;  

    Float x_angle = 0f;
    float x_bias = 0;
    float PX_00 = 0, PX_01 = 0, PX_10 = 0, PX_11 = 0;	
    float dtX, yX, SX;
    float KX_0, KX_1;

  Float kalmanCalculateX(float newAngle, float newRate,int looptime) {
    dtX = (float) (looptime)/1000;                                    // XXXXXXX arevoir
    x_angle += dtX * (newRate - x_bias);
    PX_00 +=  - dtX * (PX_10 + PX_01) + Q_angleX * dtX;
    PX_01 +=  - dtX * PX_11;
    PX_10 +=  - dtX * PX_11;
    PX_11 +=  + Q_gyroX * dtX;
    
    yX = newAngle - x_angle;
    SX = PX_00 + R_angleX;
    KX_0 = PX_00 / SX;
    KX_1 = PX_10 / SX;
    
    x_angle +=  KX_0 * yX;
    x_bias  +=  KX_1 * yX;
    PX_00 -= KX_0 * PX_00;
    PX_01 -= KX_0 * PX_01;
    PX_10 -= KX_1 * PX_00;
    PX_11 -= KX_1 * PX_01;
    
    return x_angle;
    
  }
//========================================
	
	public static float Median(ArrayList values)
	{
	    Collections.sort(values);
		
	    if (values.size() % 2 == 1)
	    	return (Float) values.get( values.size() / 2 );
	    else
	    {
	    	
	    	float lower = (Float) values.get( (values.size()/2) - 1);
	    	float upper = (Float) values.get( values.size() / 2);

	    	return (lower + upper) / 2.0f;
	    }	
	}
/*
	double convertToPitch(float x, float y, float z){
		double pitch;
		x = x - ZEROGy;
		x = x / SENSITIVITY;

		y = y - ZEROGy;
		y = y / SENSITIVITY;

		z = z - ZEROGz;
		z = z / SENSITIVITY;

		pitch = Math.atan (x / Math.sqrt((y*y) + (z*z)));
		pitch = (pitch * 180) / Math.PI; 

		return pitch;
	}

	double convertToRoll (float y, float z){
		double roll; //degrees
		y = y - ZEROGy;
		y = y / SENSITIVITY;

		z = z - ZEROGz;
		z = z / SENSITIVITY;

		roll = Math.atan (y / Math.sqrt((z*z) + (z*z)));
		roll = (roll * 180) / Math.PI;

		return roll;
	}*/

	void setSensorButtonBackground(final Button button, final int drawable) {
		runOnUiThread(new Runnable() { 
            @Override 
            public void run() { 
            	button.setBackgroundDrawable(getResources().getDrawable(drawable));
            	if(drawable == R.drawable.blackbutton) button.setTextColor(Color.WHITE);
            	else button.setTextColor(Color.BLACK);
            	
            } 
        }); 
    	 
    } 

	void log(final String logString) {
		runOnUiThread(new Runnable() { 
            @Override 
            public void run() { 
            	tv1_.setText(tv1_.getText() + logString + "\n");
            } 
        }); 
    	 
    }

	void updateRoll_nd_Pitch(final String newroll) {
		runOnUiThread(new Runnable() { 
            @Override 
            public void run() { 
            	roll_tv.setText("Roll: "+ newroll);
            } 
        }); 
    	 
    }

	void sendBTaudio(final int type, final String[] ttsStrings){

		myHash = new HashMap<String, String>();
		myHash.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "Text_Voice");

		amanager.setBluetoothScoOn(true);
        amanager.startBluetoothSco();
        
        myHash.put( TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_VOICE_CALL));
        
        amanager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        
        
        switch(type){
        
        // If message from bike unit, just speak ttsStrings[0];
        case AUDIOMSG_TYPE_LOCAL:
        	tts.speak(ttsStrings[0], TextToSpeech.QUEUE_FLUSH, myHash);
        break;
        
        // If message is from web service response, ttsStrings[0] contains a prompt of the form:
        //
        //		"Found 3 traffic incidents and 1 weather alert. Do you want to hear them?"
        //
        // To which the rider can say "yes" or "no". If they say yes, loop through the rest of the
        // ttsStrings. 
        case AUDIOMSG_TYPE_WEBSERVICE:
        	// Speak Prompt
        	tts.speak(ttsStrings[0], TextToSpeech.QUEUE_FLUSH, myHash);
        	
        	// Listen for Yes/No
        	while(tts.isSpeaking()){
        		
        	}
        	
        	tv1_.post(new Runnable(){ public void run(){ 
        		voiceRecognizerBusy = true;
        		mSpeechRecognizer.setRecognitionListener(mYesNoRecognitionListener);
        		mSpeechRecognizer.startListening(mRecognizerIntent); 
        		}
        	});
        	
        	while(voiceRecognizerBusy){
        		//	Delay here to make sure the recognizer has gotten a yes/no answer or timed out
        	}
        	
        	if(!declinedAlerts){
        		
        		for(int i = 1 ; i < ttsStrings.length - 1; i++){
        			tts.speak(ttsStrings[i], TextToSpeech.QUEUE_ADD, myHash);
        		}
        		
        	}
        	
        break;        
        
        }
        
        
	    //String warning = "Crash detected. Dialing 9 1 1. Say cancel for false alarm.";

	}
	/**
	 * A method to create our IOIO thread.
	 * 
	 * @see ioio.lib.util.AbstractIOIOActivity#createIOIOThread()
	 */
	@Override
	protected AbstractIOIOActivity.IOIOThread createIOIOThread() {
		return new IOIOThread();
	}

	
	
	

	public void onLocationChanged(Location location) {
		Log.i("SAMSS/GPSlocationListener", "onLocationChanged");
		//printLocation(location);
		lat = (double) location.getLatitude();
		lng = (double) location.getLongitude();
		heading = location.getBearing();
		gpsLock = true;
		
		if(gpsLock){
    		// send GPS cords to web service
    		Log.i("SAMSS/WebserviceGPS", "got to GPSTimer");

			String address = "http://olnoeve.no.de";


			JSONObject json = connect(address + "/" + heading + "/" + lat + "/" + lng);

			try {
				//Log.i("SAMSS/WebserviceResponse", json.toString(3));
				log( json.toString(3) );
				
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//myHash.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_VOICE_CALL));

			JSONObject traffic = new JSONObject();
			JSONObject weather = new JSONObject();
			JSONArray weatherAlerts = new JSONArray();
			JSONArray trafficIncidents = new JSONArray();
			JSONArray trafficConstruction = new JSONArray();
			try {
				weather = json.getJSONObject("Weather");
				traffic = json.getJSONObject("Traffic");
				//Log.i("SAMSS/JSON", traffic.toString());
				weatherAlerts = weather.getJSONArray("Alerts");
				trafficIncidents = traffic.getJSONArray("incidents");
				trafficConstruction = traffic.getJSONArray("construction");
			} catch (JSONException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			ArrayList<String> msgs = new ArrayList<String>();
					
			msgs.add("Found " 
						+ trafficIncidents.length() 
						+ " traffic incidents and " 
						+ weatherAlerts.length() 
						+ " weather alerts. Do you want to hear them?");
			
			//	Add all traffic incident messages to list first
			for(int i = 0; i < trafficIncidents.length(); i++){

				
				try {
					msgs.add( trafficIncidents.getString(i) );
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
			//	Then add all weather messages
			for(int j = 0; j < weatherAlerts.length(); j++){

				
				try {
					msgs.add( weatherAlerts.getString(j) );
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
			
			//	Finally, send the list of messages to sendBTAudio with AUDIOMSG_TYPE_WEBSERVICE
			try {
				Log.i("SAMSS/WebserviceResponse", json.toString(3));
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String[] alerts = msgs.toArray(new String[msgs.size()]);
			sendBTaudio(AUDIOMSG_TYPE_WEBSERVICE, alerts);
    		
		}
		else
			log("no gps lock");
		
		
		
	}

	public void onProviderDisabled(String provider) {
		// let okProvider be bestProvider
		// re-register for updates
		//output.append("\n\nProvider Disabled: " + provider);
	}

	public void onProviderEnabled(String provider) {
		// is provider better than bestProvider?
		// is yes, bestProvider = provider
		//output.append("\n\nProvider Enabled: " + provider);
	}

	@Override
	public void onUtteranceCompleted(String arg0) {
		// TODO Auto-generated method stub
		if (!voipEstablished){
		amanager.stopBluetoothSco();
		amanager.setBluetoothScoOn(false);
		amanager.abandonAudioFocus(null);
		}
	}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		// TODO Auto-generated method stub
		
	}
}

package com.samss;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.AbstractIOIOActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
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



public class SAMSSActivity extends AbstractIOIOActivity implements SensorEventListener,  OnUtteranceCompletedListener{
	private TextView tv1_;
	private TextView xCoor; // declare X axis object
	private TextView yCoor; // declare Y axis object
	private TextView zCoor; // declare Z axis object
	private EditText distThresh;
	
	private Button rightSensorButton;
	private Button leftSensorButton;
	private Button setDistThreshButton;
	private Button voipChatButton;
	
	private boolean left_buttonPrevState;
	private boolean right_buttonPrevState;
	private boolean prev_leftVal, prev_rightVal;
	private boolean leaning_right = true;
	private boolean leaning_left = true;
	private boolean prevLean_right = false;
	private boolean prevLean_left = false;
	
	private boolean declinedAlerts = false;
	
	private int distance_threshold_inches = 12;
	
	private int MILLISECONDS_MIN = 60000;
	private final int AUDIOMSG_TYPE_LOCAL = 1;
	private final int AUDIOMSG_TYPE_WEBSERVICE = 2;
	
	
	private static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;
	private ListView mList;
	private ArrayList<String> matches/*, match*/;

	private boolean voiceRecognizerBusy = false;
	private SpeechRecognizer mSpeechRecognizer;
	private Intent mRecognizerIntent;
	    
	    
	 private LocationManager locationManager;
	 private String provider;
	    
	 double lat,lng;

    public String sipAddress = "sip:9996183605@sip.tropo.com";
    
    public SipManager manager = null;
    public SipAudioCall call = null;
    public SipProfile me = null;
	 
	//sensor shit
	private SensorManager sensorManager;
	public float x, y, z;
	public float heading, pitch, roll;
	public float cx = 0, cy = 0, cz = 0;
	
	//calibrate method
	public void onCalibrateButtonClick(View view){
  		cx=-x;
  		cy=-y;
  		cz=-z;
  	};
	
	private TextToSpeech tts; //made private
	private HashMap<String, String> myHash; //made private 
	private AudioManager amanager = null;
	
	//private PowerManager.WakeLock mWakeLock;

	/**
	 * Called when the activity is first created. Here we normally initialize
	 * our GUI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		
		
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
		
	    SipProfile.Builder builder = null;
		try {
			builder = new SipProfile.Builder("tester", "samss");
			builder.setPassword("");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    me = builder.build();
		
		//sensor shit 
    	xCoor=(TextView)findViewById(R.id.xcoor); // create X axis object
		yCoor=(TextView)findViewById(R.id.ycoor); // create Y axis object
		zCoor=(TextView)findViewById(R.id.zcoor); // create Z axis object
		
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
		
		
		// Get the location manager
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		// Define the criteria how to select the location provider -> use
		// default
		//Criteria criteria = new Criteria();
		//provider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
		final Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		
		Timer updateTimer = new Timer("gpsTimer");
        updateTimer.scheduleAtFixedRate(new TimerTask(){
        	public void run(){
        		
        		// send GPS coords to web service
        		

				String address = "http://192.168.1.2:3000";



				if (location != null) {
					//System.out.println("Provider " + provider + " has been selected.");
					lat = (double) (location.getLatitude());
					lng = (double) (location.getLongitude());

					//tv1_.setText(tv1_.getText() +"\n lat: " + lat + "\n");
					//tv1_.setText(tv1_.getText() +"\n lng: " + lng + "\n");
				}
				float heading = location.getBearing();
				
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
        		
        		
 /*       		float[] m_rotationMatrix = null;
        		float[] m_lastMagFields = null;
        		float[] m_lastAccels = null;
        		float[] m_orientation = null;
        		
        		if (SensorManager.getRotationMatrix(m_rotationMatrix, null, m_lastMagFields, m_lastAccels)) {
					SensorManager.getOrientation(m_rotationMatrix, m_orientation);
					
					 1 radian = 57.2957795 degrees 
					 [0] : yaw, rotation around z axis
					* [1] : pitch, rotation around x axis
					* [2] : roll, rotation around y axis 
					float heading = m_orientation[0] * 57.2957795f;
					//float pitch = m_orientation[1] * 57.2957795f;
					//float roll = m_orientation[2] * 57.2957795f;
				}*/
        		
        	}
        },  MILLISECONDS_MIN * 10, MILLISECONDS_MIN * 10);
		
		
		
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
	        
	        
		 mSpeechRecognizer.setRecognitionListener(mCancelRecognitionListener);

		 
		
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
	            	
	            	int blahh = amanager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
	            	//amanager.setMode(AudioManager.MODE_IN_CALL);
	            	log("onCallEstablished:");
	            	
	                call.startAudio();
	                //call.setSpeakerMode(false);
	                call.toggleMute();
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
	
	        call = manager.makeAudioCall(me.getUriString(), sipAddress, listener, 30);
	
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
		private DigitalOutput led_crash, led_left, led_right;
		//private DigitalInput left_buttonInput_;
		//private DigitalInput right_buttonInput_;
		private AnalogInput sensorValueL_input, sensorValueR_input;
		private Float sensorvalueL, inchvalueL, footvalueL, sensorvalueR, inchvalueR, footvalueR;
		
		private Uart uart_;
		private InputStream xbeeIn;
		private OutputStream xbeeOut;
		
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
			
			log("Connection to XBee establised.");
			
			led_crash = ioio_.openDigitalOutput(3, false);
			led_left = ioio_.openDigitalOutput(2, false);
			led_right = ioio_.openDigitalOutput(1, false);

			sensorValueR_input = ioio_.openAnalogInput(40); //bar 5
			sensorValueL_input = ioio_.openAnalogInput(41); //bar 6
			
			log("Connecting IOIO UART to XBee...");
			
			uart_ = ioio_.openUart(4, 5, 57600, Uart.Parity.NONE, Uart.StopBits.ONE); //blue is 4, green is 5
			
			if(uart_ != null) log("Success.");
			
			log("Listening to sensors...");
			
			xbeeIn = uart_.getInputStream();
			xbeeOut = uart_.getOutputStream();
			
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
			boolean left_buttonVal = false;
			boolean right_buttonVal = false;
			
			try {
				//left_buttonVal = left_buttonInput_.read();
				//right_buttonVal = right_buttonInput_.read();
				sensorvalueL = sensorValueL_input.getVoltage() *1000; 		   //get analog sensor value from pin 40 in mV
				inchvalueL = sensorvalueL / (6.45f);   //convert to inches
				//log(inchvalueL.toString());
				sensorvalueR = sensorValueR_input.getVoltage() *1000; 		   //get analog sensor value from pin 41 in mV
				inchvalueR = sensorvalueR / (6.45f); 	//convert to inches
				
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			
			try {
				if((inchvalueL < distance_threshold_inches) && !prev_leftVal){
							led_left.write(true);
							xbeeOut.write(72);
							setSensorButtonBackground(leftSensorButton, R.drawable.redbutton);
							//log("Transmitted Left: H");
							prev_leftVal = true;
				} 
				else if(!(inchvalueL < distance_threshold_inches) && prev_leftVal){
					led_left.write(false);
					xbeeOut.write(76);
					setSensorButtonBackground(leftSensorButton, R.drawable.blackbutton);
					//log("Transmitted Left: L"); 
					prev_leftVal = false;	
				}
			}
			catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
			}
			
			try {
				if((inchvalueR < distance_threshold_inches) && !prev_rightVal){
							led_right.write(true);
							xbeeOut.write(74);
							setSensorButtonBackground(rightSensorButton, R.drawable.bluebutton);
							//log("Transmitted Right: H (J)"); 
							prev_rightVal = true;
				} 
				else if(!(inchvalueR < distance_threshold_inches) && prev_rightVal){
					led_right.write(false);
					xbeeOut.write(75);
					setSensorButtonBackground(rightSensorButton, R.drawable.blackbutton);
					//log("Transmitted Right: L (K)"); 
					prev_rightVal = false;	
				}
			}
			catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
			}
			
			
			
			
			//if x < -2 send BT audio warning
			if(leaning_right && !prevLean_right && (inchvalueR < distance_threshold_inches)){
				prevLean_right = leaning_right;
				String[] msg = {"Right Blindspot Warning"};
				sendBTaudio(AUDIOMSG_TYPE_LOCAL,msg);
			}

			//if x > 2 send BT audio warning
			if(leaning_left && !prevLean_left && (inchvalueL < distance_threshold_inches)){
				prevLean_left = leaning_left;
				String[] msg = {"Left Blindspot Warning"};
				sendBTaudio(AUDIOMSG_TYPE_LOCAL, msg);
			}

			
            try { 
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
            } 
		}
		
        
		
	}
	
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

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}
	
	
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER){

			// assign directions
			x=event.values[0];
			y=event.values[1];
			z=event.values[2];
			
			if ( (x+cx) < -2 )
				leaning_right=true;	
			else if ( (x+cx) > -2 ){ //made else if
				leaning_right=false;
				prevLean_right=false;
			}
			
			if ( (x+cx) > 2 )
				leaning_left=true;	
			else if ( (x+cx) < 2 ){ //made else if
				leaning_left=false;
				prevLean_left=false;
			}

			xCoor.setText("X: "+(x+cx));
			yCoor.setText("Y: "+(y+cy));
			zCoor.setText("Z: "+(z+cz));
		}
		
	}
	



	@Override
	public void onUtteranceCompleted(String arg0) {
		// TODO Auto-generated method stub
		amanager.stopBluetoothSco();
		amanager.setBluetoothScoOn(false);
		amanager.abandonAudioFocus(null);
	}
}


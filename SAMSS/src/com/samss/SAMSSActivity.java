package com.samss;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.AbstractIOIOActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.media.AudioManager;
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
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;



public class SAMSSActivity extends AbstractIOIOActivity implements SensorEventListener,  OnUtteranceCompletedListener{
	private TextView tv1_;
	private TextView xCoor; // declare X axis object
	private TextView yCoor; // declare Y axis object
	private TextView zCoor; // declare Z axis object
	
	private boolean left_buttonPrevState;
	private boolean right_buttonPrevState;
	private boolean prev_leftVal, prev_rightVal;
	private boolean leaning_right = true;
	private boolean leaning_left = true;
	private boolean prevLean_right = false;
	private boolean prevLean_left = false;
	
	private int distance_threshold_inches = 12;
	
	private static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;
	private ListView mList;
	private ArrayList<String> matches/*, match*/;

	
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
		tv1_ = (TextView) findViewById(R.id.textView1);
		tv1_.setSingleLine(false);
		
		
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
		
		sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);
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
	        
	        
		 mSpeechRecognizer.setRecognitionListener(mRecognitionListener);

		 
		
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
	            	//tv1_.setText(tv1_.getText() +"\n" + "onCallEstablished: " + "\n");
	                call.startAudio();
	                call.setSpeakerMode(true);
	                call.toggleMute();
	                //updateStatus(call);
	            }
	
	            @Override
	            public void onCallEnded(SipAudioCall call) {
	                //updateStatus("Ready.");
	            	//tv1_.setText(tv1_.getText() +"\n" + "onCallEnded: " + "\n");
	            }
	            @Override
	            public void onError(SipAudioCall call, int errorCode, String errorMessage) {
	                //updateStatus("Ready.");
	            	//tv1_.setText(tv1_.getText() +"\n" + "onError: " + errorCode + "\n");
	            }
	            @Override
	            public void onCalling(SipAudioCall call) {
	                //updateStatus("Ready.");
	            	//tv1_.setText(tv1_.getText() +"\n" + "calling " + "\n");
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

private RecognitionListener mRecognitionListener = new RecognitionListener() {
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
                //Toast.makeText(getBaseContext(), "got voice results!", Toast.LENGTH_SHORT);

                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(!matches.contains("cancel")){
                	
                	Toast.makeText(getBaseContext(), "got cancel", Toast.LENGTH_SHORT);
                
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
	
	
	
	

	//
//	tog1_ would be our pushbutton on the helmet to initiate voice chat
//
/*        tog1_ = (ToggleButton) findViewById(R.id.toggleButton1);
        
        tog1_.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				// TODO Auto-generated method stub
				buttonView.setChecked(isChecked);
				if(call == null){
					
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
			
        });*/
		 
//
//	DO THIS TO MAKE A PHONE CALL (if rider doesn't "cancel")
//								 
/*	       phoneButton_.setOnClickListener(new OnClickListener() {
	           public void onClick(View v) {
	               
	        	    try {
	        	        Intent callIntent = new Intent(Intent.ACTION_CALL);
	        	        callIntent.setData(Uri.parse("tel:6177748487"));
	        	        startActivity(callIntent);
	        	    } catch (ActivityNotFoundException e) {
	        	    	tv1_.setText(tv1_.getText() +"\n" + "Call failed" + e + "\n");
	        	        //Log.e("helloandroid dialing example", "Call failed", e);
	        	    }
	        	   
	           }
	       }); */	
	
	
	
	
	
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
			led_crash = ioio_.openDigitalOutput(3, false);
			led_left = ioio_.openDigitalOutput(2, false);
			led_right = ioio_.openDigitalOutput(1, false);

			sensorValueR_input = ioio_.openAnalogInput(40); //bar 5
			sensorValueL_input = ioio_.openAnalogInput(41); //bar 6
			
			log("Connecting to XBee UART...\n");
			
			uart_ = ioio_.openUart(4, 5, 57600, Uart.Parity.NONE, Uart.StopBits.ONE); //blue is 4, green is 5
			
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
							log("Transmitted Left: H");
							prev_leftVal = true;
				} 
				else if(!(inchvalueL < distance_threshold_inches) && prev_leftVal){
					led_left.write(false);
					xbeeOut.write(76);
					log("Transmitted Left: L"); 
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
							log("Transmitted Right: H (J)"); 
							prev_rightVal = true;
				} 
				else if(!(inchvalueR < distance_threshold_inches) && prev_rightVal){
					led_right.write(false);
					xbeeOut.write(75);
					log("Transmitted Right: L (K)"); 
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
				myHash = new HashMap<String, String>();
	       		myHash.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "Text_Voice");
	       		amanager.setBluetoothScoOn(true);
	            amanager.startBluetoothSco();
	            myHash.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
	                    String.valueOf(AudioManager.STREAM_VOICE_CALL));
	            amanager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
	                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
	       		String warning = "Right Blindspot Warning";
	       		tts.speak(warning, TextToSpeech.QUEUE_FLUSH, myHash);
			}

			//if x > 2 send BT audio warning
			if(leaning_left && !prevLean_left && (inchvalueL < distance_threshold_inches)){
				prevLean_left = leaning_left;
				myHash = new HashMap<String, String>();
	       		myHash.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "Text_Voice");
	       		amanager.setBluetoothScoOn(true);
	            amanager.startBluetoothSco();
	            myHash.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
	                    String.valueOf(AudioManager.STREAM_VOICE_CALL));
	            amanager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
	                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
	       		String warning = "Left Blindspot Warning";
	       		tts.speak(warning, TextToSpeech.QUEUE_FLUSH, myHash);
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
			        	    
				       		  
				       		  
			        	   myHash = new HashMap<String, String>();
			       		   myHash.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "Text_Voice");
			       		   amanager.setBluetoothScoOn(true);
			               amanager.startBluetoothSco();
			               myHash.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
			                       String.valueOf(AudioManager.STREAM_VOICE_CALL));
			               amanager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
			                       AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
			       		   String warning = "Crash detected. Dialing 9 1 1. Say cancel for false alarm.";
			       		   tts.speak(warning, TextToSpeech.QUEUE_FLUSH, myHash);
			       		   
			       		//startVoiceRecognitionActivity();
			       		   
			       		View v = findViewById(R.id.title); //fetch a View: any one will do

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

	void log(final String logString) {
		runOnUiThread(new Runnable() { 
            @Override 
            public void run() { 
            	tv1_.setText(tv1_.getText() + logString + "\n");
            } 
        }); 
    	 
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


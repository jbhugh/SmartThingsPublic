/*
   Virtual Thermostat for 3 Speed Ceiling Fan Control
   Copyright 2016 Dale Coffing
   
   This smartapp provides automatic control of Low, Medium, High speeds of a ceiling fan using 
   any temperature sensor with optional motion override. 
   It requires two hardware devices; any temperature sensor and a dimmer type smart fan controller
   such as the GE 12730 or Leviton VRF01-1LX
   
  Change Log
  2016-5-19 code clean up only
  2016-5-17 fanDiffTemp input changed to use enum with preselected values to overcome range:"0.1..5.0" bug
  2016-5-16 fixed typo with motion to motionSensor in hasBeenRecentMotion()
            fixed IDE integration with ST by making another change to file name specifics.
  2016-5-15 fixed fan differenial decimal point error by removing range: "1..99", removed all fanDimmer.setLevel(0)
 	         added iconX3Url, reworded preferences, rename evaluate to tempCheck for clarity,
 	         best practices to utilize initialize() method & replace motion with motionSensor,
  2016-5-14 Fan temperature differential variable added, best practices to change sensor to tempSensor,
  2016-5-13 best practices to replace ELSE IF for SWITCH statements on fan speeds, removed emergency temp control
  2016-5-12 added new icons for 3SFC, colored text in 3SFC125x125.png and 3sfc250x250.png
  2016-5-6  (e)minor changes to text, labels, for clarity, (^^^e)default to NO-Manual for thermostat mode 
  2016-5-5c clean code, added current ver section header, allow for multiple fan controllers,
            replace icons to ceiling fan, modify name from Control to Thermostat
  2016-5-5b @krlaframboise change to bypasses the temperatureHandler method and calls the tempCheck method
            with the current temperature and setpoint setting
  2016-5-5  autoMode added for manual override of auto control
  2016-5-4b cleaned debug logs, removed heat-cool selection, removed multiple stages
  2016-5-3  fixed error on not shutting down, huge shout out to my bro Stephen Coffing in the logic formation 
  
  Known Behavior from original Virtual Thermostat code
  -(fixed) when SP is updated, temp control isn't evaluated immediately, an event must trigger like change in temp, motion
  - if load is previously running when smartapp is loaded, it isn't evaluated immediately to turn off when SP>CT
 
  Thanks to @krlaframboise, @MikeMaxwell for help and knowledge in solving issues for a first time coder.
 
   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
   in compliance with the License. You may obtain a copy of the License at: www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
   for the specific language governing permissions and limitations under the License.
  
 */
definition(
    name: "3 Speed Ceiling Fan Thermostat",
    namespace: "dcoffing",
    author: "Dale Coffing",
    description: "Automatic control for 3 Speed Ceiling Fan using Low, Medium, High speeds with any temperature sensor.",
    category: "My Apps",
	iconUrl: "https://raw.githubusercontent.com/dcoffing/SmartThingsPublic/master/smartapps/dcoffing/3-speed-ceiling-fan-thermostat.src/3scft125x125.png", 
   	iconX2Url: "https://raw.githubusercontent.com/dcoffing/SmartThingsPublic/master/smartapps/dcoffing/3-speed-ceiling-fan-thermostat.src/3scft250x250.png",
	iconX3Url: "https://raw.githubusercontent.com/dcoffing/SmartThingsPublic/master/smartapps/dcoffing/3-speed-ceiling-fan-thermostat.src/3scft250x250.png",
)

preferences {
	section("Select a temperature sensor to control the fan..."){
		input "tempSensor", "capability.temperatureMeasurement",
        	multiple:false, title: "Temperature Sensor", required: true 
	}
	section("Select the ceiling fan control hardware..."){
		input "fanDimmer", "capability.switchLevel", 
	    	multiple:false, title: "Fan Control device", required: true
	}
	section("Enter the desired room temperature (ie 72.5)..."){
		input "setpoint", "decimal", title: "Room Setpoint Temp", required: true
	}
	section("Enter the desired differential temp between fan speeds (default=1.0)..."){
		input "fanDiffTempString", "enum", title: "Fan Differential Temp", options: ["0.5","1.0","1.5","2.0"], required: false
	}
	section("Enable ceiling fan thermostat only if motion is detected at (optional, leave blank to not require motion)..."){
		input "motionSensor", "capability.motionSensor", title: "Select Motion device", required: false
	}
	section("Turn off ceiling fan when there's been no movement for..."){
		input "minutes", "number", title: "Minutes?", required: false
	}
	section("Select ceiling fan operating mode desired (default to 'YES-Auto'..."){
		input "autoMode", "enum", title: "Enable Ceiling Fan Thermostat?", options: ["NO-Manual","YES-Auto"], required: false
	}
	section ("3 Speed Ceiling Fan Thermostat;   Version:1.0.160519") { } //ver format 1.0.YYMMDD
}

def installed() {
	log.debug "def INSTALLED with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "def UPDATED with settings: ${settings}"
	unsubscribe()
	initialize()
    handleTemperature(tempSensor.currentTemperature) //call handleTemperature to bypass temperatureHandler method 
} 

def initialize() {
	log.debug "def INITIALIZE with settings: ${settings}"
	subscribe(tempSensor, "temperature", temperatureHandler) //call temperatureHandler method when any reported change to "temperature" attribute
	if (motionSensor) {
		subscribe(motionSensor, "motion", motionHandler) //call the motionHandler method when there is any reported change to the "motion" attribute
	}   
}
                                   //Event Handler Methods                     
def temperatureHandler(evt) {
	log.debug "temperatureHandler called: $evt"	
    handleTemperature(evt.doubleValue)
	log.debug "temperatureHandler evt.doubleValue : $evt"
}

def handleTemperature(temp) {		//
	log.debug "handleTemperature called: $evt"	
	def isActive = hasBeenRecentMotion()
	if (isActive) {
		//motion detected recently
		tempCheck(temp, setpoint)
		log.debug "handleTemperature ISACTIVE($isActive)"
	}
	else {
     	fanDimmer.off()
 	}
}

def motionHandler(evt) {
	if (evt.value == "active") {
		//motion detected
		def lastTemp = tempSensor.currentTemperature
		log.debug "motionHandler ACTIVE($isActive)"
		if (lastTemp != null) {
			tempCheck(lastTemp, setpoint)
		}
	} else if (evt.value == "inactive") {		//testing to see if evt.value is indeed equal to "inactive" (vs evt.value to "active")
		//motion stopped
		def isActive = hasBeenRecentMotion()	//define isActive local variable to returned true or false
		log.debug "motionHandler INACTIVE($isActive)"
		if (isActive) {
			def lastTemp = tempSensor.currentTemperature
			if (lastTemp != null) {				//lastTemp not equal to null (value never been set) 
				tempCheck(lastTemp, setpoint)
			}
		}
		else {
     	    fanDimmer.off()
		}
	}
}

private tempCheck(currentTemp, desiredTemp)
{
	log.debug "TEMPCHECK#1(CT=$currentTemp,SP=$desiredTemp,FD=$fanDimmer.currentSwitch,FD_LVL=$fanDimmer.currentLevel, automode=$autoMode,FDTstring=$fanDiffTempString, FDTvalue=$fanDiffTempValue)"
    
    //convert Fan Diff Temp input enum string to number value and if user doesn't select a Fan Diff Temp default to 1.0 
	//  log.debug "fanDiffTempValue BEFORE convert String (FDTstring=$fanDiffTempString, FDTvalue=$fanDiffTempValue)"
    def fanDiffTempValue = (settings.fanDiffTempString != null && settings.fanDiffTempString != "") ? Double.parseDouble(settings.fanDiffTempString): 1.0
	//  log.debug "fanDiffTempValue AFTER convert String (FDTstring=$fanDiffTempString, FDTvalue=$fanDiffTempValue)"  
	
    //if user doesn't select autoMode then default to "YES-Auto"
    def autoModeValue = (settings.autoMode != null && settings.autoMode != "") ? settings.autoMode : "YES-Auto"	
    
    def LowDiff = fanDiffTempValue*1 
    def MedDiff = fanDiffTempValue*2
    def HighDiff = fanDiffTempValue*3
	
	log.debug "TEMPCHECK#2(CT=$currentTemp,SP=$desiredTemp,FD=$fanDimmer.currentSwitch,FD_LVL=$fanDimmer.currentLevel, automode=$autoMode,FDTstring=$fanDiffTempString, FDTvalue=$fanDiffTempValue)"
	if (autoModeValue == "YES-Auto") {
    	switch (currentTemp - desiredTemp) {
        	case { it  >= HighDiff }:
        		// turn on fan high speed
       			fanDimmer.setLevel(90) 
            	log.debug "HI speed(CT=$currentTemp, SP=$desiredTemp, FD-LVL=$fanDimmer.currentLevel, HighDiff=$HighDiff)"
	            break  //exit switch statement 
			case { it >= MedDiff }:
            	// turn on fan medium speed
            	fanDimmer.setLevel(60)
            	log.debug "MED speed(CT=$currentTemp, SP=$desiredTemp, FD-LVL=$fanDimmer.currentLevel, MedDiff=$MedDiff)"
                break
       		case { it >= LowDiff }:
            	// turn on fan low speed
            	if (fanDimmer.currentSwitch == "off") {		// if fan is OFF to make it easier on motor by   
            		fanDimmer.setLevel(90)					// starting fan in High speed temporarily then 
                	fanDimmer.setLevel(30, [delay: 3000])	// change to Low speed after 3 seconds
                	log.debug "LO speed after HI 3secs(CT=$currentTemp, SP=$desiredTemp, FD-LVL=$fanDimmer.currentLevel, LowDiff=$LowDiff)"
          		} else {
                	fanDimmer.setLevel(30)	//fan is already running, not necessary to protect motor
            	}							//set Low speed immediately
            	log.debug "LO speed immediately(CT=$currentTemp, SP=$desiredTemp, FD-LVL=$fanDimmer.currentLevel, LowDiff=$LowDiff)"
                break
		default:
            	// check to see if fan should be turned off
            	if (desiredTemp - currentTemp >= 0 ) {	//below or equal to setpoint, turn off fan, zero level
            		fanDimmer.off()
            		log.debug "below SP+Diff=fan OFF (CT=$currentTemp, SP=$desiredTemp, FD-LVL=$fanDimmer.currentLevel, FD=$fanDimmer.currentSwitch,autoMode=$autoMode,)"
				} 
                log.debug "autoMode YES-MANUAL? else OFF(CT=$currentTemp, SP=$desiredTemp, FD-LVL=$fanDimmer.currentLevel, FD=$fanDimmer.currentSwitch,autoMode=$autoMode,)"
        }	//end of switch statement
	}	// end of IF (autoModeValue...
}

private hasBeenRecentMotion()
{
	def isActive = false
	if (motionSensor && minutes) {
		def deltaMinutes = minutes as Long
		if (deltaMinutes) {
     //    log.debug "HASBEENRECENTMOTION ($deltaMinutes, $motionEvents, $motion.eventsSince, $deltaMinutes)"
			def motionEvents = motionSensor.eventsSince(new Date(now() - (60000 * deltaMinutes)))
			log.trace "Found ${motionEvents?.size() ?: 0} events in the last $deltaMinutes minutes"
			if (motionEvents.find { it.value == "active" }) {
				isActive = true
			}
		}
	}
	else {
		isActive = true
	}
	isActive
}

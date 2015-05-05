# bearcastlib
Library for connecting to BearCast

# How to Use
Clone this module

Clone the blescanner module at https://github.com/twuster/blescanner

Open your project in Android Studio
Click File->New->Import Module

Navigate to the source directory of bearcastlib

Import the module

Repeat the past 3 steps to import the blescanner module as well

Add bearcastlib as a module dependency for your project

This can be done by right clicking on your project->Open Module Settings->Dependencies->Click plus icon->Module Dependency->Select bearcastlib

Add the blescanner module as a module dependency for bearcastlib by doing the same thing

#API
Instantiate a new BearCastUtil object in your Activity's onCreate method. You must pass in a Context into the constructor and a name as an identifier. e.g.
```
private static BearCastUtil sBearCastUtil;

@Override
protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);
  sBearCastUtil = new BearCastUtil(this, "My cool device"); 
}
```

Override your onResume() method and call BearCastUtil.startBluetoothScan() to start a scan for nearby BearCast beacons. e.g.
```
@Override
protected void onResume() {
  super.onResume();
  sBearCastUtil.startBluetoothScan();
}
```

Override your onStop() method and call BearCastUtil.stopBluetoothScan() to stop the scan when your app closes. e.g.
```
@Override
protected void onStop() {
  super.onStop();
  sBearCastUtil.stopBluetoothScan();
}
```

#Device Cast
Call BearCastUtil.deviceCast(String[] data, String[] datatypes, String templateName) to cast information to the nearest BearCast display. This message will insert the arguments in the data array into the corresponding HTML template with the given template name. Then, this information will be displayed in the device specific section of the display. e.g.
```
String[] dataArray = {"40", "done"};
String[] dataTypes = {"number", "string"};
String templateName = "riceCooker.html"
sBearCastUtil.deviceCast(dataArray, dataTypes, templateName);
```

#Visitor Cast

Call BearCastUtil.castMessage(String msg) to cast a message to the nearest BearCast display. This message will displayed in the visitor section.
```
String msg = "HELLO WORLD";
sBearCastUtil.castMessage(msg);
```


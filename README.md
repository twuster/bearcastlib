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
Instantiate a new BearCastUtil object in your Activity's onCreate method. You must pass in a Context into the constructor.

Override your onResume() method and call BearCastUtil.startBluetoothScan() to start a scan for nearby BearCast beacons.

Override your onStop() method and call BearCastUtil.stopBluetoothScan() to stop the scan when your app closes.

Call BearCastUtil.castMessage(String msg) to cast a message to the nearest BearCast display 


# godot-firebase-plugin
A Firebase Plugin for the new [Godot Android Plugin System](https://docs.godotengine.org/pt_BR/stable/tutorials/plugins/android/android_plugin.html).

This works for [Godot Engine](https://godotengine.org/)'s 3.2.2+

## Installation instructions
1. Download and start Godot 3.2.2 No need to build it on your own (compile, ...).
2. Install **Export Templates**: select menu *Editor > Manage Export Templates...* and download for Current Version (3.2.2+)
3. Install **Android Build Template** for your project: select menu *Project > Install Android Build Template...*, and then click *Install*. This will install the files in your project's directory (by adding `res://android/`).
4. From the [Releases Page](https://github.com/taticus/godot-firebase-plugin/releases) download the latest plugin version and unzip it to `res://android/plugins`. Both files (`Firebase.release.aar` and `Firebase.gdap`) must be in the folder.
5. Select menu *Project > Export*, and *Add...* Android. After setting your *Unique Name*, keystore stuff etc, don't forget to turn on ***Use Custom Build*** and enable **Firebase** plugin:

![](https://i.imgur.com/uobGpQ6.png)

6. From [Firebase console](http://console.firebase.google.com/) download your project's **google-services.json** and copy/move it to `res://android/build/`.

   **Notice:**<br/>Remember to always download a new version of google-services.json whenever you make changes at the Firebase console!

7. Change `minSdk` from 18 to 21 in `res://android/build/config.gradle`:

   <pre>minSdk : 21</pre>
   
8. Add the google-services dep in buildscript dependencies `res://android/build/build.gradle`:

   <pre>
   buildscript {
    apply from: 'config.gradle'

    repositories {
	google()
	jcenter()
	//CHUNK_BUILDSCRIPT_REPOSITORIES_BEGIN
	//CHUNK_BUILDSCRIPT_REPOSITORIES_END
    }
    dependencies {
	classpath libraries.androidGradlePlugin
	classpath libraries.kotlinGradlePlugin
	
	// ADD THIS DEPENDENCY!!!
	classpath 'com.google.gms:google-services:4.3.3'
	
	//CHUNK_BUILDSCRIPT_DEPENDENCIES_BEGIN
	//CHUNK_BUILDSCRIPT_DEPENDENCIES_END
    }
   }
   </pre>
   
9. Add the google-services plugin at the end of the file`res://android/build/build.gradle`:

   <pre>
	apply plugin: 'com.google.gms.google-services'
   </pre>
   
Setup is done, now you can run the project on an android device.

## API instructions

### Initialization

In any of your Godot Script (I prefer a singleton called Global.gd), initialize the Firebase Module:

	var firebase = null

	func _ready() -> void:
		if Engine.has_singleton("Firebase"):
			firebase = Engine.get_singleton("Firebase")
			firebase.connect("login_success", self, "_on_login_success")
			firebase.connect("login_error", self, "_on_login_error")

	func play_services_login() -> void:
		var web_client_id = "you can store your play games web client id in your project settings"
		firebase.login_with_google(web_client_id)

	func _on_login_success() -> void:
		print(firebase.email())

	func _on_login_error(error) -> void:
		printerr(error)

More features comming soon...

## Debug Instructions

To check errors on the plugin, run the `adb logcat` on terminal:

	adb -d logcat godot:V GoogleService:V Firebase:V DEBUG:V AndroidRuntime:V FirebasePlugin:V FirebaseApp:V *:S

package com.taticus.godot.plugin.android.firebase;

import android.app.Activity;
import android.view.View;
import java.util.Collections;
import java.util.List;
import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;

public class FirebasePlugin extends GodotPlugin {

  private static final String HELLO_WORLD = "Hello World";


  public FirebasePlugin(Godot godot) {
    super(godot);
  }

  @Override
  public String getPluginName() {
    return "Firebase";
  }

  @Override
  public List<String> getPluginMethods() {
    return Collections.singletonList("hello_world");
  }

  @Override
  public View onMainCreateView(Activity activity) {
    return null;
  }

  /**
   * Show/hide, print and return "Hello World".
   */
  public String hello_world() {
    System.out.println(HELLO_WORLD);
    return HELLO_WORLD;
  }
}

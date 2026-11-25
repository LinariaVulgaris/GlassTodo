# Room generates its implementation classes; keep them reachable via the
# generated Factory rather than by name, so nothing extra is required here.
# Widget providers, services and receivers are declared in AndroidManifest.xml
# and are therefore kept automatically by AGP's generated rules.

-keepattributes *Annotation*

# RemoteViews inflates widget layouts by resource id in the launcher process;
# resource shrinking must not drop widget layouts or their drawables.
-keep class android.widget.RemoteViews { *; }

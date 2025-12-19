package demo.multiagent3.domain;

// tag::all[]
import akka.javasdk.annotations.TypeName;

public sealed interface PreferencesEvent {
  @TypeName("preference-added")
  record PreferenceAdded(String preference) implements PreferencesEvent {}
}
// end::all[]

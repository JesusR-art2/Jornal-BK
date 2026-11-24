package jornal.common.events;

/** Tipos de evento (atributo {@code type} de CloudEvents). Se versionan con el sufijo {@code .vN}. */
public final class EventTypes {

    public static final String REGION_PROFILE_UPDATED = "jornal.region.profile-updated.v1";

    private EventTypes() {
    }
}

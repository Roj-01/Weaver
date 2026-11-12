package diag;

/** إطار واحد في stack trace. */
public final class Frame {

    public final String className; // com.example.weathermod.tick.WeatherTickHandler
    public final String method;    // onWorldTick
    public final String location;  // WeatherTickHandler.java:87 | Unknown Source | Native Method
    public final Category category;
    public final String rootPackage;
    public final boolean mixinish;

    public Frame(String className, String method, String location) {
        this.className = className;
        this.method = method;
        this.location = location == null ? "" : location.strip();
        this.category = Classifier.categorize(className);
        this.rootPackage = Classifier.rootPackage(className);
        this.mixinish = Classifier.isMixinish(className, method, this.category);
    }

    public String shortForm() {
        return className + "." + method + "(" + location + ")";
    }

    @Override
    public String toString() {
        return shortForm();
    }
}
